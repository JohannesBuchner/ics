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

import net.mc_cubed.icedjava.ice.IcePeer;

import org.apache.log4j.Logger;

import udt.UDTSocket;

import com.jakeapp.availablelater.AvailableLater;
import com.jakeapp.availablelater.AvailableLaterWaiter;
import com.jakeapp.availablelater.AvailableLaterWrapperObject;
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

	private IIceConnect iceconnect;

	private IUdtOverIceConnect udtconnect;

	public ClientRequestHandler(IMsgService negotiationService,
			IIceConnect iceconnect, IUdtOverIceConnect udtconnect,
			int maximalRequestAgeSeconds, UserId user, UserId peer) {
		log.debug("creating ClientRequestHandler for user " + user);
		this.maximalRequestAgeSeconds = maximalRequestAgeSeconds;
		this.myUserId = user;
		this.peerUserId = peer;
		this.negotiationService = negotiationService;
		this.iceconnect = iceconnect;
		this.udtconnect = udtconnect;

		startTimeoutTimer();
	}

	public void stop() {
		this.running = false;
		Thread.currentThread().interrupt();
	}

	@Override
	public void run() {
		if (PROACTIVE) {
			iceconnect.getNomination(peerUserId);
		}
		while (running) {
			try {
				this.currentRequest = requests.poll(1, TimeUnit.SECONDS);
				if (this.currentRequest == null)
					continue;
				log.debug("handling request " + currentRequest);
				iceconnect.getNomination(peerUserId);
				handleRequest();
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
		if (!this.requests.add(r)) {
			log.warn("couldn't add request to queue");
			throw new IllegalStateException("queue full, shouldn't happen");
		}
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

	public void receivedAck(final FileRequest fr, final AESObject aes) {
		// so the other user has the file and will send it to us through the
		// socket (or is already transmitting).
		if (!this.currentRequest.equals(fr)) {
			log.warn("received a ack, but not for the right file request");
			return;
		}
		try {
			log.info("received server ACK, starting file transfer");
			AvailableLater<IcePeer> peerAvl = iceconnect.getNomination(fr
					.getPeer());
			final INegotiationSuccessListener listener = getCurrentListener();
			new AvailableLaterWrapperObject<Void, IcePeer>(peerAvl) {

				@Override
				public Void calculate() throws Exception {
					log.debug("we finally have a peer -- connecting");
					socket = udtconnect.connect(iceconnect.getSocket(),
							getSourceResult(), false);
					log.debug("connected -- running file transfer now");
					IceUdtFileTransfer ft = new IceUdtFileTransfer(fr, socket,
							aes);
					listener.succeeded(ft);
					ft.run();
					return null;
				}
			}.start();
		} catch (Exception e) {
			log.warn("received a Ack, but we couldn't find a socket", e);
			getCurrentListener()
					.failed(new IOException(
							"No socket connection available. Try again later."));
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
