package com.jakeapp.gui.swing.helpers;

import com.jakeapp.gui.swing.JakeMainApp;
import com.jakeapp.gui.swing.callbacks.ErrorCallback;
import com.jakeapp.gui.swing.dialogs.generic.JSheet;

import javax.swing.*;

/**
 * Exception handling utilites.
 *
 * @author: studpete
 */
public class ExceptionUtilities {
	/**
	 * Displays an exception to the user.
	 *
	 * @param e
	 */
	// TODO: provide a same way of logging, showing details.
	// Custom Dialog would be great!
	public static void showError(Exception e) {
		JSheet.showMessageSheet(JakeMainApp.getFrame(), e.toString(),
				  JOptionPane.ERROR_MESSAGE, null);
	}

	/**
	 * Convenience wrapper for JakeErrorEvent.
	 *
	 * @param ee
	 */
	public static void showError(ErrorCallback.JakeErrorEvent ee) {
		showError(ee.getException());
	}
}