package com.jakeapp.core.util;

import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.jakeapp.core.dao.IFileObjectDao;
import com.jakeapp.core.dao.ILogEntryDao;
import com.jakeapp.core.dao.INoteObjectDao;
import com.jakeapp.core.dao.IProjectMemberDao;
import com.jakeapp.core.dao.exceptions.NoSuchProjectException;
import com.jakeapp.core.domain.JakeObject;
import com.jakeapp.core.domain.Project;
import com.jakeapp.core.domain.ProjectMember;
import com.jakeapp.core.domain.TrustState;

/**
 * A factory that creates and configures spring application contexts.
 * Application contexts for a certain <code>Project</code>are only created once
 * and then reused.
 * 
 * @author Simon
 */
public class ApplicationContextFactory {

	private static Logger log = Logger.getLogger(ApplicationContextFactory.class);

	private Hashtable<String, ConfigurableApplicationContext> contextTable;

	private String[] configLocation;


	public ApplicationContextFactory() {
		log.debug("Creating the ApplicationContextFactory");

		this.contextTable = new Hashtable<String, ConfigurableApplicationContext>();
	}

	/**
	 * Get the <code>ApplicationContext</code> for a given <code>
	 * Project</code>. If an
	 * <code>ApplicationContext</code> for the given <code>Project</code>
	 * already exists, the existing context is returned, otherwise a new context
	 * will be created. This method is <code>synchronized</code>
	 * 
	 * @param project
	 *            the project for which the application context is used
	 * @return the <code>ApplicationContext</code>
	 */
	public synchronized ApplicationContext getApplicationContext(Project project) {

		ConfigurableApplicationContext applicationContext = null;

		log.debug("acquiring context for project: " + project.getProjectId());
		if (this.contextTable.containsKey(project.getProjectId())) {
			log.debug("context for project: " + project.getProjectId()
					+ " already created, returning existing...");
			applicationContext = this.contextTable.get(project.getProjectId());
		} else {
			applicationContext = new ClassPathXmlApplicationContext(this.configLocation);
			Properties props = new Properties();
			props.put("db_path", project.getProjectId());
			PropertyPlaceholderConfigurer cfg = new PropertyPlaceholderConfigurer();
			cfg.setProperties(props);

			log.debug("configuring context with path: " + project.getProjectId());
			applicationContext.addBeanFactoryPostProcessor(cfg);
			applicationContext.refresh();

			this.contextTable.put(project.getProjectId(), applicationContext);
		}
		return applicationContext;
	}

	/**
	 * Set the location of the spring config file to be used by the factory to
	 * create the application contexts.
	 * 
	 * @param configLocation
	 *            the location of the config file (i.e. beans.xml etc.)
	 * @see ClassPathXmlApplicationContext
	 */
	public void setConfigLocation(String[] configLocation) {
		log.debug("Setting config Location to: ");
		for (String bla : configLocation) {
			log.debug(bla);
		}
		this.configLocation = configLocation;
	}

	public ILogEntryDao getLogEntryDao(Project p) {
		return (ILogEntryDao) getApplicationContext(p).getBean("logEntryDao");
	}

	public ILogEntryDao getLogEntryDao(JakeObject jo) {
		return (ILogEntryDao) getApplicationContext(jo.getProject()).getBean(
				"logEntryDao");
	}

	public IProjectMemberDao getProjectMemberDao(Project p) {
		return (IProjectMemberDao) getApplicationContext(p).getBean("projectMemberDao");
	}

	public INoteObjectDao getNoteObjectDao(Project p) {
		return (INoteObjectDao) getApplicationContext(p).getBean("noteObjectDao");
	}

	public IFileObjectDao getFileObjectDao(Project p) {
		return (IFileObjectDao) getApplicationContext(p).getBean("fileObjectDao");
	}

	public Collection<ProjectMember> getProjectMembers(Project p)
			throws NoSuchProjectException {
		return getProjectMemberDao(p).getAll(p);
	}

	public List<ProjectMember> getTrustedProjectMembers(Project p)
			throws NoSuchProjectException {
		List<ProjectMember> allmembers = getProjectMemberDao(p).getAll(p);
		for (ProjectMember member : allmembers) {
			if (member.getTrustState() != TrustState.NO_TRUST)
				allmembers.remove(member);
		}
		return allmembers;
	}
}
