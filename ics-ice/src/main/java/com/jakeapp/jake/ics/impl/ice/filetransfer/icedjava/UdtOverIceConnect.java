package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import net.mc_cubed.icedjava.ice.CandidatePair;
import net.mc_cubed.icedjava.ice.IcePeer;
import net.mc_cubed.icedjava.ice.IceSocket;
import net.mc_cubed.icedjava.ice.IceSocketChannel;

import org.apache.log4j.Logger;

import udt.ClientSession;
import udt.UDPNIOEndPoint;
import udt.UDTSession;
import udt.UDTSocket;
import udt.packets.Destination;

/**
 * Operates over the Ice stream provided by Ice
 * 
 * Benefits: re-negotiation is possible
 * 
 * Drawbacks: running a packet-based protocol over a stream is flawed.
 * 
 * @author johannes
 */
public class UdtOverIceConnect extends IUdtOverIceConnect {

	private static final Logger log = Logger.getLogger(IceConnect.class);

	@Override
	public UDTSocket doConnect(IceSocket socket, IcePeer peer, boolean server)
			throws InterruptedException, IOException {
		CandidatePair a = peer.getNominated().get(socket).get(component);
		IceSocketChannel ch = peer.getChannels(socket).get(0);

		InetSocketAddress local = a.getLocalCandidate().getSocketAddress();
		InetSocketAddress remote = a.getRemoteCandidate().getSocketAddress();

		log.debug("creating UDT connection");
		UDPNIOEndPoint peerEnd = new UDPNIOEndPoint(ch, local.getAddress(),
				local.getPort(), remote.getAddress(), remote.getPort());
		Destination destination = new Destination(remote.getAddress(),
				remote.getPort());

		UDTSession session = null;
		if (server) {
			log.debug("server: starting");
			peerEnd.start(true);
			while (session == null) {
				session = peerEnd.getSession(10000, TimeUnit.MILLISECONDS);
				log.debug("server: created session with id "
						+ session.getSocketID());

				// wait for handshake to complete
				while (session != null && !session.isReady()
						|| session.getSocket() == null) {
					log.debug("server: waiting for handshake response: session ready? "
							+ session.isReady());
					Thread.sleep(100);
				}
			}
		} else {
			ClientSession sess = new ClientSession(peerEnd, destination);
			// destination.setSocketID(sess.getSocketID());
			log.debug("client: adding session with id " + sess.getSocketID());
			peerEnd.addSession(sess.getSocketID(), sess);
			log.debug("client: starting communication");
			peerEnd.start();
			log.debug("client: connecting...");
			sess.connect();
			log.debug("client: connecting done");
			session = sess;
			// wait for handshake
			while (!sess.isReady()) {
				log.debug("client: waiting for session to be ready ...");
				Thread.sleep(500);
			}
		}
		return session.getSocket();
	}
}
