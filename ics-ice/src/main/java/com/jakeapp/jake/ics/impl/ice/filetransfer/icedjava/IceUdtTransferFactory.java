package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;


import org.apache.log4j.Logger;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.filetransfer.methods.ITransferMethod;
import com.jakeapp.jake.ics.filetransfer.methods.ITransferMethodFactory;
import com.jakeapp.jake.ics.msgservice.IMsgService;
import com.jakeapp.jake.ics.users.IUsersService;

public class IceUdtTransferFactory implements ITransferMethodFactory {

	protected static final String START = "<filetransfer><![CDATA[";

	protected static final String END = "]]></filetransfer>";

	public static final int DEFAULT_PORT = 0;

	public static final int DEFAULT_MAXIMAL_REQUEST_AGE_SECONDS = 60;

	private int maximalRequestAgeSeconds = DEFAULT_MAXIMAL_REQUEST_AGE_SECONDS;

	static Logger log = Logger.getLogger(IceUdtTransferFactory.class);

	public IceUdtTransferFactory() {
		//
	}

	public IceUdtTransferFactory(int maximalRequestAgeSeconds) {
		this.maximalRequestAgeSeconds = maximalRequestAgeSeconds;
	}

	@Override
	public ITransferMethod getTransferMethod(IMsgService negotiationService,
			UserId user) {
		return new IceUdtFileTransferMethod(this.maximalRequestAgeSeconds,
				negotiationService, user);
	}
}
