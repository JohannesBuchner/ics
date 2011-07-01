package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.IOException;

import javax.sdp.SdpParseException;

import net.mc_cubed.icedjava.ice.SDPListener;

import org.apache.log4j.Logger;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.msgservice.IMessageReceiveListener;
import com.jakeapp.jake.ics.msgservice.IMsgService;


public class SDPOverICS extends SDPTextCommunicationListener implements
		IMessageReceiveListener {

	private static final Logger log = Logger.getLogger(SDPOverICS.class);

	private static final String prefix = "<sdplike/>";

	private UserId peer;

	private IMsgService msg;

	public SDPOverICS(SDPListener receiver, IMsgService msg, UserId otheruser) {
		super(receiver);
		this.msg = msg;
		this.msg.registerReceiveMessageListener(this);
		this.peer = otheruser;
	}

	@Override
	protected void sendText(String text) throws IOException {
		try {
			this.msg.sendMessage(peer, prefix + text);
		} catch (Exception e) {
			throw new IOException("sending sdp message failed", e);
		}
	}

	@Override
	public void receivedMessage(UserId user, String text) {
		if (user.equals(peer) && text.startsWith(prefix)) {
			try {
				String content = text.substring(prefix.length());
				onReceiveText(content);
			} catch (SdpParseException e) {
				log.warn("sdp couldn't handle the message we handed it: '"
						+ text + "'", e);
			}
		}
	}

}
