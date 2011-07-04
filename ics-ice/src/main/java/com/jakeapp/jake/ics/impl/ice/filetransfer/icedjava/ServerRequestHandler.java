package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import org.apache.log4j.Logger;

import udt.UDTSocket;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.filetransfer.FileRequestFileMapper;
import com.jakeapp.jake.ics.filetransfer.IncomingTransferListener;
import com.jakeapp.jake.ics.filetransfer.methods.ITransferMethod;
import com.jakeapp.jake.ics.filetransfer.negotiate.FileRequest;
import com.jakeapp.jake.ics.impl.sockets.filetransfer.AESObject;
import com.jakeapp.jake.ics.msgservice.IMsgService;


public class ServerRequestHandler {

	private static final Logger log = Logger
			.getLogger(ServerRequestHandler.class);

	private IMsgService negotiationService;

	public boolean running = true;

	private UserId myUserId;

	private FileRequestFileMapper mapper;

	private IncomingTransferListener incomingTransferListener;

	public ServerRequestHandler(IMsgService negotiationService, UserId user,
			FileRequestFileMapper mapper, IncomingTransferListener l) {
		this.myUserId = user;
		log.debug("creating ServerRequestHandler for user " + myUserId);
		this.mapper = mapper;
		this.negotiationService = negotiationService;
		this.incomingTransferListener = l;
	}

	public void serve(FileRequest fr) throws IOException {
		boolean success;
		String response = IceUdtTransferFactory.START;
		AESObject aes = null;
		log.debug("Do we accept?");
		if (!incomingTransferListener.accept(fr)) {
			success = false;
		} else {
			log.debug("Do we have the file?");

			File localFile = mapper.getFileForRequest(fr);

			try {
				aes = new AESObject();

				if (localFile != null) {
					response += IceUdtFileTransferMethod.GOT_REQUESTED_FILE;
					response += aes.getKey();
					response += fr.getFileName();
					success = true;
				} else {
					success = false;
				}
			} catch (GeneralSecurityException e) {
				log.error("We don't support encryption! (why?)", e);
				success = false;
			}
		}
		if (!success) {
			response += ITransferMethod.FILE_RESPONSE_DONT_HAVE;
			log.debug("Not answering with a positive response");
		}
		response += IceUdtTransferFactory.END;

		try {
			this.negotiationService.sendMessage(fr.getPeer(), response);
		} catch (Exception e) {
			IceUdtTransferFactory.log.warn("sending failed", e);
		}
		if (!success)
			return;
		if (aes == null)
			throw new IllegalStateException(
					"expected: success is true => aes exists");

		// ok, now send content

		UDTSocket socket = getSocketCached(fr.getPeer(), true);
		IceUdtSendingFileTransfer ft = new IceUdtSendingFileTransfer(socket,
				aes, fr);
		new Thread(ft).start();
		incomingTransferListener.started(ft);
	}

	private UDTSocket getSocketCached(UserId peer, boolean controlling)
			throws IOException {
		UDTSocket serverAdress;
		try {
			serverAdress = UDTOverICEConnectFactory.getFor(negotiationService)
					.initiate(peer, controlling);
			if (!serverAdress.isActive())
				throw new IOException("Socket is not active (any more)");
		} catch (Exception e) {
			log.warn(e);
			try {
				serverAdress = UDTOverICEConnectFactory.getFor(
						negotiationService).initiate(peer, controlling);
			} catch (Exception e1) {
				log.error(e);
				throw new IOException("couldn't establish ICE connection with "
						+ peer, e1);
			}
		}
		return serverAdress;
	}

}
