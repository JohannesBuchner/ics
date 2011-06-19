package com.jakeapp.jake.ics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringBufferInputStream;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.jakeapp.jake.ics.impl.sockets.filetransfer.AESObject;


public class TestAESObject {

	private static Logger log = Logger.getLogger(TestAESObject.class);

	private AESObject aes;

	private String msg = new String("foo bar my secret message is so secret");

	@Before
	public void setup() throws Exception {
		aes = new AESObject();
	}


	@Test
	public void testEncDec() throws Exception {
		String key = aes.getKey();
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		OutputStream encstream = aes.encrypt(os);
		encstream.write(msg.getBytes());
		encstream.close();

		log.debug("key: " + key);

		String recvmsg = otherSide(key,
				new ByteArrayInputStream(os.toByteArray()));
		Assert.assertEquals("encryption left msg unchanged", msg, recvmsg);

	}


	/* the other side */
	private String otherSide(String key, InputStream encstream)
			throws Exception {
		AESObject aes2 = new AESObject(key);
		InputStream is = aes2.decrypt(encstream);

		InputStreamReader isr = new InputStreamReader(is);
		char[] content = new char[1000];
		int len = 0;
		while (true) {
			int r = isr.read(content, len, 1000 - len);
			if (r >= 0)
				len += r;
			else
				break;
		}
		String recvmsg = new String(content, 0, len);
		return recvmsg;
	}

}
