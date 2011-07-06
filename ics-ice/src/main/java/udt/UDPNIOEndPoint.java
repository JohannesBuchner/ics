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
import java.nio.IntBuffer;
import java.nio.channels.ByteChannel;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import udt.packets.ConnectionHandshake;
import udt.packets.Destination;
import udt.packets.PacketFactory;
import udt.util.UDTThreadFactory;

/**
 * the UDPEndpoint takes care of sending and receiving UDP network packets,
 * dispatching them to the correct {@link UDTSession}
 */
public class UDPNIOEndPoint extends UDPEndPoint {

	private static final boolean USE_HEADER = false;

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

	private final InetAddress addr;

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
		if (socket == null) {
			throw new NullPointerException();
		}
		dgSocket = socket;
		port = localport;
		addr = localaddr;
		dp.setAddress(remoteaddr);
		dp.setPort(remoteport);
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
	@Override
	protected UDTSession accept(long timeout, TimeUnit unit)
			throws InterruptedException {
		return sessionHandoff.poll(timeout, unit);
	}

	@Override
	protected void doReceive() throws IOException {
		ByteBuffer hdr = ByteBuffer.wrap(new byte[4]);
		IntBuffer hdrint = hdr.asIntBuffer();
		ByteBuffer buf = ByteBuffer.wrap(new byte[DATAGRAM_SIZE]);
		while (!stopped) {
			try {
				try {
					// will block until a packet is received or timeout has
					// expired
					buf.rewind();
					if (USE_HEADER) {
						hdr.rewind();
						hdr.limit(hdr.capacity());
						int hl = dgSocket.read(hdr);
						if (hl == 0)
							continue;
						if (hl == -1)
							return;
						int length = hdrint.get();
						buf.limit(length);
						logger.info("expecting to receive " + length
								+ " bytes... got space for " + buf.remaining());
					} else {
					}

					int l = dgSocket.read(buf);
					if (l == -1) {
						return;
					}
					if (l == 0)
						continue;

					logger.info("but received " + l + " bytes.");
					for (int i = 0; i < l; i++) {
						if (buf.get(i) != 0)
							break;
						else if (i == l - 1)
							throw new IOException("received " + l + " zeros.");
					}
					if (!buf.hasArray())
						throw new IOException("expected received length " + l
								+ " to be the same as limit after flipping");

					Destination peer = new Destination(dp.getAddress(),
							dp.getPort());
					UDTPacket packet = PacketFactory.createPacket(buf.array(),
							l);
					lastPacket = packet;
					peer.setSocketID(packet.getDestinationID());
					logger.info("received socket/destination id "
							+ peer.getSocketID());

					// handle connection handshake
					if (packet.isConnectionHandshake()) {
						logger.log(Level.INFO,
								"got a packet: its a connection-handshake");
						synchronized (lock) {
							Long id = Long.valueOf(packet.getDestinationID());
							UDTSession session = getSession(id);
							if (session == null) {
								logger.info("got a packet: its a connection-handshake for a new session");
								// don't need data in dp
								session = new ServerSession(dp, this);
								logger.info("new session/socket id="
										+ session.getSocketID());
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

	@Override
	protected void doSend(UDTPacket packet) throws IOException {
		byte[] data = packet.getEncoded();
		ByteBuffer buf = ByteBuffer.wrap(data);
		if (USE_HEADER) {
			logger.info("sending " + data.length + " bytes");
			ByteBuffer hdr = ByteBuffer.allocate(4);
			IntBuffer intBuffer = hdr.asIntBuffer();
			intBuffer.put(data.length);
			int n = dgSocket.write(hdr);
			n += dgSocket.write(buf);
			if (n != data.length + 4)
				throw new IOException("did not send full data packet (4 + "
						+ data.length + " bytes), only " + n + " bytes.");
		} else {
			int n = dgSocket.write(buf);
			if (n != data.length)
				throw new IOException("did not send full data packet ("
						+ data.length + " bytes), only " + n + " bytes.");
		}
		// DatagramPacket dgp = packet.getSession().getDatagram();
		// dgp.setData(data);
		// dgSocket.write(ByteBuffer.wrap(dgp.getData(), dgp.getOffset(),
		// dgp.getLength()));
	}

	@Override
	UDTPacket getLastPacket() {
		return lastPacket;
	}


	/**
	 * @return Gets the local address to which the socket is bound
	 */
	@Override
	public InetAddress getLocalAddress() {
		return addr;
	}

	/**
	 * @return the port which this client is bound to
	 */
	@Override
	public int getLocalPort() {
		return port;
	}

	public UDTSession getSession(long timeout, TimeUnit unit)
			throws InterruptedException {
		return accept(timeout, unit);
	}

	@Override
	public void sendRaw(DatagramPacket dgp) throws IOException {
		dgSocket.write(ByteBuffer.wrap(dgp.getData(), dgp.getOffset(),
				dgp.getLength()));
	}

	@Override
	public void start() {
		start(false);
	}

	/**
	 * start the endpoint. If the serverSocketModeEnabled flag is
	 * <code>true</code>, a new connection can be handed off to an application.
	 * The application needs to call #accept() to get the socket
	 * 
	 * @param serverSocketModeEnabled
	 */
	@Override
	public void start(boolean serverSocketModeEnabled) {
		serverSocketMode = serverSocketModeEnabled;
		// start receive thread
		Runnable receive = new Runnable() {

			@Override
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

	@Override
	public void stop() {
		stopped = true;
		try {
			dgSocket.close();
		} catch (IOException e) {
			// TODO: warn
		}
	}

	@Override
	public String toString() {
		return "UDPEndpoint port=" + port;
	}
}
