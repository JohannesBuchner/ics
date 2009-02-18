/**
 * 
 */
package com.jakeapp.core.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.jakeapp.core.dao.ILogEntryDao;
import com.jakeapp.core.dao.exceptions.NoSuchLogEntryException;
import com.jakeapp.core.domain.FileObject;
import com.jakeapp.core.domain.ILogable;
import com.jakeapp.core.domain.JakeObject;
import com.jakeapp.core.domain.LogEntry;
import com.jakeapp.core.domain.ProjectMember;
import com.jakeapp.core.domain.Tag;

public final class UnprocessedBlindLogEntryDaoProxy {

	private final ILogEntryDao innerDao;

	public UnprocessedBlindLogEntryDaoProxy(ILogEntryDao innerDao) {
		super();
		this.innerDao = innerDao;
	}

	private static final Boolean processedState = true;

	private static final boolean includeUnprocessed = false;

	public void create(LogEntry<? extends ILogable> logEntry) {
		this.innerDao.create(logEntry);
	}

	/**
	 * @see ILogEntryDao#findLastMatching(LogEntry, Boolean)
	 */
	public LogEntry<? extends ILogable> findLastMatching(LogEntry<? extends ILogable> le) {
		return this.innerDao.findLastMatching(le, processedState);
	}

	/**
	 * @see ILogEntryDao#findMatching(LogEntry, Boolean)
	 */
	public List<LogEntry<? extends ILogable>> findMatching(LogEntry<? extends ILogable> le) {
		return this.innerDao.findMatching(le, processedState);
	}

	/**
	 * @see ILogEntryDao#findMatchingAfter(LogEntry, Boolean)
	 */
	public List<LogEntry<? extends ILogable>> findMatchingAfter(
			LogEntry<? extends ILogable> le) throws NullPointerException {
		return this.innerDao.findMatchingAfter(le, processedState);
	}

	/**
	 * @see ILogEntryDao#findMatchingBefore(LogEntry, Boolean)
	 */
	public List<LogEntry<? extends ILogable>> findMatchingBefore(
			LogEntry<? extends ILogable> le) throws NullPointerException {
		return this.innerDao.findMatchingBefore(le, processedState);
	}

	/**
	 * @see ILogEntryDao#getAll(boolean)
	 */
	public List<LogEntry<? extends ILogable>> getAll() {
		return this.innerDao.getAll(includeUnprocessed);
	}

	/**
	 * @see ILogEntryDao#getAllOfJakeObject(JakeObject, boolean)
	 */
	public <T extends JakeObject> List<LogEntry<T>> getAllOfJakeObject(T jakeObject) {
		return this.innerDao.getAllOfJakeObject(jakeObject, includeUnprocessed);
	}

	/**
	 * @see ILogEntryDao#getAllVersions(boolean)
	 */
	public <T extends JakeObject> List<LogEntry<T>> getAllVersions() {
		return this.innerDao.getAllVersions(includeUnprocessed);
	}

	/**
	 * @see ILogEntryDao#getAllVersionsOfJakeObject(JakeObject, boolean)
	 */
	public <T extends JakeObject> List<LogEntry<T>> getAllVersionsOfJakeObject(
			T jakeObject) {
		return this.innerDao.getAllVersionsOfJakeObject(jakeObject, includeUnprocessed);
	}

	/**
	 * @see ILogEntryDao#getCurrentProjectMembers()
	 */
	public Collection<ProjectMember> getCurrentProjectMembers() {
		return this.innerDao.getCurrentProjectMembers();
	}

	/**
	 * @see ILogEntryDao#getCurrentTags(JakeObject)
	 */
	public Collection<Tag> getCurrentTags(JakeObject belongsTo) {
		return this.innerDao.getCurrentTags(belongsTo);
	}

	/**
	 * @see ILogEntryDao#getDeleteState(JakeObject, boolean)
	 */
	public Boolean getDeleteState(JakeObject jakeObject) {
		return this.innerDao.getDeleteState(jakeObject, includeUnprocessed);
	}

	/**
	 * @see ILogEntryDao#getExistingFileObjects(boolean)
	 */
	public Iterable<FileObject> getExistingFileObjects() {
		return this.innerDao.getExistingFileObjects(includeUnprocessed);
	}

	/**
	 * @see ILogEntryDao#getLastOfJakeObject(JakeObject, boolean)
	 */
	public LogEntry<JakeObject> getLastOfJakeObject(JakeObject jakeObject)
			throws NoSuchLogEntryException {
		return this.innerDao.getLastOfJakeObject(jakeObject, includeUnprocessed);
	}

	/**
	 * @see ILogEntryDao#getLastVersion(JakeObject, boolean)
	 */
	public LogEntry<JakeObject> getLastVersion(JakeObject jakeObject) {
		return this.innerDao.getLastVersion(jakeObject, includeUnprocessed);
	}

	/**
	 * @see ILogEntryDao#getLastVersionOfJakeObject(JakeObject, boolean)
	 */
	public LogEntry<JakeObject> getLastVersionOfJakeObject(JakeObject jakeObject)
			throws NoSuchLogEntryException {
		return this.innerDao.getLastVersionOfJakeObject(jakeObject, includeUnprocessed);
	}

	/**
	 * @see ILogEntryDao#getLock(JakeObject)
	 */
	public LogEntry<JakeObject> getLock(JakeObject belongsTo) {
		return this.innerDao.getLock(belongsTo);
	}

	/**
	 * @see ILogEntryDao#getProjectCreatedEntry()
	 */
	public LogEntry<? extends ILogable> getProjectCreatedEntry() {
		return this.innerDao.getProjectCreatedEntry();
	}
	
	/**
	 * @see ILogEntryDao#getTrustGraph()
	 */
	public Map<ProjectMember, List<ProjectMember>> getTrustGraph() {
		return this.innerDao.getTrustGraph();
	}

	/**
	 * @see ILogEntryDao#trusts(ProjectMember, ProjectMember)
	 */
	public Boolean trusts(ProjectMember a, ProjectMember b) {
		return this.innerDao.trusts(a, b);
	}

	/**
	 * @see ILogEntryDao#trusts(ProjectMember)
	 */
	@Deprecated
	public Collection<ProjectMember> trusts(ProjectMember a) {
		return this.innerDao.trusts(a);
	}

}