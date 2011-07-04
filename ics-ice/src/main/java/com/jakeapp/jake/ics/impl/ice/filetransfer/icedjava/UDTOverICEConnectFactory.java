package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

import javax.sdp.SdpParseException;

import com.jakeapp.jake.ics.msgservice.IMsgService;


public class UDTOverICEConnectFactory {

	private UDTOverICEConnectFactory() {
	}

	private static final Map<IMsgService, UDTOverICEConnect> instances = new HashMap<IMsgService, UDTOverICEConnect>();

	public static UDTOverICEConnect getFor(IMsgService msg)
			throws SdpParseException, SocketException {
		UDTOverICEConnect instance = instances.get(msg);
		if (instance == null) {
			instance = new UDTOverICEConnect(msg);
			instances.put(msg, instance);
		}
		return instance;
	}
}
