package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;


import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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

	@Before
	public void setUp() throws Exception {
		msgServer = MockConnectingMsgService.createInstance(serverUserId);
		msgClient = MockConnectingMsgService.createInstance(clientUserId);
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

	@Test(timeout = 30000)
	public void testUdtOverIceAddressConnect() throws Exception {
		testConnect(new UdtOverIceAddressesConnect());
	}

	@Test(timeout = 30000)
	@Ignore
	public void testUdtOverIceStreamsConnect() throws Exception {
		testConnect(new UdtOverIceConnect());
	}

	public void testConnect(IUdtOverIceConnect udtconnect) throws Exception {
		log.debug("logging server & client in");
		log.debug("both logged in.");
		log.debug("waiting for negotiation");
		// client can only be found after server has started negotiation.
		AvailableLaterWaiter.await(conServer.getNomination(clientUserId));

		Assert.assertTrue(conServer.hasCandidatePair(clientUserId));
		if (!conClient.hasCandidatePair(serverUserId))
			Thread.sleep(10);
		Assert.assertTrue(conClient.hasCandidatePair(serverUserId));

		AvailableLater<UDTSocket> avlServer = udtconnect.connect(
				conServer.getSocket(), conServer.getNomination(clientUserId),
				true);
		AvailableLater<UDTSocket> avlClient = udtconnect.connect(
				conClient.getSocket(), conClient.getNomination(serverUserId),
				false);

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
