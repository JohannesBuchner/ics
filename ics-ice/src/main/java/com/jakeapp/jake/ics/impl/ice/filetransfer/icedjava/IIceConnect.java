package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.util.List;

import javax.sdp.Attribute;
import javax.sdp.Connection;
import javax.sdp.MediaDescription;
import javax.sdp.Origin;
import javax.sdp.SdpException;

import net.mc_cubed.icedjava.ice.IcePeer;
import net.mc_cubed.icedjava.ice.IceSocket;

import com.jakeapp.availablelater.AvailableLater;
import com.jakeapp.jake.ics.UserId;

/**
 * Dispatcher for incoming sdp packets
 * 
 * @author johannes
 */
public interface IIceConnect {

	/**
	 * @param peer
	 * @return the IcePeer to the user, or null if no connection yet.
	 */
	IcePeer get(UserId peer);

	/**
	 * Forward this SDP update to the corresponding icepeer; and start a ice
	 * client if someone offers a new SDP session.
	 * 
	 * @param user
	 * @param conn
	 * @param attr
	 * @param media
	 * @throws SdpException
	 */
	void updateMedia(UserId user, Origin origin, Connection conn,
			List<Attribute> attr, List<MediaDescription> media)
			throws SdpException;

	/**
	 * Negotiate, wait for negotiation to end, and return a candidate pair.
	 * 
	 * If a candidate pair is already negotiated, return immediately.
	 * 
	 * @param peer
	 * @return the connected IcePeer
	 */
	AvailableLater<IcePeer> getNomination(UserId user);

	/**
	 * close the connection
	 * 
	 * @param user
	 */
	void close(UserId user);

	/**
	 * Returns the status of the negotiation immediately.
	 * 
	 * @param user
	 * @return true iff a candidate pair has been successfully negotiated.
	 */
	boolean hasCandidatePair(UserId user);

	/**
	 * 
	 * @return the master Ice socket
	 */
	IceSocket getSocket();

}
