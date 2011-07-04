package com.jakeapp.jake.ics.impl.ice.filetransfer.icedjava;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import udt.UDTSocket;

import com.jakeapp.jake.ics.UserId;
import com.jakeapp.jake.ics.exceptions.TimeoutException;
import com.jakeapp.jake.ics.filetransfer.exceptions.OtherUserDoesntHaveRequestedContentException;
import com.jakeapp.jake.ics.filetransfer.negotiate.FileRequest;
import com.jakeapp.jake.ics.filetransfer.negotiate.INegotiationSuccessListener;
import com.jakeapp.jake.ics.impl.sockets.filetransfer.AESObject;
import com.jakeapp.jake.ics.msgservice.IMsgService;


public class ClientRequestHandler implements Runnable {

	private static final Logger log = Logger
			.getLogger(ClientRequestHandler.class);

	private static final boolean PROACTIVE = false;

	private BlockingQueue<FileRequest> requests = new LinkedBlockingQueue<FileRequest>();

	private IMsgService negotiationService;

	public boolean running = true;

	private UserId myUserId;

	private UserId peerUserId;

	private Long socketLastActive = null;

	private Map<FileRequest, INegotiationSuccessListener> listeners = new HashMap<FileRequest, INegotiationSuccessListener>();

	private int maximalRequestAgeSeconds;

	private FileRequest currentRequest;

	private UDTSocket socket;

	public ClientRequestHandler(int maximalRequestAgeSeconds,
			IMsgService negotiationService, UserId user, UserId peer) {
		log.debug("creating ClientRequestHandler for user " + user);
		this.maximalRequestAgeSeconds = maximalRequestAgeSeconds;
		this.myUserId = user;
		this.peerUserId = peer;
		this.negotiationService = negotiationService;

		startTimeoutTimer();
	}

	public void stop() {
		this.running = false;
		Thread.currentThread().interrupt();
	}

	@Override
	public void run() {
		if (PROACTIVE) {
			try {
				socket = getSocketCached(peerUserId, false);
			} catch (IOException e) {
				log.warn("proactively acquiring socket failed", e);
			}
		}
		while (running) {
			try {
				this.currentRequest = requests.poll(1, TimeUnit.SECONDS);
				try {
					socket = getSocketCached(peerUserId, false);
				} catch (IOException e) {
					log.warn("reactively acquiring socket failed", e);
				}
				if (this.currentRequest != null) {
					handleRequest();
				}
			} catch (InterruptedException e) {
				// might have been stopped, otherwise loop will continue
			}
		}
	}


	public void addRequest(FileRequest r, INegotiationSuccessListener nsl) {
		if (!r.getPeer().equals(peerUserId)) {
			throw new IllegalArgumentException("This handler is for "
					+ peerUserId + ", not for " + r.getPeer());
		}
		log.debug("queueing " + r);
		this.listeners.put(r, nsl);
		// this.socketLastActive.put(r, new Date().getTime());
		// this should be last as it triggers the further processing
		this.requests.offer(r);
	}

	private void handleRequest() {
		log.debug(myUserId + ": We request " + this.currentRequest);
		String request = IceUdtTransferFactory.START
				+ IceUdtFileTransferMethod.ADDRESS_REQUEST
				+ this.currentRequest.getFileName() + IceUdtTransferFactory.END;

		try {
			// sending the message
			this.negotiationService.sendMessage(peerUserId, request);
			// we should receive a ACK & encryption key in the message listener
		} catch (Exception e) {
			log.info("request failed", e);
			try {
				getCurrentListener().failed(e);
			} catch (Exception ignored) {
				log.info("Listener died", ignored);
			}
			removeOutgoing(this.currentRequest);
		}
	}

	private UDTSocket getSocketCached(UserId peer, boolean controlling)
			throws IOException {
		UDTSocket serverAdress;
		try {
			serverAdress = UDTOverICEConnectFactory.getFor(negotiationService)
					.initiate(peer, controlling);
			if (!serverAdress.isActive())
				throw new IOException("Socket is not active (any more)");
		} catch (Exception e) {
			log.warn(e);
			try {
				serverAdress = UDTOverICEConnectFactory.getFor(
						negotiationService).initiate(peer, controlling);
			} catch (Exception e1) {
				log.error(e);
				throw new IOException("couldn't establish ICE connection with "
						+ peer, e1);
			}
		}
		return serverAdress;
	}

	private void removeOutgoing(FileRequest r) {
		log.debug("I'm done with outgoing request " + r
				+ " (one way or the other)");
		synchronized (this.socketLastActive) {
			this.listeners.remove(r);
			this.socketLastActive = null;
		}
	}

	/**
	 * A cleanup thread for requests that get stuck (sockets time out, NAT
	 * forward stops, peer goes away, etc.
	 */
	private void startTimeoutTimer() {
		final Timer timer = new Timer(true);
		timer.schedule(new TimerTask() {

			private final Logger log = Logger
					.getLogger(ClientRequestHandler.class);

			@Override
			public void run() {
				long now = new Date().getTime();
				if (!ClientRequestHandler.this.running)
					timer.cancel();

				if (ClientRequestHandler.this.socketLastActive == null) {
					// not started yet
					return;
				}
				long age = (now - ClientRequestHandler.this.socketLastActive) / 1000;
				log.debug("checking timeout with "
						+ ClientRequestHandler.this.peerUserId + " age: " + age);
				if (age > ClientRequestHandler.this.maximalRequestAgeSeconds) {
					log.debug("removing old request");
					synchronized (ClientRequestHandler.this.socketLastActive) {
						try {
							if (ClientRequestHandler.this.socket != null)
								ClientRequestHandler.this.socket.close();
						} catch (IOException e) {
							log.warn("closing socket", e);
						}
						ClientRequestHandler.this.socketLastActive = null;
						try {
							ClientRequestHandler.this.listeners.remove(
									currentRequest).failed(
									new TimeoutException());
						} catch (Exception ignored) {
							log.warn("Ignoring Exception", ignored);
						}
					}
				}
			}
		}, 0, this.maximalRequestAgeSeconds * 1000 / 2);
	}

	public void receivedAck(FileRequest fr, AESObject aes) {
		// so the other user has the file and will send it to us through the
		// socket (or is already transmitting).
		if (!this.currentRequest.equals(fr)) {
			log.warn("received a ack, but not for the right file request");
			return;
		}
		if (socket == null) {
			log.warn("received a Ack, but we don't have a socket");
			getCurrentListener()
					.failed(new IOException(
							"No socket connection available. Try again later."));
			return;
		}
		try {
			log.info("received server ACK, starting file transfer");
			new Thread(new IceUdtFileTransfer(fr, socket, aes)).start();
		} catch (IOException e) {
			getCurrentListener().failed(e);
		}
	}

	private INegotiationSuccessListener getCurrentListener() {
		return listeners.get(currentRequest);
	}

	public void receivedNack() {
		getCurrentListener().failed(
				new OtherUserDoesntHaveRequestedContentException());
		removeOutgoing(currentRequest);
	}

}
