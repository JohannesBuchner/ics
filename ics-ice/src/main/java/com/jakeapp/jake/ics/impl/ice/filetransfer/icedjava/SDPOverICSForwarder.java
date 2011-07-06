package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.IOException;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.msgservice.IMsgService;


public class SDPOverICSForwarder extends SDPTextCommunicationListener {

	private static final String prefix = SDPOverICSListener.prefix;

	private final UserId peer;

	private final IMsgService msg;

	public SDPOverICSForwarder(IMsgService msg, UserId otheruser) {
		this.msg = msg;
		this.peer = otheruser;
	}

	@Override
	protected void sendText(String text) throws IOException {
		this.msg.sendMessage(peer, prefix + text);
	}

}
