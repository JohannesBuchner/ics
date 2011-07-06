package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sdp.Attribute;
import javax.sdp.Connection;
import javax.sdp.Media;
import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SdpFactory;
import javax.sdp.SdpParseException;

import net.mc_cubed.icedjava.ice.CandidatePair;
import net.mc_cubed.icedjava.ice.IceFactory;
import net.mc_cubed.icedjava.ice.IcePeer;
import net.mc_cubed.icedjava.ice.IceSocket;
import net.mc_cubed.icedjava.ice.IceStatus;
import net.mc_cubed.icedjava.stun.StunUtil;

import org.apache.log4j.Logger;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;

import udt.UDTReceiver;

import com.jakeapp.availablelater.AvailableLater;
import com.jakeapp.availablelater.AvailableLaterObject;
import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.msgservice.IMsgService;

/**
 * There should be one of these per msgservice
 * 
 * @author johannes
 * 
 */
public class IceConnect implements IIceConnect {

	private static final Logger log = Logger.getLogger(IceConnect.class);

	private Map<UserId, IcePeer> ices = new HashMap<UserId, IcePeer>();

	private IceSocket socket;

	private IMsgService msg;

	private int component = 0;

	public IceConnect(IMsgService msg) throws SdpParseException,
			SocketException {
		/*
		 * configure the datagram sockets given back, for UDT
		 */
		log.debug("configuring STUN");
		UDPNIOTransport transport = StunUtil.getDatagramTransport();
		transport.setReadBufferSize(128 * 1024);
		transport.setReuseAddress(false);
		transport.setConnectionTimeout(100000);
		UDTReceiver.connectionExpiryDisabled = true;


		/*
		 * The easiest method of obtaining a Media object is oddly enough,
		 * creating a MediaDescription, then getting the Media object from that:
		 */
		Media media = SdpFactory
				.getInstance()
				.createMediaDescription("application", 0, 1, "RTP/AVP",
						new String[] { "97" }).getMedia();

		/*
		 * Once you have a Media object, you can instantiate an IceSocket:
		 */
		log.debug("creating ICE socket");
		this.socket = IceFactory.createIceSocket(media);

		this.msg = msg;
		/*
		 * we want to receive stuff
		 */
		this.msg.registerReceiveMessageListener(new SDPOverICSListener(this));
	}

	@Override
	public void updateMedia(UserId user, Connection conn, List<Attribute> attr,
			List<MediaDescription> media) throws SdpException {
		synchronized (ices) {
			IcePeer peer = ices.get(user);
			if (peer == null) {
				log.info("incoming ICE connect from " + user);
				peer = IceFactory.createIcePeerControlled(socket);
				peer.setSdpListener(new SDPOverICSForwarder(msg, user));
				ices.put(user, peer);
			}
			peer.updateMedia(conn, attr, media);
			// make sure its started, or, if its a client and this is the first
			// message, start it.
			peer.start();
		}
	}

	@Override
	public boolean hasCandidatePair(final UserId user) {
		if (!ices.containsKey(user))
			return false;
		IcePeer peer = ices.get(user);
		if (peer.getStatus() == IceStatus.SUCCESS) {
			return true;
		}
		return false;
	}

	@Override
	public IcePeer get(final UserId peer) {
		return ices.get(peer);
	}

	@Override
	public AvailableLater<IcePeer> getNomination(final UserId user) {
		return new AvailableLaterObject<IcePeer>() {

			@Override
			public IcePeer calculate() throws Exception {
				log.debug("creating ICE socket");
				IcePeer peer = startConnect(user);
				log.debug("waiting until peer status == finished");
				// TODO: replace with asynchronous call once icedjava has one
				while (peer.getStatus() == IceStatus.IN_PROGRESS
						|| peer.getStatus() == IceStatus.NOT_STARTED) {
					log.debug("waiting until peer status == finished; currently "
							+ peer.getStatus()
							+ ") controlling? "
							+ peer.isLocalControlled());
					Thread.sleep(1000);
					if (peer.getStatus() == IceStatus.FAILED) {
						// give it another chance
						Thread.sleep(20);
						if (peer.getStatus() != IceStatus.FAILED) {
							log.debug("recovered from failed state");
							break;
						}
					}
				}
				if (peer.getStatus() == IceStatus.FAILED) {
					throw new IOException("ICE connect failed");
				}

				log.info("ICE connect succeeded");
				CandidatePair a = peer.getNominated().get(socket)
						.get(component);
				log.info("ICE connect succeeded: pair: " + a);

				return peer;
			}
		}.start();
	}

	private IcePeer startConnect(final UserId user) throws SdpException {
		synchronized (ices) {
			IcePeer peer = ices.get(user);
			if (peer == null) {
				log.debug("initiating ICE connection to " + user);
				/*
				 * If you look at the network, nothing has happened yet.
				 * IceSockets encapsulate some logic, but do not affect the
				 * network on their own. For that, you'll need to create an
				 * IcePeer. An IcePeer represents a remote target for this
				 * IceSocket, or potentially, several IceSockets.
				 */
				peer = IceFactory.createIcePeerAgressive(socket);

				/*
				 * Once you invoke start() on a peer, ICE processing will begin.
				 * The Controlled peer will immediately send a reply to the
				 * Controller and start ICE tests. The Controlling peer, will
				 * wait for a reply from the Controlled peer, and then begin
				 * tests. ICE processing can take from several seconds to a
				 * minute depending on the number of possibilities, and whether
				 * Nomination is done aggressively or not. (Aggressive
				 * nomination can speed processing time, but may chose a less
				 * optimal pair than normal nomination would.
				 * 
				 * Once ICE processing is complete, (The current status can be
				 * queried using the getStatus() method) or even before then,
				 * you may obtain an ICE Socket Channel using
				 * peer.getChannels(socket) method. Socket Channels are
				 * analogous to NIO socket channels, and implement many of the
				 * NIO methods and interfaces. One socket channel corresponds to
				 * one port, on one media line, connected to one peer. (one
				 * media line can have multiple ports) Ice processing is, by
				 * necessity, a one port to one peer proposition, and so there's
				 * little purpose in querying the source of packets, or
				 * directing their destination in an Ice Socket Channel. What
				 * this also means is that you can treat the Ice Socket Channel
				 * as a stream, like you would a TCP connection in NIO, just be
				 * aware that packets may arrive out of order or not at all.
				 */
				peer.setSdpListener(new SDPOverICSForwarder(msg, user));
				peer.start();
				ices.put(user, peer);
			}
			/*
			 * At this point the network will already be churning away,
			 * collecting candidates and preparing for ICE processing. This peer
			 * will assume it is in the controlling role initially, and once
			 * started, will attempt to send an SDP update to it's peer. How
			 * this data reaches the peer MUST be defined by you using the
			 * SDPListener interface, which you register using the
			 * peer.setSdpListener() method.
			 */
			return peer;
		}
	}

	protected void finalize() throws Throwable {
		socket.close();
	}

	@Override
	public void close(UserId user) {
		/*
		 * Finally, closing a Peer down is as easy as calling the peer.close()
		 * method, which will disconnect the peer from the IceSocket, and tear
		 * down the allocated ports.
		 * 
		 * Calling close() an an IceSocket will close all peer connections that
		 * use that socket.
		 */
		get(user).close();
	}

	@Override
	public IceSocket getSocket() {
		return socket;
	}
}
