package com.jakeapp.jake.ics.exceptions;

/**
 * @author johannes
 * 
 *         The userid to talk to is not registered. This is not thrown if the
 *         user is just offline
 */
@SuppressWarnings("serial")
public class NoSuchUseridException extends NetworkException {

	public NoSuchUseridException() {
	}

	public NoSuchUseridException(String s) {
		super(s);
	}
}
