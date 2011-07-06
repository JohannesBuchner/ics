package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.IOException;
import java.net.InetSocketAddress;

import net.mc_cubed.icedjava.ice.CandidatePair;
import net.mc_cubed.icedjava.ice.IcePeer;
import net.mc_cubed.icedjava.ice.IceSocket;
import net.mc_cubed.icedjava.ice.IceSocketChannel;

import org.apache.log4j.Logger;

import udt.ClientSession;
import udt.UDPEndPoint;
import udt.UDTServerSocket;
import udt.UDTSocket;
import udt.packets.Destination;

/**
 * Operates over the ICE end points (IP addresses) negotiated
 * 
 * Benefits: full control for UDT
 * 
 * Drawbacks: re-negotiation is not possible. A timed-out/stuck socket will have
 * to be closed.
 * 
 * @author johannes
 */
public class UdtOverIceAddressesConnect extends IUdtOverIceConnect {

	private static final Logger log = Logger.getLogger(IceConnect.class);

	@Override
	public UDTSocket doConnect(IceSocket socket, IcePeer peer, boolean server)
			throws InterruptedException, IOException {
		CandidatePair a = peer.getNominated().get(socket).get(component);
		// closing so we can open the connection
		log.debug("closing peer to free address");
		peer.close();

		InetSocketAddress local = a.getLocalCandidate().getSocketAddress();
		InetSocketAddress remote = a.getRemoteCandidate().getSocketAddress();

		log.debug("creating UDT connection");


		UDPEndPoint localEnd = new UDPEndPoint(local.getAddress(),
				local.getPort());
		Destination destination = new Destination(remote.getAddress(),
				remote.getPort());

		if (server) {
			log.debug("server: starting");
			UDTServerSocket serverSocket = new UDTServerSocket(localEnd);
			return serverSocket.accept();
		} else {
			ClientSession clientSession = new ClientSession(localEnd,
					destination);
			localEnd.addSession(clientSession.getSocketID(), clientSession);

			localEnd.start();
			clientSession.connect();
			// wait for handshake
			while (!clientSession.isReady()) {
				Thread.sleep(500);
			}
			log.info("The UDTClient is connected");
			Thread.sleep(500);
			return clientSession.getSocket();
		}
	}
}
