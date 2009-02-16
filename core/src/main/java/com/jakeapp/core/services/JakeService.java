package com.jakeapp.core.services;

import org.apache.log4j.Logger;
import org.springframework.transaction.annotation.Transactional;

import com.jakeapp.core.dao.IUserIdDao;
import com.jakeapp.core.dao.exceptions.NoSuchLogEntryException;
import com.jakeapp.core.domain.JakeObject;
import com.jakeapp.core.util.ProjectApplicationContextFactory;

/**
 * Abstract baseclass for services using an ApplicationContext.
 * Primary use is eliminating duplicate code.
 *  
 * @author djinn
 *
 */
public abstract class JakeService {
	private static final Logger log = Logger.getLogger(JakeService.class);
	
	private ProjectApplicationContextFactory applicationContextFactory;
	private IUserIdDao userIdDao;
	
	
	public JakeService(ProjectApplicationContextFactory applicationContextFactory,IUserIdDao userIdDao) {
		super();
		this.setApplicationContextFactory(applicationContextFactory);
		this.setUserIdDao(userIdDao);
	}
	
	/**
	 * *********** GETTERS & SETTERS ************
	 */
	
	public void setUserIdDao(IUserIdDao userIdDao) {
		this.userIdDao = userIdDao;
	}

	public IUserIdDao getUserIdDao() {
		return this.userIdDao;
	}
	
	public ProjectApplicationContextFactory getApplicationContextFactory() {
		return this.applicationContextFactory;
	}

	public void setApplicationContextFactory(
			  ProjectApplicationContextFactory applicationContextFactory) {
		this.applicationContextFactory = applicationContextFactory;
	}

	@Transactional
	public boolean isLocalJakeObject(JakeObject jo) {
		boolean result = false;
	
		//FIXME: make prettier!!! 
		try {
			log.debug("Checking isLocalJakeObject for jo " + jo + " with project " + jo.getProject());
			this.getApplicationContextFactory().getLogEntryDao(jo.getProject()).getMostRecentFor(jo);
			
		} catch (NoSuchLogEntryException e) {
			/*
			* There is no Logentry for this note. Therefore it has never been
			* announced and is only local.
			*/
			result = true;
		}
		return result;
	}

}