package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.util.ArrayList;
import java.util.List;

import javax.sdp.Attribute;
import javax.sdp.Connection;
import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SdpFactory;
import javax.sdp.SessionDescription;

import org.apache.log4j.Logger;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.msgservice.IMessageReceiveListener;

/**
 * Forwards messages to the iceconnect
 * 
 * @author johannes
 */
public class SDPOverICSListener implements IMessageReceiveListener {

	private static final Logger log = Logger
			.getLogger(SDPOverICSListener.class);

	public static final String prefix = "<sdplike/>";

	private IIceConnect iceconnect;

	public SDPOverICSListener(IIceConnect iceconnect) {
		this.iceconnect = iceconnect;
	}

	@Override
	public void receivedMessage(UserId user, String text) {
		if (text.startsWith(prefix)) {
			try {
				String content = text.substring(prefix.length());
				log.debug("received SDP from " + user + ": " + content);
				SessionDescription session = SdpFactory.getInstance()
						.createSessionDescription(content);
				Connection conn = session.getConnection();
				List<Attribute> attr = new ArrayList<Attribute>(
						session.getAttributes(true));
				List<MediaDescription> media = new ArrayList<MediaDescription>(
						session.getMediaDescriptions(true));
				iceconnect.updateMedia(user, conn, attr, media);
			} catch (SdpException e) {
				log.warn("sdp couldn't handle the message we handed it: '"
						+ text + "'", e);
			}
		}
	}


}
