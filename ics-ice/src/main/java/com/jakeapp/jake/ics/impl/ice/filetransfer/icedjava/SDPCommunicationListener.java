package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.util.Collection;
import java.util.List;
import java.util.Vector;

import javax.sdp.Attribute;
import javax.sdp.Connection;
import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SdpParseException;
import javax.sdp.SessionDescription;

import net.mc_cubed.icedjava.ice.SDPListener;

/**
 * Adapter to provide two-way communication through a rendevouz protocol, with a
 * convinient interface.
 * 
 * @author Johannes Buchner
 */
public abstract class SDPCommunicationListener implements SDPListener {

	@Override
	public void sendSession(SessionDescription session) throws SdpException {
		throw new IllegalStateException(
				"you shouldn't call this, it is deprecated");
	}

	/**
	 * we are requested to tell the other party about a SDP message (some
	 * attributes, media may have changed).
	 * 
	 * @param conn
	 * @param iceAttributes
	 * @param iceMedias
	 * @throws SdpParseException
	 */
	protected abstract void doUpdateMedia(Connection conn,
			Collection<Attribute> iceAttributes,
			Collection<MediaDescription> iceMedias) throws SdpParseException;

	@SuppressWarnings({ "cast", "unchecked" })
	@Override
	public void updateMedia(Connection conn, Vector iceAttributes,
			Vector iceMedias) throws SdpParseException {
		doUpdateMedia(conn, (Vector<Attribute>) iceAttributes,
				(Vector<MediaDescription>) iceMedias);
	}

	@Override
	public void updateMedia(Connection conn, List<Attribute> iceAttributes,
			List<MediaDescription> iceMedias) throws SdpParseException {
		doUpdateMedia(conn, iceAttributes, iceMedias);
	}
}
