package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import org.apache.log4j.Logger;

import udt.UDTSocket;

import com.jakeapp.jake.ics.filetransfer.negotiate.FileRequest;
import com.jakeapp.jake.ics.filetransfer.runningtransfer.Status;
import com.jakeapp.jake.ics.impl.sockets.filetransfer.AESObject;
import com.jakeapp.jake.ics.impl.sockets.filetransfer.FileTransfer;

public class IceUdtSendingFileTransfer extends FileTransfer implements Runnable {

	private static final int BLOCKSIZE = 1024;

	private UDTSocket client;

	private final Logger log = Logger
			.getLogger(IceUdtSendingFileTransfer.class);

	private AESObject aes;

	public IceUdtSendingFileTransfer(UDTSocket client, AESObject aes,
			FileRequest req) {
		this.log.debug("ClientHandler for " + client + " created");
		this.client = client;
		this.request = req;
		this.aes = aes;
	}

	public void run() {
		InputStreamReader bis = null;
		try {
			bis = new InputStreamReader(this.client.getInputStream());

			sendContent();
		} catch (IOException e) {
			log.warn("serving client failed", e);
		} finally {
			try {
				log.debug("closing client connection");
				if (bis != null)
					bis.close();
				this.client.close();
			} catch (IOException e) {
				log.error("closing failed", e);
			}
		}
	}

	public void sendContent() {
		this.status = Status.negotiated;
		this.localFile = request.getData().getDataFile();

		log.debug("starting content");
		InputStream source;
		try {
			source = new FileInputStream(this.localFile);
			OutputStream out = aes.encrypt(this.client.getOutputStream());
			this.status = Status.in_progress;
			byte[] b = new byte[BLOCKSIZE];
			while (this.status == Status.in_progress) {
				log.debug("sending content ... ");
				int len = source.read(b);
				if (len == -1)
					break;
				this.amountWritten += len;
				out.write(b, 0, len);
			}
			out.flush();
			out.close();
			log.debug("sending content done:" + amountWritten
					+ " bytes written");
		} catch (FileNotFoundException e) {
			setError(e);
		} catch (IOException e) {
			setError(e);
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
			this.client.close();
		} catch (IOException e) {
			log.error(e);
		}
	}

	@Override
	public Boolean isReceiving() {
		return false;
	}
}
