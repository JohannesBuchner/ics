package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;


import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.sdp.SdpParseException;

import org.apache.log4j.Logger;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.exceptions.NotLoggedInException;
import com.jakeapp.jake.ics.filetransfer.FileRequestFileMapper;
import com.jakeapp.jake.ics.filetransfer.IncomingTransferListener;
import com.jakeapp.jake.ics.filetransfer.methods.ITransferMethod;
import com.jakeapp.jake.ics.filetransfer.negotiate.FileRequest;
import com.jakeapp.jake.ics.filetransfer.negotiate.INegotiationSuccessListener;
import com.jakeapp.jake.ics.impl.sockets.filetransfer.AESObject;
import com.jakeapp.jake.ics.msgservice.IMessageReceiveListener;
import com.jakeapp.jake.ics.msgservice.IMsgService;

public class IceUdtFileTransferMethod implements ITransferMethod,
		IMessageReceiveListener {

	private static final Logger log = Logger
			.getLogger(IceUdtFileTransferMethod.class);

	static final String ADDRESS_REQUEST = "<addressrequest/>";

	static final String ADDRESS_RESPONSE = "<addressresponse/>";

	static final String GOT_REQUESTED_FILE = "<file/>";

	private IMsgService negotiationService;

	private UserId myUserId;

	@Inject
	private final int maximalRequestAgeSeconds;

	private Map<UserId, ClientRequestHandler> clientRequestHandlers = new HashMap<UserId, ClientRequestHandler>();

	private ServerRequestHandler serverRequestHandler;

	private final IceConnect iceconnect;

	private final IUdtOverIceConnect udtconnect;

	public IceUdtFileTransferMethod(IMsgService negotiationService,
			UserId user, IUdtOverIceConnect udtconnect,
			int maximalRequestAgeSeconds) throws SdpParseException,
			SocketException {
		log.debug("creating IceUdtFileTransferMethod for user " + user);
		this.myUserId = user;
		this.maximalRequestAgeSeconds = maximalRequestAgeSeconds;
		this.negotiationService = negotiationService;
		this.negotiationService.registerReceiveMessageListener(this);

		this.iceconnect = new IceConnect(negotiationService);
		this.udtconnect = udtconnect;
	}

	public ClientRequestHandler getClientConnect(UserId user) {
		if (!clientRequestHandlers.containsKey(user)) {
			ClientRequestHandler h = new ClientRequestHandler(
					negotiationService, iceconnect, udtconnect,
					maximalRequestAgeSeconds, user, myUserId);
			clientRequestHandlers.put(user, h);
			new Thread(h).start();
		}
		return clientRequestHandlers.get(user);
	}

	/*
	 * first step, client requests something
	 */
	@Override
	public void request(FileRequest r, INegotiationSuccessListener nsl) {
		getClientConnect(r.getPeer()).addRequest(r, nsl);
	}

	@Override
	public void receivedMessage(final UserId from_userid, String content) {
		if (!content.startsWith(IceUdtTransferFactory.START)
				|| !content.endsWith(IceUdtTransferFactory.END))
			return;

		String inner = content.substring(IceUdtTransferFactory.START.length(),
				content.length() - IceUdtTransferFactory.END.length());

		log.debug(myUserId + ": receivedMessage : " + from_userid + " : "
				+ inner);

		if (inner.startsWith(ADDRESS_REQUEST)) {
			handleFileRequest(from_userid, inner);
			/*
			 * ADDRESS_RESPONSE:
			 * [GOT_REQUESTED_FILE<uuid><filename>]ADDRESS_RESPONSE[<address>]
			 */
		} else if (inner.equals(ADDRESS_RESPONSE)) {
			/*
			 * third step, client receives no ok from server
			 */
			// we are the client, server doesn't have it
			getClientConnect(from_userid).receivedNack();
		} else if (inner.startsWith(GOT_REQUESTED_FILE)) {
			/*
			 * third step, client receives ok from server
			 */
			handleServerOk(from_userid, inner);
		} else if (inner.startsWith(FILE_RESPONSE_DONT_HAVE)) {
			log.info("Got response: User " + from_userid
					+ " doesn't have file.");
		} else {
			log.warn("unknown request from " + from_userid + ": " + content);
		}
	}


	private void handleServerOk(UserId from_userid, String inner) {
		// we are the client
		// GOT_REQUESTED_FILE<decryption key>ADDRESS_RESPONSE<filename>

		try {
			String innerWithoutType = inner.substring(GOT_REQUESTED_FILE
					.length());
			int strkeylen = AESObject.getKeylength();
			AESObject aes = new AESObject(innerWithoutType.substring(strkeylen));
			log.debug(myUserId + ": I got the decryptionKey " + aes.getKey());

			String innerWithoutTransferKey = innerWithoutType
					.substring(strkeylen);

			int pos = innerWithoutTransferKey.indexOf(ADDRESS_RESPONSE);

			String filename = innerWithoutTransferKey.substring(0, pos);

			FileRequest fr = new FileRequest(filename, false, from_userid);
			getClientConnect(from_userid).receivedAck(fr, aes);
		} catch (IndexOutOfBoundsException e) {
			log.warn(from_userid
					+ " packet came not as [GOT_REQUESTED_FILE<uuid><filename>]"
					+ "ADDRESS_RESPONSE[<address>]");
			return;
		} catch (IllegalArgumentException e) {
			log.warn(from_userid + " violated the protocol");
			return;
		} catch (GeneralSecurityException e) {
			log.warn(from_userid + " unexpected encryption/decryption problem",
					e);
			return;
		}
		log.debug("done with " + ADDRESS_RESPONSE + " from " + from_userid);
	}

	private void handleFileRequest(UserId from_userid, String inner) {
		/*
		 * second step, server receives request
		 */
		// we are the server
		String filename = inner.substring(ADDRESS_REQUEST.length());
		FileRequest fr = new FileRequest(filename, true, from_userid);
		log.debug("received a request " + fr);

		if (!isServing()) {
			log.debug("currently not serving");
		} else {
			log.debug("serving " + fr);
			serverRequestHandler.serve(fr);
		}
	}


	public boolean isServing() {
		return this.serverRequestHandler != null;
	}

	@Override
	public void startServing(IncomingTransferListener l,
			FileRequestFileMapper mapper) throws NotLoggedInException {
		log.debug(this.myUserId + ": starting to serve");

		this.serverRequestHandler = new ServerRequestHandler(
				negotiationService, iceconnect, udtconnect, this.myUserId,
				mapper, l);
	}

	@Override
	public void stopServing() {
		log.debug(this.myUserId + ": stopping to serve");
		this.serverRequestHandler = null;
	}

}