package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.util.HashMap;
import java.util.Map;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.exceptions.NetworkException;
import com.jakeapp.jake.ics.exceptions.NoSuchUseridException;
import com.jakeapp.jake.ics.exceptions.OtherUserOfflineException;
import com.jakeapp.jake.ics.exceptions.TimeoutException;
import com.jakeapp.jake.ics.msgservice.IMessageReceiveListener;
import com.jakeapp.jake.ics.msgservice.IMsgService;
import com.jakeapp.jake.ics.status.ILoginStateListener;

/**
 * Provides a mock msgservice that forwards messages between instances
 * 
 * @author johannes
 */
public class MockConnectingMsgService implements IMsgService {

	private static Map<UserId, MockConnectingMsgService> instances = new HashMap<UserId, MockConnectingMsgService>();

	public static MockConnectingMsgService getInstance(UserId user) {
		return instances.get(user);
	}

	public static MockConnectingMsgService createInstance(UserId user) {
		if (!instances.containsKey(user)) {
			instances.put(user, new MockConnectingMsgService(user));
		}
		return getInstance(user);
	}

	private IMessageReceiveListener listener;

	private UserId user;

	public MockConnectingMsgService(UserId user) {
		this.user = user;
	}

	@Override
	public Boolean sendMessage(UserId to_userid, String content)
			throws NetworkException, TimeoutException, NoSuchUseridException,
			OtherUserOfflineException {
		MockConnectingMsgService msg = getInstance(to_userid);
		if (msg == null)
			throw new OtherUserOfflineException();
		if (msg.listener == null)
			throw new OtherUserOfflineException();
		msg.listener.receivedMessage(user, content);
		return true;
	}

	@Override
	public void registerReceiveMessageListener(
			IMessageReceiveListener receiveListener) {
		this.listener = receiveListener;
	}

	@Override
	public void registerLoginStateListener(ILoginStateListener loginListener) {

	}

	@Override
	public IMsgService getFriendMsgService() {
		return this;
	}

	@Override
	public void unRegisterReceiveMessageListener(
			IMessageReceiveListener receiveListener) {
		this.listener = null;
	}

	@Override
	public void unRegisterLoginStateListener(ILoginStateListener loginListener) {

	}

}
