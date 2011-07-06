package com.jakeapp.jake.ics.filetransfer;

import java.util.Iterator;

import org.apache.log4j.Logger;

import com.jakeapp.jake.ics.filetransfer.methods.ITransferMethod;
import com.jakeapp.jake.ics.filetransfer.negotiate.FileRequest;
import com.jakeapp.jake.ics.filetransfer.negotiate.INegotiationSuccessListener;
import com.jakeapp.jake.ics.filetransfer.runningtransfer.IFileTransfer;

public class FailoverRequest implements INegotiationSuccessListener {

	private Logger log = Logger.getLogger(FailoverRequest.class);

	private FileRequest request;

	private INegotiationSuccessListener parentListener;

	private Iterator<ITransferMethod> methodIterator;

	public FailoverRequest(FileRequest request,
			INegotiationSuccessListener nsl, Iterable<ITransferMethod> methods) {
		this.request = request;
		this.parentListener = nsl;
		this.methodIterator = methods.iterator();
	}

	public void request() {
		nextMethod().request(request, parentListener);
	}

	private ITransferMethod nextMethod() {
		while (methodIterator.hasNext()) {
			ITransferMethod m = methodIterator.next();
			if (m == null)
				return m;
		}
		return null;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public void failed(Exception reason) {
		// if
		// (reason.getClass().equals(CommunicationProblemException.class)) {
		ITransferMethod method = nextMethod();
		if (method != null) {
			log.info("failing over to method " + method);
			method.request(this.request, this);
			return;
		}
		log.info("no methods left, failure");
		try {
			this.parentListener.failed(reason);
		} catch (Exception ignored) {
		}
	}

	@Override
	public void succeeded(IFileTransfer ft) {
		try {
			this.parentListener.succeeded(ft);
		} catch (Exception ignored) {
		}
	}

}