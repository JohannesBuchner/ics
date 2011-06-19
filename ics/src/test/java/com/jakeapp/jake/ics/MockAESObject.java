package com.jakeapp.jake.ics;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;

import javax.crypto.NoSuchPaddingException;

import com.jakeapp.jake.ics.impl.sockets.filetransfer.AESObject;

/**
 * does no encryption (in/out without modification)
 * 
 * @author johannes
 */
public class MockAESObject extends AESObject {

	public MockAESObject() throws NoSuchAlgorithmException,
			NoSuchPaddingException {
	}

	public MockAESObject(String key) throws NoSuchAlgorithmException,
			NoSuchPaddingException {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.jakeapp.jake.ics.impl.sockets.filetransfer.AESObject#decrypt(java
	 * .io.InputStream)
	 */
	@Override
	public InputStream decrypt(InputStream encin) {
		return encin;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.jakeapp.jake.ics.impl.sockets.filetransfer.AESObject#encrypt(java
	 * .io.OutputStream)
	 */
	@Override
	public OutputStream encrypt(OutputStream encout) {
		return encout;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.jakeapp.jake.ics.impl.sockets.filetransfer.AESObject#getKey()
	 */
	@Override
	public String getKey() {
		return "12345678901234567890123456789012";
	}

}
