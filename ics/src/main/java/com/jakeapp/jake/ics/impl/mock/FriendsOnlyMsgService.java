/**
 * 
 */
package com.jakeapp.jake.ics.impl.mock;

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
import com.jakeapp.jake.ics.users.IUsersService;

/**
 * A subset of the message service that only allows communication with friends
 * (members of the group).
 */
public class FriendsOnlyMsgService implements IMsgService {

	private IUsersService users;

	private IMsgService msg;

	private Map<IMessageReceiveListener, IMessageReceiveListener> listenerMap = new HashMap<IMessageReceiveListener, IMessageReceiveListener>();

	public FriendsOnlyMsgService(IUsersService users, IMsgService msg) {
		this.users = users;
		this.msg = msg;
	}

	@Override
	public IMsgService getFriendMsgService() {
		return this;
	}

	@Override
	public void registerReceiveMessageListener(
			final IMessageReceiveListener receiveListener) {
		listenerMap.put(receiveListener, new FriendsOnlyReceiveFilter(
				receiveListener, this.users));
		msg.registerReceiveMessageListener(listenerMap.get(receiveListener));
	}

	@Override
	public void registerLoginStateListener(ILoginStateListener loginListener) {
		msg.registerLoginStateListener(loginListener);
	}

	@Override
	public Boolean sendMessage(UserId to_userid, String content)
			throws NetworkException, TimeoutException, NoSuchUseridException,
			OtherUserOfflineException {
		return users.isFriend(to_userid) && msg.sendMessage(to_userid, content);
	}

	@Override
	public void unRegisterReceiveMessageListener(
			IMessageReceiveListener receiveListener) {
		msg.unRegisterReceiveMessageListener(listenerMap.get(receiveListener));
	}

	@Override
	public void unRegisterLoginStateListener(ILoginStateListener loginListener) {
		msg.unRegisterLoginStateListener(loginListener);
	}
}