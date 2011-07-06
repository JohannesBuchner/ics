package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.File;
import java.security.GeneralSecurityException;

import net.mc_cubed.icedjava.ice.IcePeer;

import org.apache.log4j.Logger;

import udt.UDTSocket;

import com.jakeapp.availablelater.AvailableLaterWrapperObject;
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

	private IIceConnect connect;

	private IUdtOverIceConnect udtconnect;

	public ServerRequestHandler(IMsgService negotiationService,
			IIceConnect connect, IUdtOverIceConnect udtconnect, UserId user,
			FileRequestFileMapper mapper, IncomingTransferListener l) {
		this.myUserId = user;
		log.debug("creating ServerRequestHandler for user " + myUserId);
		this.mapper = mapper;
		this.negotiationService = negotiationService;
		this.incomingTransferListener = l;
		this.connect = connect;
		this.udtconnect = udtconnect;
	}

	public void serve(final FileRequest fr) {
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
			log.warn("sending failed", e);
		}
		if (!success)
			return;
		if (aes == null)
			throw new IllegalStateException(
					"expected: success is true => aes exists");
		final AESObject aesfinal = aes;
		new AvailableLaterWrapperObject<Void, IcePeer>(connect.getNomination(fr
				.getPeer())) {

			@Override
			public Void calculate() throws Exception {
				log.debug("connecting ...");
				UDTSocket socket = udtconnect.connect(connect.getSocket(),
						getSourceResult(), true);
				log.debug("sending content to client ...");
				// ok, now send content
				IceUdtSendingFileTransfer ft = new IceUdtSendingFileTransfer(
						socket, aesfinal, fr);
				incomingTransferListener.started(ft);
				ft.run();
				return null;
			}
		}.start();
	}
}
