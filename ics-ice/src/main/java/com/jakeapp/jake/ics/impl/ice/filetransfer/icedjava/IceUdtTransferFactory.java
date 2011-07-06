package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;


import java.net.SocketException;

import javax.sdp.SdpParseException;

import org.apache.log4j.Logger;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.filetransfer.methods.ITransferMethod;
import com.jakeapp.jake.ics.filetransfer.methods.ITransferMethodFactory;
import com.jakeapp.jake.ics.msgservice.IMsgService;
import com.jakeapp.jake.ics.users.IUsersService;

public class IceUdtTransferFactory implements ITransferMethodFactory {

	protected static final String START = "<filetransfer><![CDATA[";

	protected static final String END = "]]></filetransfer>";

	public static final int DEFAULT_MAXIMAL_REQUEST_AGE_SECONDS = 60;

	private final int maximalRequestAgeSeconds;

	private final IUdtOverIceConnect udtconnect;

	private static final Logger log = Logger
			.getLogger(IceUdtTransferFactory.class);

	public IceUdtTransferFactory(IUdtOverIceConnect udtconnect,
			int maximalRequestAgeSeconds) {
		this.udtconnect = udtconnect;
		this.maximalRequestAgeSeconds = maximalRequestAgeSeconds;
	}

	@Override
	public ITransferMethod getTransferMethod(IMsgService negotiationService,
			UserId user) {
		try {
			return new IceUdtFileTransferMethod(negotiationService, user,
					udtconnect, this.maximalRequestAgeSeconds);
		} catch (SdpParseException e) {
			log.error(e);
			return null;
		} catch (SocketException e) {
			log.error(e);
			return null;
		}
	}
}
