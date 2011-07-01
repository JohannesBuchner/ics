/*
 * Copyright 2010 Charles Chappell.
 *
 * This file is part of IcedJava.
 *
 * IcedJava is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * IcedJava is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with IcedJava.  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.jakeapp.jake.ics.impl.ice.filetransfer.fsm;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import net.mc_cubed.icedjava.ice.AddressDiscovery;
import net.mc_cubed.icedjava.ice.AddressDiscoveryMechanism;
import net.mc_cubed.icedjava.ice.Candidate.CandidateType;
import net.mc_cubed.icedjava.ice.CandidatePair;
import net.mc_cubed.icedjava.ice.IceDatagramSocket;
import net.mc_cubed.icedjava.ice.IcePeer;
import net.mc_cubed.icedjava.ice.IceReply;
import net.mc_cubed.icedjava.ice.IceSocket;
import net.mc_cubed.icedjava.ice.LocalCandidate;
import net.mc_cubed.icedjava.ice.StunAddressDiscovery;
import net.mc_cubed.icedjava.ice.pmp.IcePMPBridge;
import net.mc_cubed.icedjava.ice.upnp.IceUPNPBridge;
import net.mc_cubed.icedjava.packet.StunPacket;
import net.mc_cubed.icedjava.packet.attribute.AttributeFactory;
import net.mc_cubed.icedjava.packet.attribute.AttributeType;
import net.mc_cubed.icedjava.packet.attribute.NullAttribute;
import net.mc_cubed.icedjava.packet.header.MessageClass;
import net.mc_cubed.icedjava.packet.header.MessageMethod;
import net.mc_cubed.icedjava.stun.StunReply;
import net.mc_cubed.icedjava.stun.StunSocket;
import net.mc_cubed.icedjava.stun.StunUtil;
import net.mc_cubed.icedjava.util.ExpiringCache;

import org.glassfish.grizzly.filterchain.BaseFilter;

import com.jakeapp.availablelater.AvailabilityListener;
import com.jakeapp.availablelater.AvailableLater;
import com.jakeapp.availablelater.AvailableLaterObject;
import com.jakeapp.availablelater.StatusUpdate;

/**
 * Implements the ICE state machine
 * 
 * @author Charles Chappell
 * @since 1.0
 */
abstract class MyIceStateMachine extends BaseFilter implements Runnable {

	public static final int PEER_REFLEXIVE_PRIORITY = CandidateType.PEER_REFLEXIVE
			.getPriority();

	private Logger log = Logger.getLogger(MyIceStateMachine.class.getName());

	private InetSocketAddress stunServer = StunUtil.getCachedStunServerSocket();

	protected final Map<IceSocket, List<CandidatePair>> checkPairs = new HashMap<IceSocket, List<CandidatePair>>();

	// private final Map<CandidateType, Integer> priorities = new
	// HashMap<CandidateType, Integer>();
	private final Map<IceSocket, List<LocalCandidate>> socketCandidateMap = new LinkedHashMap<IceSocket, List<LocalCandidate>>();

	protected static SecureRandom random = new SecureRandom();

	protected final Map<IceSocket, List<CandidatePair>> nominated = new HashMap<IceSocket, List<CandidatePair>>();

	private boolean localOnly = false;

	private boolean sendKeepalives = false;

	private ExpiringCache<SocketAddress, IcePeer> socketCache = new ExpiringCache<SocketAddress, IcePeer>();

	private Block<CandidatePair> frozenPairs = new Block<CandidatePair>();

	private boolean controlling = false;

	public MyIceStateMachine(boolean controlling) {
		this.controlling = controlling;
	}

	public void addCandidatePair(CandidatePair pair) {
		frozenPairs.enter(pair);
	}

	@Override
	public void run() {
		//
		// find
		final Block<CandidatePair> failedPairs = new Block<CandidatePair>();
		final Block<CandidatePair> succeedPairs = new Block<CandidatePair>();

		final Block<CandidatePair> waitingPairs = new Block<CandidatePair>();
		waitingPairs.registerListener(new BlockEventListener<CandidatePair>() {

			@Override
			public void onEmpty(Block<CandidatePair> block) {
				// pull items with the same lowest priority into here
				if (frozenPairs.getContent().isEmpty())
					return;
				List<CandidatePair> nexts = new ArrayList<CandidatePair>();
				long lowestPriority = frozenPairs.getContent().poll()
						.getPriority();

				for (CandidatePair next : frozenPairs.getContent()) {
					if (next.getPriority() < lowestPriority) {
						lowestPriority = next.getPriority();
						nexts.clear();
					}
					if (next.getPriority() == lowestPriority) {
						nexts.add(next);
					}
				}

				for (CandidatePair next : nexts) {
					block.enter(next);
				}
			}

			@Override
			public void onEnter(Block<CandidatePair> block,
					final CandidatePair e) {
				// start doing something with the pairs
				AvailableLater<Void> avl = startTesting(e);
				avl.setListener(new AvailabilityListener<Void>() {

					@Override
					public void statusUpdate(StatusUpdate arg0) {
						log.info("status on " + e + ": "
								+ (int) (arg0.getProgress() * 100) + "%"
								+ " -- " + arg0.getStatus());
					}

					@Override
					public void finished(Void arg0) {
						log.info(e + " finished with success");
						succeedPairs.enter(e);
					}

					@Override
					public void error(Exception arg0) {
						log.info(e + " failed: " + arg0);
						failedPairs.enter(e);
					}
				});
			}
		});

		succeedPairs.registerListener(new BlockEventListener<CandidatePair>() {

			@Override
			public void onEnter(Block<CandidatePair> block, CandidatePair e) {
				notifySuccessful(e);
			}
		});

		// start processing
		waitingPairs.triggerOnEmpty();
	}

	protected void notifySuccessful(CandidatePair e) {
		// TODO -- this is good!
		log.log(Level.FINE, "suggesting " + e.getLocalCandidate().getAddress()
				+ ":" + e.getLocalCandidate().getPort() + " <--> "
				+ e.getRemoteCandidate().getAddress() + ":"
				+ e.getRemoteCandidate().getPort());

	}

	protected AvailableLater<Void> startTesting(final CandidatePair e) {
		return new AvailableLaterObject<Void>() {

			@Override
			public Void calculate() throws Exception {
				// TODO -- test
				return doIceTest(e);
			}

		}.start();
	}

	protected Void doIceTest(CandidatePair e) {
		// TODO Auto-generated method stub
		return null;
	}
}
