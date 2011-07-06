package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;


import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import udt.UDTSocket;
import udt.util.Util;

import com.jakeapp.availablelater.AvailableLater;
import com.jakeapp.availablelater.AvailableLaterWaiter;
import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.impl.mock.MockUserId;
import com.jakeapp.jake.ics.msgservice.IMsgService;


public class TestUDTOverICEConnect {

	private static final Logger log = Logger
			.getLogger(TestUDTOverICEConnect.class);

	private static final String MESSAGE = "secret message";

	private static final String MESSAGE2 = "secret answer";

	private IMsgService msgClient;

	private IMsgService msgServer;

	private IIceConnect conClient;

	private IIceConnect conServer;

	private UserId serverUserId = new MockUserId("myserver@localhost");

	private UserId clientUserId = new MockUserId("myclient@localhost");

	private IUdtOverIceConnect udtconnect;

	private AvailableLater<UDTSocket> createClient() {
		Assert.assertTrue(conClient.hasCandidatePair(serverUserId));
		return udtconnect.connect(conClient.getSocket(),
				conClient.getNomination(serverUserId), false);
	}

	private AvailableLater<UDTSocket> createServer() {
		Assert.assertFalse(conServer.hasCandidatePair(clientUserId));
		return udtconnect.connect(conServer.getSocket(),
				conServer.getNomination(clientUserId), true);
	}

	@Before
	public void setUp() throws Exception {
		msgServer = MockConnectingMsgService.createInstance(serverUserId);
		msgClient = MockConnectingMsgService.createInstance(clientUserId);
		udtconnect = new UdtOverIceAddressesConnect();
		conClient = new IceConnect(msgClient);
		conServer = new IceConnect(msgServer);
	}

	@After
	public void tearDown() throws Exception {
		if (conServer != null) {
			conServer.close(clientUserId);
		}
		if (conClient != null) {
			conClient.close(serverUserId);
		}
	}

	@Test()
	public void testConnect() throws Exception {
		log.debug("logging server & client in");
		log.debug("both logged in.");
		AvailableLater<UDTSocket> avlServer = createServer();
		log.debug("waiting for negotiation");
		// client can only be found after server has started negotiation.
		AvailableLaterWaiter.await(conServer.getNomination(clientUserId));

		AvailableLater<UDTSocket> avlClient = createClient();
		log.debug("waiting for server avl");
		UDTSocket server = AvailableLaterWaiter.await(avlServer);
		log.debug("waiting for client avl");
		UDTSocket client = AvailableLaterWaiter.await(avlClient);
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
	}
}
