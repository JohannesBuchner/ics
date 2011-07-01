/*********************************************************************************
 * Copyright (c) 2010 Forschungszentrum Juelich GmbH 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * (1) Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the disclaimer at the end. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * 
 * (2) Neither the name of Forschungszentrum Juelich GmbH nor the names of its 
 * contributors may be used to endorse or promote products derived from this 
 * software without specific prior written permission.
 * 
 * DISCLAIMER
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *********************************************************************************/

package udt;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sdp.Info;

import org.apache.log4j.lf5.LogLevel;

import net.mc_cubed.icedjava.ice.IceSocketChannel;
import udt.packets.ConnectionHandshake;
import udt.packets.Destination;
import udt.packets.PacketFactory;
import udt.util.UDTThreadFactory;

/**
 * the UDPEndpoint takes care of sending and receiving UDP network packets,
 * dispatching them to the correct {@link UDTSession}
 */
public class UDPNIOEndPoint extends UDPEndPoint {

	private static final Logger logger = Logger.getLogger(ClientSession.class
			.getName());

	private final int port;

	private final ByteChannel dgSocket;

	// last received packet
	private UDTPacket lastPacket;

	// if the endpoint is configured for a server socket,
	// this queue is used to handoff new UDTSessions to the application
	private final SynchronousQueue<UDTSession> sessionHandoff = new SynchronousQueue<UDTSession>();

	private boolean serverSocketMode = false;

	// has the endpoint been stopped?
	private volatile boolean stopped = false;

	private InetAddress addr;

	/**
	 * create an endpoint on the given socket
	 * 
	 * @param socket
	 *            - a UDP datagram socket
	 * @throws UnknownHostException
	 * @throws SocketException
	 */
	public UDPNIOEndPoint(ByteChannel socket, InetAddress localaddr,
			int localport, InetAddress remoteaddr, int remoteport)
			throws SocketException, UnknownHostException {
		// we don't want to use the super implementation.
		super();
		super.stop();
		if (socket == null)
			throw new NullPointerException();
		this.dgSocket = socket;
		this.port = localport;
		this.addr = localaddr;
		dp.setAddress(remoteaddr);
		dp.setPort(remoteport);
	}

	/**
	 * start the endpoint. If the serverSocketModeEnabled flag is
	 * <code>true</code>, a new connection can be handed off to an application.
	 * The application needs to call #accept() to get the socket
	 * 
	 * @param serverSocketModeEnabled
	 */
	public void start(boolean serverSocketModeEnabled) {
		serverSocketMode = serverSocketModeEnabled;
		// start receive thread
		Runnable receive = new Runnable() {

			public void run() {
				try {
					doReceive();
				} catch (Exception ex) {
					logger.log(Level.WARNING, "", ex);
				}
			}
		};
		Thread t = UDTThreadFactory.get().newThread(receive);
		t.setName("UDPEndpoint-" + t.getName());
		t.setDaemon(true);
		t.start();
		logger.info("UDTEndpoint started.");
	}

	public void start() {
		start(false);
	}

	public void stop() {
		stopped = true;
		try {
			dgSocket.close();
		} catch (IOException e) {
			// TODO: warn
		}
	}

	/**
	 * @return the port which this client is bound to
	 */
	public int getLocalPort() {
		return this.port;
	}

	/**
	 * @return Gets the local address to which the socket is bound
	 */
	public InetAddress getLocalAddress() {
		return this.addr;
	}

	UDTPacket getLastPacket() {
		return lastPacket;
	}

	/**
	 * wait the given time for a new connection
	 * 
	 * @param timeout
	 *            - the time to wait
	 * @param unit
	 *            - the {@link TimeUnit}
	 * @return a new {@link UDTSession}
	 * @throws InterruptedException
	 */
	protected UDTSession accept(long timeout, TimeUnit unit)
			throws InterruptedException {
		return sessionHandoff.poll(timeout, unit);
	}

	public UDTSession getSession(long timeout, TimeUnit unit)
			throws InterruptedException {
		return accept(timeout, unit);
	}


	/**
	 * single receive, run in the receiverThread, see {@link #start()}
	 * <ul>
	 * <li>Receives UDP packets from the network</li>
	 * <li>Converts them to UDT packets</li>
	 * <li>dispatches the UDT packets according to their destination ID.</li>
	 * </ul>
	 * 
	 * @throws IOException
	 */
	private long lastDestID = -1;

	private UDTSession lastSession;

	private int n = 0;

	private final Object lock = new Object();

	protected void doReceive() throws IOException {
		while (!stopped) {
			try {
				try {
					ByteBuffer buf = ByteBuffer.allocate(DATAGRAM_SIZE);
					// buf.rewind();
					// will block until a packet is received or timeout has
					// expired
					int l = dgSocket.read(buf);
					if (l == -1) {
						return;
					}

					Destination peer = new Destination(dp.getAddress(),
							dp.getPort());
					UDTPacket packet = PacketFactory.createPacket(buf.array(),
							l);
					lastPacket = packet;

					// handle connection handshake
					if (packet.isConnectionHandshake()) {
						logger.log(Level.INFO, "got a packet: its a connection-handshake");
						synchronized (lock) {
							Long id = Long.valueOf(packet.getDestinationID());
							UDTSession session = getSession(id);
							if (session == null) {
								logger.log(Level.INFO, "got a packet: its a connection-handshake for a new session");
								// don't need data in dp
								session = new ServerSession(dp, this);
								addSession(session.getSocketID(), session);
								// TODO need to check peer to avoid duplicate
								// server session
								if (serverSocketMode) {
									logger.fine("Pooling new request.");
									sessionHandoff.put(session);
									logger.fine("Request taken for processing.");
								}
							}
							peer.setSocketID(((ConnectionHandshake) packet)
									.getSocketID());
							session.received(packet, peer);
						}
					} else {
						// dispatch to existing session
						long dest = packet.getDestinationID();
						UDTSession session;
						if (dest == lastDestID) {
							session = lastSession;
						} else {
							session = getSession(dest);
							lastSession = session;
							lastDestID = dest;
						}
						if (session == null) {
							n++;
							if (n % 100 == 1) {
								logger.warning("Unknown session <" + dest
										+ "> requested from <" + peer
										+ "> packet type "
										+ packet.getClass().getName());
							}
						} else {
							session.received(packet, peer);
						}
					}
				} catch (SocketException ex) {
					logger.log(Level.INFO,
							"SocketException: " + ex.getMessage());
				} catch (SocketTimeoutException ste) {
					// can safely ignore... we will retry until the endpoint is
					// stopped
				}

			} catch (Exception ex) {
				logger.log(Level.WARNING, "Got: " + ex.getMessage(), ex);
			}
		}
	}

	protected void doSend(UDTPacket packet) throws IOException {
		byte[] data = packet.getEncoded();
		ByteBuffer buf = ByteBuffer.wrap(data);
		dgSocket.write(buf);
		// DatagramPacket dgp = packet.getSession().getDatagram();
		// dgp.setData(data);
		// dgSocket.write(ByteBuffer.wrap(dgp.getData(), dgp.getOffset(),
		// dgp.getLength()));
	}

	public String toString() {
		return "UDPEndpoint port=" + port;
	}

	public void sendRaw(DatagramPacket dgp) throws IOException {
		dgSocket.write(ByteBuffer.wrap(dgp.getData(), dgp.getOffset(),
				dgp.getLength()));
	}
}
