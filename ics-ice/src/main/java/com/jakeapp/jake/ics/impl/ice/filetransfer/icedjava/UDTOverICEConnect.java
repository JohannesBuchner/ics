package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import net.mc_cubed.icedjava.ice.IceSocketChannel;
import net.mc_cubed.icedjava.ice.IceStatus;
import net.mc_cubed.icedjava.stun.StunUtil;

import org.apache.log4j.Logger;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;

import udt.ClientSession;
import udt.UDPNIOEndPoint;
import udt.UDTReceiver;
import udt.UDTSession;
import udt.UDTSocket;
import udt.packets.Destination;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.msgservice.IMsgService;


public class UDTOverICEConnect {

	private static final Logger log = Logger.getLogger(UDTOverICEConnect.class);

	private IMsgService msg;

	private Map<UserId, IcePeer> ice = new HashMap<UserId, IcePeer>();

	private Map<UserId, UDTSocket> sockets = new HashMap<UserId, UDTSocket>();

	private IceSocket socket;

	public void shutdown(boolean alsoShutdownSockets) {
		for (UserId other : ice.keySet()) {
			shutdownIceFor(other, alsoShutdownSockets);
		}
	}


	public UDTOverICEConnect(IMsgService msg) throws SdpParseException,
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
		this.socket = IceFactory.createIceSocket(media);

		this.msg = msg;
	}

	public void shutdownIceFor(UserId otheruser, boolean alsoShutdownSocket) {
		if (alsoShutdownSocket)
			try {
				sockets.get(otheruser).close();
			} catch (IOException e) {
				log.warn("shutting down socket", e);
			}
		ice.get(otheruser).close();
		ice.remove(otheruser);
	}

	public IcePeer createIceFor(UserId otheruser, boolean controlling)
			throws SdpException {
		if (ice.containsKey(otheruser))
			return ice.get(otheruser);
		log.debug("creating IcePeer for connecting to " + otheruser
				+ " -- controlling? " + controlling);
		/*
		 * If you look at the network, nothing has happened yet. IceSockets
		 * encapsulate some logic, but do not affect the network on their own.
		 * For that, you'll need to create an IcePeer. An IcePeer represents a
		 * remote target for this IceSocket, or potentially, several IceSockets.
		 */

		final IcePeer peer;
		if (controlling) {
			peer = IceFactory.createIcePeerAgressive(socket);
		} else
			peer = IceFactory.createIcePeerControlled(socket);

		/*
		 * At this point the network will already be churning away, collecting
		 * candidates and preparing for ICE processing. This peer will assume it
		 * is in the controlling role initially, and once started, will attempt
		 * to send an SDP update to it's peer. How this data reaches the peer
		 * MUST be defined by you using the SDPListener interface, which you
		 * register using the peer.setSdpListener() method.
		 */
		peer.setSdpListener(new SDPOverICS(peer, msg, otheruser) {

			@Override
			protected void onReceiveText(String text) throws SdpParseException {
				log.debug("we received: " + text);
				super.onReceiveText(text);
			}

			@Override
			protected void onReceive(Connection conn,
					List<Attribute> iceAttributes,
					List<MediaDescription> iceMedias) throws SdpParseException {
				super.onReceive(conn, iceAttributes, iceMedias);
				/*
				 * If you want the peer to act in Controlled mode, you will
				 * first need an SDP offer from the remote peer, which you offer
				 * to IcePeer using the updateMedia() method before invoking
				 * start();
				 */

				// once we received the offer, start state machine
				peer.start();
			}

		});

		if (controlling) {
			log.debug("starting controlling peer now");
			peer.start();
		}

		ice.put(otheruser, peer);
		return peer;
	}


	public CandidatePair findCandidatePair(IcePeer peer) throws IOException,
			InterruptedException {
		log.debug("waiting until peer status == finished");
		// TODO: replace with asynchronous call once icedjava has one
		while (peer.getStatus() == IceStatus.IN_PROGRESS
				|| peer.getStatus() == IceStatus.NOT_STARTED) {
			log.debug("waiting until peer status == finished; currently "
					+ peer.getStatus() + " (nominated: "
					+ peer.getNominated().get(socket) + ")");
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
		int component = 0;
		CandidatePair a = peer.getNominated().get(socket).get(component);
		log.info("ICE connect succeeded: pair: " + a);

		/*
		 * Finally, closing a Peer down is as easy as calling the peer.close()
		 * method, which will disconnect the peer from the IceSocket, and tear
		 * down the allocated ports.
		 * 
		 * Calling close() an an IceSocket will close all peer connections that
		 * use that socket.
		 */
		// peer.close();
		// socket.close();
		return a;

	}

	public UDTSocket initiateSending(UserId otheruser) throws IOException,
			SdpException, InterruptedException {
		return initiate(otheruser, true);
	}

	public UDTSocket initiateReceiving(UserId otheruser) throws IOException,
			InterruptedException, SdpException {
		return initiate(otheruser, false);
	}

	public UDTSocket initiate(UserId otheruser, boolean controlling)
			throws IOException, InterruptedException, SdpException {
		UDTSocket sock = sockets.get(otheruser);
		if (sock != null && sock.isActive()) {
			return sock;
		}

		log.debug("(re)starting ICE " + (controlling ? "server" : "client")
				+ " connect");
		IcePeer peer = createIceFor(otheruser, controlling);
		if (controlling) {
			/*
			 * Once you invoke start() on a peer, ICE processing will begin. The
			 * Controlled peer will immediately send a reply to the Controller
			 * and start ICE tests. The Controlling peer, will wait for a reply
			 * from the Controlled peer, and then begin tests. ICE processing
			 * can take from several seconds to a minute depending on the number
			 * of possibilities, and whether Nomination is done aggressively or
			 * not. (Aggressive nomination can speed processing time, but may
			 * chose a less optimal pair than normal nomination would.
			 * 
			 * Once ICE processing is complete, (The current status can be
			 * queried using the getStatus() method) or even before then, you
			 * may obtain an ICE Socket Channel using peer.getChannels(socket)
			 * method. Socket Channels are analogous to NIO socket channels, and
			 * implement many of the NIO methods and interfaces. One socket
			 * channel corresponds to one port, on one media line, connected to
			 * one peer. (one media line can have multiple ports) Ice processing
			 * is, by necessity, a one port to one peer proposition, and so
			 * there's little purpose in querying the source of packets, or
			 * directing their destination in an Ice Socket Channel. What this
			 * also means is that you can treat the Ice Socket Channel as a
			 * stream, like you would a TCP connection in NIO, just be aware
			 * that packets may arrive out of order or not at all.
			 */
			peer.start();
		}

		CandidatePair a = findCandidatePair(peer);
		IceSocketChannel ch = peer.getChannels(socket).get(0);

		InetSocketAddress local = a.getLocalCandidate().getSocketAddress();
		InetSocketAddress remote = a.getRemoteCandidate().getSocketAddress();

		log.debug("creating UDT connection");
		UDPNIOEndPoint peerEnd = new UDPNIOEndPoint(ch, local.getAddress(),
				local.getPort(), remote.getAddress(), remote.getPort());
		Destination destination = new Destination(remote.getAddress(),
				remote.getPort());

		UDTSession session = null;
		if (controlling) {
			peerEnd.start(true);
			while (session == null) {
				try {
					session = peerEnd.getSession(10000, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					continue;
				}

				// wait for handshake to complete
				while (session != null && !session.isReady()
						|| session.getSocket() == null) {
					log.debug("server is waiting for handshake response: session ready? "
							+ session.isReady());
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
					}
				}
			}
		} else {
			ClientSession sess = new ClientSession(peerEnd, destination);
			// destination.setSocketID(sess.getSocketID());
			log.debug("client: adding session");
			peerEnd.addSession(sess.getSocketID(), sess);
			log.debug("client: starting communication");
			peerEnd.start();
			log.debug("client: connecting...");
			sess.connect();
			session = sess;
			// wait for handshake
			while (!sess.isReady()) {
				log.debug("client: waiting for session to be ready ...");
				Thread.sleep(500);
			}
		}
		sockets.put(otheruser, session.getSocket());
		return session.getSocket();
	}
}
