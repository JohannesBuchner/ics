package com.jakeapp.core.services;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.jakeapp.core.dao.IProjectDao;
import com.jakeapp.core.dao.exceptions.NoSuchProjectException;
import com.jakeapp.core.domain.FileObject;
import com.jakeapp.core.domain.ILogable;
import com.jakeapp.core.domain.InvitationState;
import com.jakeapp.core.domain.JakeObject;
import com.jakeapp.core.domain.LogEntry;
import com.jakeapp.core.domain.NoteObject;
import com.jakeapp.core.domain.Project;
import com.jakeapp.core.domain.TrustState;
import com.jakeapp.core.domain.UserId;
import com.jakeapp.core.domain.exceptions.InvalidProjectException;
import com.jakeapp.core.domain.exceptions.ProjectNotLoadedException;
import com.jakeapp.core.synchronization.ChangeListener;
import com.jakeapp.core.synchronization.exceptions.ProjectException;
import com.jakeapp.core.util.ApplicationContextFactory;
import com.jakeapp.jake.fss.IFSService;
import com.jakeapp.jake.fss.exceptions.InvalidFilenameException;
import com.jakeapp.jake.fss.exceptions.NotADirectoryException;


public class ProjectsManagingServiceImpl implements IProjectsManagingService {
	private static final Logger log = Logger.getLogger(ProjectsManagingServiceImpl.class);

	private List<Project> projectList = new ArrayList<Project>();
	
	private Map<Project,IFSService> fileServices = new HashMap<Project,IFSService>();

	private ApplicationContextFactory applicationContextFactory;

	private IProjectDao projectDao;

	private InternalFrontendService frontendService;

	public ProjectsManagingServiceImpl() {
	}

	/************** GETTERS & SETTERS *************/

	private void setFileServices(Map<Project,IFSService> fileServices) {
		this.fileServices = fileServices;
	}

	private Map<Project,IFSService> getFileServices() {
		return fileServices;
	}

	public IProjectDao getProjectDao() {
		return projectDao;
	}

	public void setProjectDao(IProjectDao projectDao) {
		this.projectDao = projectDao;
	}

	public ApplicationContextFactory getApplicationContextFactory() {
		return applicationContextFactory;
	}

	public void setApplicationContextFactory(
			ApplicationContextFactory applicationContextFactory) {
		this.applicationContextFactory = applicationContextFactory;
	}
	
	@Override
	public IFSService getFileServices(Project p) {
		return this.getFileServices().get(p);
	}

	private List<Project> getInternalProjectList() {
		return this.projectList;
	}

	/******** STARTING IMPLEMENTATIONS **************/
	@Override
	public List<Project> getProjectList() {
		return this.getProjectDao().getAll();
		//return Collections.unmodifiableList(this.getInternalProjectList());
	}

	@Override
	public List<Project> getProjectList(InvitationState state) {
		
		//FIXME not very elegant: can't we do that via DB-Select?
		List<Project> all = this.getProjectList();
		List<Project> result = new ArrayList<Project>();
		
		for (Project p : all)
			if (p.getInvitationState().equals(state))
				result.add(p);
		
		return result;
	}

	@Override
	public Project createProject(String name, String rootPath, MsgService msgService)
			throws FileNotFoundException, IllegalArgumentException {
		
		File projectRoot = new File(rootPath);
		
		//create a new, empty project
		Project project = new Project(name, UUID.randomUUID(), msgService, projectRoot);
		
		//create and initialize the Project's root folder
		/*
		 * Since the FSService would start to watch the directory immediately, it is not created yet.
		 */
		projectRoot.mkdirs();
		if (!projectRoot.isDirectory() || !projectRoot.canWrite()) {
			log.warn("Creating a Project's root path failed.");
			throw new FileNotFoundException();
		}
		
		try {
			this.initializeProjectFolder();
		} catch (IOException e) {
			throw new FileNotFoundException();
		}
		
		//create the new Project in the global database
		try {
			project = this.getProjectDao().create(project);
		} catch (InvalidProjectException e) {
			log.debug("Creating a project failed.",e);
			project = null;
		}
		
		if (project!=null) {
			//add the project to the service's internal project list	
			this.getInternalProjectList().add(project);
		}
		
		//create an applicationContext for the Project
		this.applicationContextFactory.getApplicationContext(project);
		
		//TODO create a new Project-local database
		
		return project; 
	}

	/**
	 * Initializes a project folder by putting special files
	 * (self-implemented trash, ...) in it.
	 */
	private void initializeProjectFolder() throws IOException {
		//empty implementation
	}

	@Override
	public boolean startProject(Project project, ChangeListener cl) throws IllegalArgumentException,
			FileNotFoundException, ProjectException {
		//Check preconditions
		if (project == null) throw new IllegalArgumentException();
		if (!project.isOpen() || project.isStarted()) return false;
		
		try {
			this.getFileServices(project).setRootPath(project.getRootPath());
		} catch (Exception e) {
			throw new ProjectException(e);
		}
		
		frontendService.getSync().startServing(project, new TrustRequestHandlePolicy(project), cl);
		
		project.setStarted(true);	
		
		return true;
	}

	@Override
	public boolean stopProject(Project project) throws IllegalArgumentException,
			FileNotFoundException {
		//Check preconditions
		if (project == null) throw new IllegalArgumentException();
		if (!project.isOpen() || !project.isStarted()) return false;
		
		try {
			frontendService.getSync().stopServing(project);
			//stops monitoring the project
			this.getFileServices(project).unsetRootPath();
		}
		finally {
			project.setStarted(false);
		}
		
		return true;
	}

	@Override
	public void closeProject(Project project) throws IllegalArgumentException,
			FileNotFoundException {
		//Check preconditions
		if (project == null) throw new IllegalArgumentException();
		
		//Make sure project is stopped
		if (project.isStarted())
			this.stopProject(project);
		
		//remove project from internal list
		this.getInternalProjectList().remove(project);
		//remove the project's file services
		this.getFileServices().remove(project);
		
		//Remove Project from the database
		try {
			this.getProjectDao().delete(project);
		} catch (NoSuchProjectException e) {
			log.warn("Project does not exist in DB and cannot be deleted from it.",e);
		}
	}

	@Override
	public Project openProject(String project) throws IllegalArgumentException,
			FileNotFoundException {
		//Check preconditions
		if (project == null) throw new IllegalArgumentException();
		
		// TODO Auto-generated method stub
		// TODO the interface for this method is very bogus
		
		return null;
	}

	@Override
	public boolean deleteProject(Project project) throws IllegalArgumentException,
			SecurityException, FileNotFoundException {
		boolean result = true;
		IFSService fss;
		FileNotFoundException t = null;
		
		//Check preconditions
		if (project == null) throw new IllegalArgumentException();
		
		//Remove the Project's root folder
		fss = this.getFileServices(project);
		if (fss!=null) {
			try {
				fss.trashFile(project.getRootPath());
			} catch (InvalidFilenameException e) {
				log.warn("Deleting Project with invalid rootpath.",e);
			} catch (FileNotFoundException e) {
				t = e;
			}
		}
		
		//Make sure project is stopped & closed
		if (project.isOpen())
			this.closeProject(project);
		
		if (t!=null) throw t;
		
		return result; 
	}

	@Override
	public Map<Project, List<LogEntry<? extends ILogable>>> getLog() {
		return null; // TODO
	}

	@Override
	public List<LogEntry<? extends ILogable>> getLog(Project project)
			throws IllegalArgumentException {
		return null; // TODO
	}

	@Override
	public List<LogEntry<? extends ILogable>> getLog(JakeObject jakeObject)
			throws IllegalArgumentException {
		return null; // TODO
	}

	@Override
	public void assignUserToProject(Project project, UserId userId)
			throws IllegalArgumentException, IllegalAccessException {
		// TODO
	}

	@Override
	public void setTrust(Project project, UserId userid, TrustState trust) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<FileObject> getFiles(Project project, String relPath)
			throws IllegalArgumentException {
		return null; // TODO
	}

	@Override
	public List<NoteObject> getNotes(Project project) throws IllegalArgumentException,
			ProjectNotLoadedException {

		if (false) // TODO check if project is loaded
			throw new ProjectNotLoadedException("Project with uuid "
					+ project.getUserId() + " is not loaded");

		// todo replace with dao access
		List<NoteObject> list = new ArrayList<NoteObject>();
		list
				.add(new NoteObject(new UUID(1, 1), project, "Project: "
						+ project.getName()));
		list
				.add(new NoteObject(
						new UUID(1, 1),
						project,
						"If you have five dollars and Chuck Norris has five dollars, Chuck Norris has more money than you"));
		list.add(new NoteObject(new UUID(2, 1), project,
				"Apple pays Chuck Norris 99 cents every time he listens to a song."));
		list
				.add(new NoteObject(
						new UUID(3, 1),
						project,
						"Chuck Norris is suing Myspace for taking the name of what he calls everything around you."));
		list
				.add(new NoteObject(
						new UUID(4, 1),
						project,
						"Chuck Norris destroyed the periodic table, because he only recognizes the element of surprise."));
		list.add(new NoteObject(new UUID(4, 1), project,
				"Chuck Norris can kill two stones with one bird."));
		list
				.add(new NoteObject(
						new UUID(5, 1),
						project,
						"The leading causes of death in the United States are: 1. Heart Disease 2. Chuck Norris 3. Cancer."));
		list.add(new NoteObject(new UUID(6, 1), project,
				"Chuck Norris does not sleep. He waits."));
		list
				.add(new NoteObject(new UUID(7, 1), project,
						"There is no theory of evolution. Just a list of animals Chuck Norris allows to live. "));
		list.add(new NoteObject(new UUID(8, 1), project,
				"Guns don't kill people, Chuck Norris does."));
		return list;
	}
}
