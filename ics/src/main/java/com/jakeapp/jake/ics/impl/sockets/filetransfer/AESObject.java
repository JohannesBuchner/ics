package com.jakeapp.jake.ics.impl.sockets.filetransfer;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * The AESObject capsules AES calls so that the key is easily transferrable as
 * String object.
 * 
 * @author johannes
 */
public class AESObject {

	private static final String CIPHERNAME = "AES";

	private static final int KEYLENGTH = 129;
	
	private SecretKey skey;

	/**
	 * Sets up an AES black box with a randomly generated key.
	 * 
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchPaddingException
	 */
	public AESObject() throws NoSuchAlgorithmException, NoSuchPaddingException {
		KeyGenerator kgen = KeyGenerator.getInstance(CIPHERNAME);
		kgen.init(128);
		skey = kgen.generateKey();
		Cipher.getInstance(CIPHERNAME);
	}

	/**
	 * Sets up an AES black box with the given key
	 * 
	 * @param key
	 * @throws NoSuchPaddingException
	 * @throws NoSuchAlgorithmException
	 */
	public AESObject(String strKey) throws NoSuchAlgorithmException,
			NoSuchPaddingException {
		byte[] keyBytes = hexToBytes(strKey);
		skey = new SecretKeySpec(keyBytes, CIPHERNAME);
		Cipher.getInstance(CIPHERNAME);
	}

	/**
	 * returns a readable String representation of the symmetric key
	 * 
	 * @return
	 */
	public String getKey() {
		byte[] keyBytes = skey.getEncoded();
		return asHex(keyBytes);
	}

	/**
	 * Decryption filter
	 * 
	 * Decrypts the incoming stream
	 * 
	 * @return the decrypted stream
	 */
	public InputStream decrypt(InputStream encin) {
		try {
			Cipher cipher = Cipher.getInstance(CIPHERNAME);
			cipher.init(Cipher.DECRYPT_MODE, skey);
			return new CipherInputStream(encin, cipher);
		} catch (GeneralSecurityException e) {
			/*
			 * this should never happened, as it should have been captured in
			 * the constructor.
			 */
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Encryption filter
	 * 
	 * Encrypts the incoming stream
	 * 
	 * @return
	 * @throws NoSuchPaddingException
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 */
	public OutputStream encrypt(OutputStream encout) {
		try {
			Cipher cipher = Cipher.getInstance(CIPHERNAME);
			cipher.init(Cipher.ENCRYPT_MODE, skey);
			return new CipherOutputStream(encout, cipher);
		} catch (GeneralSecurityException e) {
			/*
			 * this should never happened, as it should have been captured in
			 * the constructor.
			 */
			throw new IllegalStateException(e);
		}
	}


	@SuppressWarnings("cast")
	public static String asHex(byte buf[]) {
		StringBuffer strbuf = new StringBuffer(buf.length * 2);
		int i;

		for (i = 0; i < buf.length; i++) {
			if (((int) buf[i] & 0xff) < 0x10)
				strbuf.append("0");

			strbuf.append(Long.toString((int) buf[i] & 0xff, 16));
		}

		return strbuf.toString();
	}

	public static byte[] hexToBytes(char[] hex) {
		int length = hex.length / 2;
		byte[] raw = new byte[length];
		for (int i = 0; i < length; i++) {
			int high = Character.digit(hex[i * 2], 16);
			int low = Character.digit(hex[i * 2 + 1], 16);
			int value = (high << 4) | low;
			if (value > 127)
				value -= 256;
			raw[i] = (byte) value;
		}
		return raw;
	}

	public static byte[] hexToBytes(String hex) {
		return hexToBytes(hex.toCharArray());
	}

	/**
	 * length of the keys string representation
	 * 
	 * @return
	 */
	public static int getKeylength() {
		return KEYLENGTH / 8 * 2;
	}


}
