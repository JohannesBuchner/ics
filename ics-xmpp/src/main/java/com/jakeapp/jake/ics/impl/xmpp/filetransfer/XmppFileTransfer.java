/**
 * 
 */
package com.jakeapp.jake.ics.impl.xmpp.filetransfer;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.filetransfer.negotiate.FileRequest;
import com.jakeapp.jake.ics.filetransfer.runningtransfer.IFileTransfer;
import com.jakeapp.jake.ics.filetransfer.runningtransfer.Status;
import org.apache.log4j.Logger;
import org.jivesoftware.smackx.filetransfer.FileTransfer;
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;

import java.io.File;

/**
 * Handles ongoing incoming and outgoing transfers. You want to watch its
 * progress using a thread.
 * 
 * @author johannes
 */
/*
 * just a wrapper for smack
 */
public class XmppFileTransfer implements IFileTransfer {

	private static final Logger log = Logger.getLogger(XmppFileTransfer.class);

	private FileTransfer transfer;

	private FileRequest request;

	private File localFile;

	public XmppFileTransfer(FileTransfer ft, FileRequest fr, File localFile) {
		log.info("new watchable FileTransfer: " + fr.getFileName() + " with "
				+ fr.getPeer() + " using local file " + localFile);
		this.transfer = ft;
		this.request = fr;
		this.localFile = localFile;
	}

	@Override
	public void cancel() {
		transfer.cancel();
	}

	@Override
	public long getAmountWritten() {
		return transfer.getAmountWritten();
	}

	@Override
	public String getError() {
		if (transfer.getError() == null)
			return null;
		else
			return transfer.getError().toString();
	}

	@Override
	public String getFileName() {
		return getFileRequest().getFileName();
	}

	@Override
	public FileRequest getFileRequest() {
		return request;
	}

	@Override
	public long getFileSize() {
		return transfer.getFileSize();
	}

	@Override
	public File getLocalFile() {
		return localFile;
	}

	@Override
	public UserId getPeer() {
		return request.getPeer();
	}

	@Override
	public double getProgress() {
		return transfer.getProgress();
	}

	@Override
	public Status getStatus() {
		for (Status s : Status.values()) {
			if (s.toString().equals(transfer.getStatus().toString()))
				return s;
		}
		return null;
	}

	@Override
	public boolean isDone() {
		return transfer.isDone();
	}

	/**
	 * @return null if yet undefined (not started), true if receiving, false if
	 *         sending
	 */
	@Override
	public Boolean isReceiving() {
		if (transfer.getClass().equals(IncomingFileTransfer.class))
			return true;
		else if (transfer.getClass().equals(OutgoingFileTransfer.class))
			return false;
		else
			return null;
	}
}