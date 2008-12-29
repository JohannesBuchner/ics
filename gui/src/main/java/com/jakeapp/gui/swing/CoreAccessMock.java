package com.jakeapp.gui.swing;

import com.jakeapp.core.domain.Project;
import com.jakeapp.gui.swing.callbacks.ConnectionStatus;
import com.jakeapp.gui.swing.callbacks.ProjectChanged;
import com.jakeapp.gui.swing.callbacks.RegistrationStatus;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CoreAccessMock implements ICoreAccess {
    private static final Logger log = Logger.getLogger(CoreAccessMock.class);

    private boolean isSignedIn;
    private List<Project> projects = new ArrayList<Project>();

    /**
     * Core Access Mock initialisation code
     */
    public CoreAccessMock() {
        isSignedIn = false;
        connectionStatus = new ArrayList<ConnectionStatus>();
        registrationStatus = new ArrayList<RegistrationStatus>();
        projectChanged = new ArrayList<ProjectChanged>();

        // init the demo projects
        Project pr1 = new Project("ASE", null, null, new File("/Users/studpete/Desktop"));
        pr1.setStarted(true);
        projects.add(pr1);

        Project pr2 = new Project("SEPM", null, null, new File("/Users/studpete/"));
        projects.add(pr2);

        Project pr3 = new Project("Shared Music", null, null, new File("/Users"));
        projects.add(pr3);

    }


    @Override
    public List<Project> getMyProjects() {
        return projects;
    }

    // TODO: change this: invitations are only runtime specific... (are they?)
    @Override
    public List<Project> getInvitedProjects() {
        List<Project> projects = new ArrayList<Project>();

        Project pr1 = new Project("DEMO INVITATION", null, null, new File(""));
        projects.add(pr1);


        Project pr2 = new Project("Not that secret Docs", null, null, new File(""));
        projects.add(pr2);

        return projects;
    }


    public void signIn(String user, String pass) {
        log.info("Signs in: " + user + "pass: " + pass);

        Runnable runner = new Runnable() {
            public void run() {
                callbackConnectionStatus(ConnectionStatus.ConnectionStati.SigningIn, "");

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                callbackConnectionStatus(ConnectionStatus.ConnectionStati.Online, "");
                isSignedIn = true;
            }
        };

        // start our runner thread, that makes callbacks to connection status
        new Thread(runner).start();
    }

    public void registerConnectionStatusCallback(ConnectionStatus cb) {
        log.info("Registers connection status callback: " + cb);

        connectionStatus.add(cb);
    }

    public void deRegisterConnectionStatusCallback(ConnectionStatus cb) {
        log.info("Deregisters connection status callback: " + cb);

        connectionStatus.remove(cb);
    }


    private void callbackConnectionStatus(ConnectionStatus.ConnectionStati state, String str) {
        log.info("spead callback event...");
        for (ConnectionStatus callback : connectionStatus) {
            callback.setConnectionStatus(state, str);
        }
    }

    public void register(String user, String pass) {
        log.info("Registering user: " + user + " pass: " + pass);

        Runnable runner = new Runnable() {
            public void run() {

                // registering
                callbackRegistrationStatus(RegistrationStatus.RegisterStati.RegistrationActive, "");

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                callbackRegistrationStatus(RegistrationStatus.RegisterStati.RegisterSuccess, "");

                // logging in after registering
                callbackConnectionStatus(ConnectionStatus.ConnectionStati.SigningIn, "");

                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                isSignedIn = true;

                callbackConnectionStatus(ConnectionStatus.ConnectionStati.Online, "");
            }
        };

        // start our runner thread, that makes callbacks to connection status
        new Thread(runner).start();
    }

    public void registerRegistrationStatusCallback(RegistrationStatus cb) {
        log.info("Registers registration status callback: " + cb);
    }

    public void deRegisterRegistrationStatusCallback(RegistrationStatus cb) {
        log.info("Deregisters registration status callback: " + cb);

    }


    public boolean isSignedIn() {
        return isSignedIn;
    }


    public void signOut() {
        isSignedIn = false;

        callbackConnectionStatus(ConnectionStatus.ConnectionStati.Offline, "");
    }

    public String[] getLastSignInNames() {
        return new String[]{"pstein", "csutter"};
    }


    public void registerProjectChangedCallback(ProjectChanged cb) {
        log.info("Mock: register project changed callback: " + cb);

        projectChanged.add(cb);
    }

    public void deregisterProjectChangedCallback(ProjectChanged cb) {
        log.info("Mock: deregister project changed callback: " + cb);

        if (projectChanged.contains(cb)) {
            projectChanged.remove(cb);
        }
    }

    private void callbackProjectChanged(ProjectChanged.ProjectChangedEvent ev) {
        for (ProjectChanged callback : projectChanged) {
            callback.projectChanged(ev);
        }
    }


    public void stopProject(Project project) {
        log.info("stop project: " + project);

        //if(!project.isStarted())
        //    throw new ProjectNotStartedException();

        project.setStarted(false);

        // generate event
        callbackProjectChanged(new ProjectChanged.ProjectChangedEvent(project,
                ProjectChanged.ProjectChangedEvent.ProjectChangedReason.State));
    }

    public void startProject(Project project) {
        project.setStarted(true);

        // generate event
        callbackProjectChanged(new ProjectChanged.ProjectChangedEvent(project,
                ProjectChanged.ProjectChangedEvent.ProjectChangedReason.State));
    }


    private void callbackRegistrationStatus(RegistrationStatus.RegisterStati state, String str) {
        for (RegistrationStatus callback : registrationStatus) {
            callback.setRegistrationStatus(state, str);
        }
    }


    private List<ConnectionStatus> connectionStatus;
    private List<RegistrationStatus> registrationStatus;

    private List<ProjectChanged> projectChanged;
}
