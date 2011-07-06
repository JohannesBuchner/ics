package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.jakeapp.availablelater.AvailableLater;
import com.jakeapp.availablelater.AvailableLaterWrapperObject;

import net.mc_cubed.icedjava.ice.IcePeer;
import net.mc_cubed.icedjava.ice.IceSocket;
import udt.UDTSocket;

/**
 * Connect using Udt given a Ice connection
 * 
 * @author johannes
 */
public abstract class IUdtOverIceConnect {

	protected int component = 0;

	private Map<IcePeer, UDTSocket> sockets = new HashMap<IcePeer, UDTSocket>();

	public final UDTSocket connect(IceSocket socket, IcePeer peer,
			boolean server) throws InterruptedException, IOException {
		if (peer == null)
			throw new NullPointerException();
		if (socket == null)
			throw new NullPointerException();
		if (peer.getNominated().get(socket) == null)
			throw new NullPointerException(
					"not the right socket for this peer?");

		UDTSocket sock = sockets.get(peer);
		if (sock != null && sock.isActive()) {
			return sock;
		}
		sock = doConnect(socket, peer, server);
		sockets.put(peer, sock);
		return sock;
	}

	public final AvailableLater<UDTSocket> connect(final IceSocket socket,
			AvailableLater<IcePeer> peer, final boolean server) {
		return new AvailableLaterWrapperObject<UDTSocket, IcePeer>(peer) {

			@Override
			public UDTSocket calculate() throws Exception {
				return connect(socket, getSourceResult(), server);
			}
		}.start();
	}

	protected abstract UDTSocket doConnect(IceSocket socket, IcePeer peer,
			boolean server) throws InterruptedException, IOException;

}