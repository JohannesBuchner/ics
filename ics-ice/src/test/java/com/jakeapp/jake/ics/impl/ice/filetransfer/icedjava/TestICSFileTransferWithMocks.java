package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;

import local.test.Tracer;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import udt.ClientSession;
import udt.UDPEndPoint;
import udt.UDPNIOEndPoint;
import udt.UDTServerSocket;
import udt.UDTSocket;
import udt.packets.Destination;

import com.jakeapp.jake.ics.filetransfer.negotiate.FileRequest;
import com.jakeapp.jake.ics.filetransfer.runningtransfer.Status;
import com.jakeapp.jake.ics.impl.mock.MockUserId;
import com.jakeapp.jake.ics.impl.sockets.filetransfer.AESObject;


public class TestICSFileTransferWithMocks {

	private static final Logger log = Logger
			.getLogger(TestICSFileTransferWithMocks.class);

	private Tracer t;

	private File testfile;

	private UDTServerSocket server;

	public String content = "Hello this is nice of you to write such nice things nice, eh";

	private UDTSocket client;

	FileRequest fr = new FileRequest("myfile.txt", true, new MockUserId(
			"otherpeer"));

	private IceUdtFileTransfer ft;

	@Before
	public void setUp() throws Exception {
		t = new Tracer();
		testfile = File.createTempFile("testContent", "bin");
		FileWriter fw = new FileWriter(testfile);
		for (int i = 0; i < 1; i++)
			fw.append(content);
		fw.close();
		testfile.deleteOnExit();
		server = new UDTServerSocket(new UDPEndPoint());
		new Thread(new ServeThread(this.server)).start();

		Destination destination = new Destination(
				InetAddress.getByName("localhost"), server.getEndpoint()
						.getLocalPort());
		UDPEndPoint peerEnd = new UDPEndPoint();
		log.debug("connecting to " + destination + " peerEnd=" + peerEnd);
		ClientSession sess = new ClientSession(peerEnd, destination);
		// destination.setSocketID(sess.getSocketID());
		log.debug("client: adding session");
		peerEnd.addSession(sess.getSocketID(), sess);
		log.debug("client: starting communication");
		peerEnd.start();
		log.debug("client: connecting...");
		sess.connect();
		client = sess.getSocket();
	}


	@After
	public void teardown() throws Exception {
		if (server != null)
			server.shutDown();
		if (client != null)
			client.close();
		testfile.delete();
		if (ft != null && ft.getLocalFile() != null)
			ft.getLocalFile().delete();
	}

	@Test
	public void testClient() throws Exception {
		AESObject aes = mock(AESObject.class);
		when(aes.decrypt((InputStream) Matchers.any())).thenAnswer(
				new Answer<InputStream>() {

					@Override
					public InputStream answer(InvocationOnMock invocation)
							throws Throwable {
						return (InputStream) invocation.getArguments()[0];
					}
				});

		ft = new IceUdtFileTransfer(fr, this.client, aes);
		ft.run();
		Assert.assertFalse(ft.getStatus() == Status.error);
		Assert.assertTrue(ft.getStatus() == Status.complete);
		Assert.assertNull(ft.getError());
		Assert.assertTrue(ft.getLocalFile().exists());
		Assert.assertTrue(ft.getLocalFile().isFile());
		Assert.assertEquals(content.length(), ft.getLocalFile().length());
	}

	public class ServeThread implements Runnable {

		private final Logger log = Logger.getLogger(ServeThread.class);

		public ServeThread(UDTServerSocket server) {
			this.server = server;
		}

		private UDTServerSocket server;

		@Override
		public void run() {
			try {
				UDTSocket client = server.accept();
				log.debug("got a client");
				OutputStream output = client.getOutputStream();
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
						output));
				log.debug("writing content ...");
				bw.write(content);
				log.debug("writing content ... done");
				bw.flush();
				output.flush();
				bw.close();
				output.close();
				log.debug("closed");
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
