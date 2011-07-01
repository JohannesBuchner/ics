package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;


import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import udt.UDTClient;
import udt.UDTSocket;
import udt.util.Util;

import com.jakeapp.availablelater.AvailableLater;
import com.jakeapp.availablelater.AvailableLaterObject;
import com.jakeapp.availablelater.AvailableLaterWaiter;
import com.jakeapp.jake.ics.impl.mock.MockUserId;
import com.jakeapp.jake.ics.msgservice.IMsgService;


public class TestUDTOverICEConnect {

	private static final Logger log = Logger
			.getLogger(TestUDTOverICEConnect.class);

	private static final String MESSAGE = "secret message";

	private UDTOverICEConnect conClient;

	private UDTOverICEConnect conServer;

	private IMsgService msgClient;

	private IMsgService msgServer;

	@Before
	public void setUp() throws Exception {
		msgServer = MockConnectingMsgService.createInstance(new MockUserId(
				"myserver@localhost"));
		msgClient = MockConnectingMsgService.createInstance(new MockUserId(
				"myclient@localhost"));
	}


	private AvailableLater<UDTSocket> createServer() {
		return new AvailableLaterObject<UDTSocket>() {

			@Override
			public UDTSocket calculate() throws Exception {
				log.debug("initializing server");
				conServer = new UDTOverICEConnect(msgServer);
				return conServer.initiateSending(new MockUserId(
						"myclient@localhost"));
			}
		}.start();
	}

	private AvailableLater<UDTSocket> createClient() {
		return new AvailableLaterObject<UDTSocket>() {

			@Override
			public UDTSocket calculate() throws Exception {
				log.debug("initializing client");
				conClient = new UDTOverICEConnect(msgClient);
				log.debug("initializing client");
				return conClient.initiateReceiving(new MockUserId(
						"myserver@localhost"));
			}
		}.start();
	}

	@Test
	public void testConnect() throws Exception {
		log.debug("logging server & client in");
		log.debug("both logged in.");
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

		client.close();
		server.close();
		conServer.shutdown();
		conClient.shutdown();
	}
}
