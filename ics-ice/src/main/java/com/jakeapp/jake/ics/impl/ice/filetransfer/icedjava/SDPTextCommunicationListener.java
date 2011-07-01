package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.IOException;
import java.util.Collection;
import java.util.Vector;

import javax.sdp.Attribute;
import javax.sdp.Connection;
import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SdpFactory;
import javax.sdp.SdpParseException;
import javax.sdp.SessionDescription;

import org.apache.log4j.Logger;

import net.mc_cubed.icedjava.ice.SDPListener;

/**
 * Adapter to provide two-way communication through a text-based rendevouz
 * protocol, with a convenient interface.
 * 
 * @author Johannes Buchner
 */
public abstract class SDPTextCommunicationListener extends
		SDPCommunicationListener {

	private static final Logger log = Logger
			.getLogger(SDPTextCommunicationListener.class);

	private SdpFactory sdpFactory = SdpFactory.getInstance();

	public SDPTextCommunicationListener(SDPListener receiver) {
		super(receiver);
	}

	@SuppressWarnings("unchecked")
	protected void onReceiveText(String text) throws SdpParseException {
		SessionDescription session = SdpFactory.getInstance()
				.createSessionDescription(text);
		try {
			onReceive(session.getConnection(), session.getAttributes(true),
					session.getMediaDescriptions(true));
		} catch (SdpException e) {
			log.error(e);
			throw new SdpParseException(0, 0,
					"SdpException when unmarshalling string to sdp", e);
		}

	}


	@SuppressWarnings("unchecked")
	@Override
	protected void doUpdateMedia(Connection conn,
			Collection<Attribute> iceAttributes,
			Collection<MediaDescription> iceMedias) throws SdpParseException {
		String text;
		try {
			SessionDescription session = sdpFactory.createSessionDescription();
			session.setConnection(conn);
			session.getAttributes(true).addAll(iceAttributes);
			session.setMediaDescriptions(new Vector<MediaDescription>(iceMedias));
			text = session.toString();
		} catch (SdpException e) {
			log.error(e);
			throw new SdpParseException(0, 0,
					"SdpException when marshalling sdp to string", e);
		}
		try {
			sendText(text);
		} catch (IOException e) {
			log.error(e);
			throw new SdpParseException(0, 0,
					"IOException when marshalling sdp to string", e);
		}
	}

	protected abstract void sendText(String text) throws IOException;
}
