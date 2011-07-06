package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.util.Collection;
import java.util.List;
import java.util.Vector;

import javax.sdp.Attribute;
import javax.sdp.Connection;
import javax.sdp.MediaDescription;
import javax.sdp.Origin;
import javax.sdp.SdpParseException;

import net.mc_cubed.icedjava.ice.SDPListener;

/**
 * Adapter to provide two-way communication through a rendevouz protocol, with a
 * convinient interface.
 * 
 * @author Johannes Buchner
 */
public abstract class SDPCommunicationListener implements SDPListener {

	/**
	 * we are requested to tell the other party about a SDP message (some
	 * attributes, media may have changed).
	 * @param origin 
	 * 
	 * @param conn
	 * @param iceAttributes
	 * @param iceMedias
	 * @throws SdpParseException
	 */
	protected abstract void doUpdateMedia(Origin origin, Connection conn,
			Collection<Attribute> iceAttributes,
			Collection<MediaDescription> iceMedias) throws SdpParseException;

	@SuppressWarnings({ "cast", "unchecked" })
	@Override
	public void updateMedia(Origin origin, Connection conn,
			Vector iceAttributes, Vector iceMedias) throws SdpParseException {
		doUpdateMedia(origin, conn, (Vector<Attribute>) iceAttributes,
				(Vector<MediaDescription>) iceMedias);
	}

	@Override
	public void updateMedia(Origin origin, Connection conn,
			List<Attribute> iceAttributes, List<MediaDescription> iceMedias)
			throws SdpParseException {
		doUpdateMedia(origin, conn, iceAttributes, iceMedias);
	}
}
