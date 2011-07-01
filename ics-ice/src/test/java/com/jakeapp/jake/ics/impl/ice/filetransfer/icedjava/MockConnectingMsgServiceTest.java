package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;


import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.exceptions.OtherUserOfflineException;
import com.jakeapp.jake.ics.impl.mock.MockUserId;
import com.jakeapp.jake.ics.msgservice.IMessageReceiveListener;
import com.jakeapp.jake.ics.msgservice.IMsgService;


public class MockConnectingMsgServiceTest {

	private UserId aid = new MockUserId("a");

	private UserId bid = new MockUserId("b");

	private IMsgService b = MockConnectingMsgService.createInstance(bid);

	private IMsgService a = MockConnectingMsgService.createInstance(aid);

	private String arecv;

	private String brecv;

	private final static String MESSAGE = "secret";


	@Before
	public void setup() {
		arecv = null;
		a.registerReceiveMessageListener(new IMessageReceiveListener() {

			@Override
			public void receivedMessage(UserId from_userid, String content) {
				arecv = content;
			}
		});
		brecv = null;
		b.registerReceiveMessageListener(new IMessageReceiveListener() {

			@Override
			public void receivedMessage(UserId from_userid, String content) {
				brecv = content;
			}
		});
	}

	@Test
	public void testRelay() throws Exception {
		b.sendMessage(aid, MESSAGE);
		Assert.assertEquals(arecv, MESSAGE);
		Assert.assertNull(brecv);
	}

	@Test
	public void testRelay2() throws Exception {
		a.sendMessage(bid, MESSAGE);
		Assert.assertNull(arecv);
		Assert.assertEquals(brecv, MESSAGE);
	}

	@Test(expected = OtherUserOfflineException.class)
	public void testOtherOffline() throws Exception {
		a.sendMessage(new MockUserId("c"), MESSAGE);
	}
}
