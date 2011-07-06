package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;
import local.test.Tracer;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.exceptions.NotLoggedInException;
import com.jakeapp.jake.ics.filetransfer.FileRequestFileMapper;
import com.jakeapp.jake.ics.filetransfer.IncomingTransferListener;
import com.jakeapp.jake.ics.filetransfer.exceptions.OtherUserDoesntHaveRequestedContentException;
import com.jakeapp.jake.ics.filetransfer.methods.ITransferMethod;
import com.jakeapp.jake.ics.filetransfer.negotiate.FileRequest;
import com.jakeapp.jake.ics.filetransfer.negotiate.INegotiationSuccessListener;
import com.jakeapp.jake.ics.filetransfer.runningtransfer.IFileTransfer;
import com.jakeapp.jake.ics.impl.mock.MockUserId;
import com.jakeapp.jake.ics.msgservice.IMessageReceiveListener;
import com.jakeapp.jake.ics.msgservice.IMsgService;
import com.jakeapp.jake.test.SimpleFakeMessageExchanger;

public class TestICSFileTransfer {

	private static Logger log = Logger.getLogger(TestICSFileTransfer.class);

	private UserId userid2 = new MockUserId("provider@testhost");

	private UserId userid1 = new MockUserId("requester@testhost");

	private IceUdtTransferFactory sftf;

	private String filename = "myfile.txt";

	private FileRequest outrequest = new FileRequest(this.filename, false,
			this.userid2);

	private FileRequest inrequest = new FileRequest(this.filename, true,
			this.userid1);

	private SimpleFakeMessageExchanger msgX;

	private Tracer t;

	private File testfile;

	public String content = "Hello this is nice of you to write such nice things nice, eh";


	private Queue<ITransferMethod> runningServers = new ConcurrentLinkedQueue<ITransferMethod>();

	private UdtOverIceAddressesConnect udtconnect;

	@Before
	public void setUp() throws Exception {
		t = new Tracer();
		testfile = File.createTempFile("testContent", "bin");
		FileWriter fw = new FileWriter(testfile);
		for (int i = 0; i < 1; i++)
			fw.append(content);
		fw.close();
		testfile.deleteOnExit();
		msgX = new SimpleFakeMessageExchanger();
		udtconnect = new UdtOverIceAddressesConnect();
		sftf = new IceUdtTransferFactory(udtconnect, 10);
	}


	@After
	public void teardown() throws Exception {
		msgX = null;
		sftf = null;
		testfile.delete();
		for (ITransferMethod tm : runningServers) {
			tm.stopServing();
			runningServers.remove(tm);
		}
	}

	private IFileTransfer fileTransfer;

	private void createFriendlyPeer(final Tracer t, UserId userid2)
			throws NotLoggedInException {
		createPeer(t, userid2, true);
	}

	private void createMeanPeer(final Tracer t, UserId userid2)
			throws NotLoggedInException {
		createPeer(t, userid2, false);
	}

	private void createPeer(final Tracer t, UserId userid2,
			final boolean acceptAction) throws NotLoggedInException {
		createPeer(t, userid2, acceptAction, true);
	}

	private void createEmptyPeer(final Tracer t, UserId userid2)
			throws NotLoggedInException {
		createPeer(t, userid2, true, false);
	}

	private void createPeer(final Tracer t, UserId userid2,
			final boolean acceptAction, final boolean hasTestFile)
			throws NotLoggedInException {
		log.debug("createFriendlyPeer: creating MsgService");
		IMsgService msg = msgX.addUser(userid2);
		msg.registerReceiveMessageListener(new IMessageReceiveListener() {

			private Logger log = Logger
					.getLogger("Peer:IMessageReceiveListener");

			@Override
			public void receivedMessage(UserId from_userid, String content) {
				this.log.info("receivedMessage: " + from_userid + ": "
						+ content);
				this.log.info("don't care, transfer should handle it");
			}
		});
		ITransferMethod tfm = sftf.getTransferMethod(msg, userid2);
		runningServers.add(tfm);

		FileRequestFileMapper mapper;
		if (hasTestFile)
			mapper = new TestFileMapper(t);
		else
			mapper = new NoFilesMapper();

		tfm.startServing(new IncomingTransferListener() {

			private Logger log = Logger
					.getLogger("Peer:IncomingTransferListener");

			@Override
			public boolean accept(FileRequest req) {
				log.debug("accept(): " + req);
				Assert.assertEquals(inrequest, req);
				Assert.assertEquals(inrequest.getFileName(), req.getFileName());
				return acceptAction;
			}

			@Override
			public void started(IFileTransfer ft) {
				log.debug("started(): " + ft);
				t.step("started on server");
				Assert.assertEquals(inrequest, ft.getFileRequest());
			}
		}, mapper);
	}


	@Test
	public void testSocketMethod_accepting() throws Exception {
		IMsgService msg = msgX.addUser(userid1);
		createFriendlyPeer(t, userid2);

		ITransferMethod tfm;
		tfm = sftf.getTransferMethod(msg, userid1);
		runningServers.add(tfm);

		tfm.request(this.outrequest, new INegotiationSuccessListener() {

			private Logger log = Logger
					.getLogger("Main:INegotiationSuccessListener");

			@Override
			public void failed(Exception reason) {
				this.log.debug("clientside negotiation failed: ", reason);
				t.step("clientside negotiation failed");
				Assert.fail();
			}

			@Override
			public void succeeded(IFileTransfer ft) {
				this.log.debug("clientside negotiation succeeded" + ft);
				t.step("clientside negotiation succeeded");
				Assert.assertEquals(filename, ft.getFileName());
				TestICSFileTransfer.this.fileTransfer = ft;
			}
		});

		Assert.assertTrue(this.t.awaitStep("clientside negotiation succeeded",
				1, TimeUnit.SECONDS));

		TestICSFileTransfer.log
				.debug("main: waiting for filetransfer to finish: "
						+ this.fileTransfer);


		while (!this.fileTransfer.isDone()) {
			try {
				log.debug(this.t.toString());
				Thread.sleep(40);
			} catch (InterruptedException e) {
				// best effort, doesn't matter
			}
		}
		Assert.assertTrue(this.t.await(
				"getting content from fs,started on server", 40,
				TimeUnit.MILLISECONDS));
		log.debug(this.t.toString());
		Assert.assertTrue("timeout", this.t.isDone(40, TimeUnit.MILLISECONDS));
		Assert.assertTrue(testfile.length() > 0);
		Assert.assertEquals(testfile.length(), this.fileTransfer.getLocalFile()
				.length());
	}

	@Test
	public void testUnavailableFile() throws Exception {
		IMsgService msg = msgX.addUser(userid1);
		createEmptyPeer(t, userid2);

		ITransferMethod tfm;
		tfm = sftf.getTransferMethod(msg, userid1);
		runningServers.add(tfm);

		tfm.request(this.outrequest, new INegotiationSuccessListener() {

			private Logger log = Logger
					.getLogger("Main:INegotiationSuccessListener");

			@Override
			public void failed(Exception reason) {
				this.log.debug("clientside negotiation failed: " + reason);
				t.step("clientside negotiation failed");
				Assert.assertEquals(reason.getClass(),
						OtherUserDoesntHaveRequestedContentException.class);
			}

			@Override
			public void succeeded(IFileTransfer ft) {
				this.log.debug("clientside negotiation succeeded" + ft);
				t.step("clientside negotiation succeeded");
				Assert.fail();
			}
		});

		Assert.assertTrue(this.t.awaitStep("clientside negotiation failed", 300,
				TimeUnit.SECONDS));

		Assert.assertTrue("timeout", this.t.isDone(10, TimeUnit.SECONDS));
	}

	@Test
	public void testCancellingServer() throws Exception {
		IMsgService msg = msgX.addUser(userid1);
		createMeanPeer(t, userid2);

		ITransferMethod tfm;
		tfm = sftf.getTransferMethod(msg, userid1);
		runningServers.add(tfm);

		tfm.request(this.outrequest, new INegotiationSuccessListener() {

			private Logger log = Logger
					.getLogger("Main:INegotiationSuccessListener");

			@Override
			public void failed(Exception reason) {
				this.log.debug("clientside negotiation failed: " + reason);
				t.step("clientside negotiation failed");
				Assert.assertEquals(reason.getClass(),
						OtherUserDoesntHaveRequestedContentException.class);
			}

			@Override
			public void succeeded(IFileTransfer ft) {
				this.log.debug("clientside negotiation succeeded" + ft);
				t.step("clientside negotiation succeeded");
				Assert.fail();
			}
		});

		Assert.assertTrue(this.t.awaitStep("clientside negotiation failed", 1,
				TimeUnit.SECONDS));

		Assert.assertTrue("timeout", this.t.isDone(1000, TimeUnit.MILLISECONDS));
	}

	public File getTestContentFile() throws IOException {
		File tmp = File.createTempFile("my_cute_", "tempfile");
		tmp.deleteOnExit();
		FileChannel fic = new FileInputStream(testfile).getChannel();
		FileChannel foc = new FileOutputStream(tmp).getChannel();
		fic.transferTo(0, testfile.length(), foc);
		fic.close();
		foc.close();
		return tmp;
	}

	@Test
	public void testStream() throws IOException {
		char[] cbuforig = new char[(int) (this.testfile.length() + 1)];
		char[] cbuftmp = new char[(int) (this.testfile.length() + 1)];

		FileInputStream fis = new FileInputStream(getTestContentFile());
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		int len = (br.read(cbuftmp));

		FileInputStream fisorig = new FileInputStream(this.testfile);
		BufferedReader brorig = new BufferedReader(new InputStreamReader(
				fisorig));
		int lenorig = (brorig.read(cbuforig));

		Assert.assertEquals(lenorig, len);
		Assert.assertEquals(new String(cbuforig), new String(cbuftmp));
	}

	private final class TestFileMapper implements FileRequestFileMapper {

		private final Tracer t;

		private Logger log = Logger.getLogger("Peer:FileRequestFileMapper");

		private TestFileMapper(Tracer t) {
			this.t = t;
		}

		@Override
		public File getFileForRequest(FileRequest r) {
			this.log.debug("getFileForRequest(): " + r);
			Assert.assertEquals(TestICSFileTransfer.this.filename,
					r.getFileName());
			t.step("getting content from fs");
			try {
				return getTestContentFile();
			} catch (FileNotFoundException e) {
				this.log.debug(e);
				return null;
			} catch (IOException e) {
				this.log.debug(e);
				return null;
			}
		}
	}

	static private final class NoFilesMapper implements FileRequestFileMapper {

		@Override
		public File getFileForRequest(@SuppressWarnings("unused") FileRequest r) {
			return null;
		}
	}
}
