package com.jakeapp.gui.swing.helpers;

import com.jakeapp.core.domain.Project;
import com.jakeapp.gui.swing.JakeMainApp;
import com.jakeapp.gui.swing.JakeMainView;
import org.jdesktop.application.Application;
import org.jdesktop.application.ResourceMap;

/**
 * JakeMainHelper has static functions that are used all across the ui codebase.
 * User: studpete
 * Date: Dec 21, 2008
 * Time: 5:41:44 PM
 */
public class JakeMainHelper {

    private static ResourceMap resourceMap;

    public static void initializeJakeMainHelper() {
        // initialize the resource map from main view
        setResourceMap(Application.getInstance(JakeMainApp.class).getContext().getResourceMap(JakeMainView.class));
    }


    public static String getPluralModifer(int clickCount) {
        return clickCount == 1 ? "" : "s";
    }


    private static ResourceMap getResourceMap() {
        return resourceMap;
    }

    private static void setResourceMap(ResourceMap resourceMap) {
        resourceMap = resourceMap;
    }

    public static String printProjectStatus(Project project) {
        // TODO: determine status
        return "Project is ...TODO!";
    }
}