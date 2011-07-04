package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;

import udt.UDTSocket;

import com.jakeapp.jake.ics.filetransfer.negotiate.FileRequest;
import com.jakeapp.jake.ics.filetransfer.runningtransfer.IFileTransfer;
import com.jakeapp.jake.ics.filetransfer.runningtransfer.Status;
import com.jakeapp.jake.ics.impl.sockets.filetransfer.AESObject;
import com.jakeapp.jake.ics.impl.sockets.filetransfer.FileTransfer;

public class IceUdtFileTransfer extends FileTransfer implements Runnable {

	private static Logger log = Logger.getLogger(IceUdtFileTransfer.class);

	public static final String FILE_REQUEST = "file?";

	private static final int BLOCKSIZE = 1024 * 1024;

	private IFileTransfer ft;

	private UDTSocket s;

	private AESObject aes;

	public IceUdtFileTransfer(FileRequest r, UDTSocket other, AESObject aes)
			throws IOException {
		this.request = r;
		this.s = other;
		this.aes = aes;
		this.localFile = File.createTempFile("socket", "output");
		this.status = Status.negotiated;
	}

	public IFileTransfer getFileTransfer() {
		return this.ft;
	}

	public void run() {
		try {
			log.info("starting transfer from " + this.s.getEndpoint());
			byte[] b = new byte[BLOCKSIZE];

			if (this.status == Status.negotiated)
				this.status = Status.in_progress;

			BufferedOutputStream fileOut = new BufferedOutputStream(
					new FileOutputStream(this.localFile));

			InputStream in = aes.decrypt(this.s.getInputStream());

			while (this.status == Status.in_progress) {
				log.debug("receiving content ... ");
				int len = in.read(b);
				log.debug("received " + len + " bytes ... ");
				if (len == -1 || len == 0 && !this.s.isActive())
					break;
				if (len > 0) {
					this.amountWritten += len;
					fileOut.write(b, 0, len);
				}
			}
			fileOut.flush();

			/**
			 * before with Channels, didn't work
			 * 
			 * <pre>
			 * this.s = SocketChannel.open(this.other);
			 * this.status = Status.in_progress;
			 * log.debug(&quot;sending request&quot;);
			 * this.s.write(ByteBuffer.wrap(this.transferKey.toString().getBytes()));
			 * this.s.socket().getOutputStream().flush();
			 * 
			 * FileChannel out = new FileOutputStream(this.localFile).getChannel();
			 * 
			 * log.debug(&quot;retrieving data ...&quot;);
			 * while (!this.s.socket().isClosed() &amp;&amp; out.isOpen()
			 * 		&amp;&amp; this.status != Status.cancelled) {
			 * 	this.amountWritten += out.transferFrom(this.s, this.amountWritten,
			 * 			BLOCKSIZE);
			 * 	log.debug(&quot;already received &quot; + this.amountWritten + &quot; bytes.&quot;);
			 * </pre>
			 **/
			log.debug("retrieving data done: " + amountWritten
					+ " bytes written");
		} catch (IOException e) {
			log.error("transfer failed", e);
			setError(e.getMessage());
		} catch (Exception e) {
			log.error("transfer failed for unexpected reason:", e);
			setError(e.getMessage());
		}
		try {
			this.s.close();
		} catch (IOException e) {
			log.warn("Closing socket failed.", e);
		}
		if (this.status != Status.cancelled) {
			log.debug("setting myself complete");
			this.status = Status.complete;
		}
	}

	@Override
	public void cancel() {
		super.cancel();
		try {
			this.s.close();
		} catch (IOException e) {
			log.error("cancel failed", e);
			setError(e.getMessage());
		}
	}

	@Override
	public long getAmountWritten() {
		return this.amountWritten;
	}

	@Override
	public Boolean isReceiving() {
		return true;
	}
}