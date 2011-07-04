package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;


import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import udt.ClientSession;
import udt.UDPEndPoint;
import udt.UDPNIOEndPoint;
import udt.UDTClient;
import udt.UDTServerSocket;
import udt.UDTSession;
import udt.UDTSocket;
import udt.packets.Destination;
import udt.util.Util;

import com.jakeapp.availablelater.AvailableLater;
import com.jakeapp.availablelater.AvailableLaterObject;
import com.jakeapp.availablelater.AvailableLaterWaiter;


public class UDTTransferTest {

	private static final Logger log = Logger.getLogger(UDTTransferTest.class);

	private static final String MESSAGE = "secret message";

	private static final String MESSAGE2 = "secret answer";

	private DatagramSocket conServer;

	private DatagramChannel conServerChannel;

	private DatagramSocket conClient;

	private DatagramChannel conClientChannel;

	@Before
	public void setUp() throws Exception {
		conServerChannel = DatagramChannel.open();
		conServer = conServerChannel.socket();
		conServer.bind(null);
		conClientChannel = DatagramChannel.open();
		conClient = conClientChannel.socket();
		conClient.bind(null);
		log.debug("connecting client to server @ "
				+ conServer.getLocalSocketAddress());
		conClientChannel.connect(conServer.getLocalSocketAddress());
		conServerChannel.connect(conClient.getLocalSocketAddress());
	}

	@After
	public void tearDown() throws Exception {
		if (conServer != null)
			conServer.close();
		if (conClient != null)
			conClient.close();
	}


	@Test
	public void testUDPConnect() throws Exception {
		log.debug("sending...");
		conClient.send(new DatagramPacket(MESSAGE.getBytes("ASCII"), MESSAGE
				.getBytes().length, conServer.getLocalAddress(), conServer
				.getLocalPort()));
		log.debug("receiving...");
		byte[] buf = new byte[100];
		DatagramPacket dp = new DatagramPacket(buf, buf.length);
		conServer.receive(dp);
		Assert.assertEquals(
				new String(dp.getData(), 0, dp.getLength(), "ASCII"), MESSAGE);
	}

	@Test
	public void testUDPConnectByteBuffer() throws Exception {
		conClient.send(new DatagramPacket(MESSAGE.getBytes("ASCII"), MESSAGE
				.getBytes().length, conServer.getLocalAddress(), conServer
				.getLocalPort()));
		ByteBuffer buf = ByteBuffer.allocate(100);
		DatagramPacket dp = new DatagramPacket(buf.array(), buf.capacity());
		log.debug("waiting for first receive.");
		conServer.receive(dp);
		log.debug("first msg received.");
		Assert.assertEquals(
				new String(dp.getData(), 0, dp.getLength(), "ASCII"), MESSAGE);
		conClient.send(new DatagramPacket("foobar".getBytes("ASCII"), "foobar"
				.getBytes().length, conServer.getLocalAddress(), conServer
				.getLocalPort()));
		// buf.rewind();
		log.debug("waiting for second receive.");
		conServer.receive(dp);
		log.debug("second msg received.");
		Assert.assertFalse(MESSAGE.equals(new String(dp.getData(), 0, dp
				.getLength(), "ASCII")));
		Assert.assertEquals(
				new String(dp.getData(), 0, dp.getLength(), "ASCII"), "foobar");
	}

	@Test
	public void testUDPConnectChannel() throws Exception {
		ByteBuffer bufSend = ByteBuffer.wrap(MESSAGE.getBytes("ASCII"));
		Assert.assertNotNull(bufSend);
		Assert.assertNotNull(bufSend.array());
		Assert.assertNotNull(conServer.getLocalSocketAddress());
		conClientChannel.send(bufSend, conServer.getLocalSocketAddress());
		ByteBuffer buf = ByteBuffer.allocate(100);
		log.debug("waiting for first msg");
		conServerChannel.receive(buf);
		log.debug("first msg received");
		Assert.assertEquals(MESSAGE, new String(buf.array(), 0, buf.position(),
				"ASCII"));

		ByteBuffer bufSend2 = ByteBuffer.wrap("foobar".getBytes("ASCII"));
		UDPNIOEndPoint clientend = new UDPNIOEndPoint(conClientChannel,
				conClient.getLocalAddress(), conClient.getLocalPort(),
				conServer.getInetAddress(), conServer.getLocalPort());
		clientend.sendRaw(new DatagramPacket(bufSend2.array(),
				bufSend2.array().length));

		conClientChannel.send(bufSend2, conServer.getLocalSocketAddress());
		log.debug("waiting for second msg");
		buf.rewind();
		conServerChannel.receive(buf);
		log.debug("second msg received");
		Assert.assertEquals("foobar", new String(buf.array(), 0,
				buf.position(), "ASCII"));
		Assert.assertFalse(MESSAGE.equals(new String(buf.array(), 0, buf
				.position(), "ASCII")));
	}

	private AvailableLater<UDTSocket> createServer() {
		return new AvailableLaterObject<UDTSocket>() {

			@Override
			public UDTSocket calculate() throws Exception {
				log.debug("initializing server");
				UDPNIOEndPoint serverend = new UDPNIOEndPoint(conServerChannel,
						conServer.getLocalAddress(), conServer.getLocalPort(),
						InetAddress.getLocalHost(), conClient.getLocalPort());
				UDTSocket sock;
				if (false) {
					UDTServerSocket ss = new UDTServerSocket(serverend);
					log.debug("server accept() ...");
					sock = ss.accept();
					log.debug("server accept() returned");
				} else {
					log.debug("server: waiting up to 10 seconds for session");
					serverend.start(true);
					UDTSession session = null;
					session = serverend
							.getSession(10000, TimeUnit.MILLISECONDS);
					// wait for handshake to complete
					while (!session.isReady() || session.getSocket() == null) {
						log.debug("server is waiting for handshake response: session ready? "
								+ session.isReady());
						Thread.sleep(1000);
					}
					log.debug("server got socket!");
					sock = session.getSocket();
				}
				UDPEndPoint remoteEnd = sock.getEndpoint();
				if (!remoteEnd.getLocalAddress().equals(
						conServer.getInetAddress())
						|| remoteEnd.getLocalPort() != conServer.getPort()) {
					log.error("received a different client than negotiated.");
				}
				return sock;
			}
		}.start();
	}

	private AvailableLater<UDTSocket> createClient() {
		return new AvailableLaterObject<UDTSocket>() {

			@Override
			public UDTSocket calculate() throws Exception {
				UDPNIOEndPoint clientend = new UDPNIOEndPoint(conClientChannel,
						conClient.getLocalAddress(), conClient.getLocalPort(),
						InetAddress.getLocalHost(), conClient.getPort());
				if (conClient.getInetAddress() == null)
					throw new NullPointerException();
				UDTSocket c;
				if (false) {
					// c = new UDTClient(clientend);
					// log.debug("client connect()");
					// c.connect(conServer.getInetAddress().getHostAddress(),
					// conServer.getLocalPort());
				} else {
					Destination destination = new Destination(
							InetAddress.getLocalHost(), conClient.getPort());
					ClientSession sess = new ClientSession(clientend,
							destination);
					// destination.setSocketID(sess.getSocketID());
					log.debug("client: adding session");
					clientend.addSession(sess.getSocketID(), sess);
					log.debug("client: starting communication");
					clientend.start();
					log.debug("client: connecting...");
					sess.connect();
					// wait for handshake
					while (!sess.isReady()) {
						log.debug("client: waiting for session to be ready ...");
						Thread.sleep(500);
					}
					c = sess.getSocket();
				}
				log.debug("client connect() done");
				return c;
			}
		}.start();
	}

	@Test
	public void testConnect() throws Exception {
		AvailableLater<UDTSocket> avlServer = createServer();
		AvailableLater<UDTSocket> avlClient = createClient();
		log.debug("waiting for client avl");
		UDTSocket client = AvailableLaterWaiter.await(avlClient);
		log.debug("waiting for server avl");
		UDTSocket server = AvailableLaterWaiter.await(avlServer);
		// ` server.
		log.debug("writing on server");
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(
				server.getOutputStream(), "UTF-8"));
		pw.println(MESSAGE);
		pw.flush();

		log.debug("reading on client");
		String recv = Util.readLine(client.getInputStream());

		log.debug("matching");
		Assert.assertEquals(recv, MESSAGE);
		log.debug("done");

		log.debug("writing on client");
		pw = new PrintWriter(new OutputStreamWriter(client.getOutputStream(),
				"UTF-8"));
		pw.println(MESSAGE2);
		pw.flush();

		log.debug("reading on server");
		recv = Util.readLine(server.getInputStream());

		log.debug("matching");
		Assert.assertEquals(recv, MESSAGE2);
		log.debug("done");


		client.close();
		server.close();
		conServer.close();
		conClient.close();
	}

}
