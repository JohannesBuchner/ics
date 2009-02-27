package com.jakeapp.core.domain;

import java.util.UUID;
import java.io.Serializable;

import javax.persistence.*;

/**
 * The representation of the jakeObject. A JakeObject is anything that
 * can be shared among clients.
 */

@Entity(name = "jakeobject")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class JakeObject implements ILogable, Serializable {
	private static final long serialVersionUID = -3601181472574035652L;
	
    private UUID uuid;
    private Project project;

    private boolean deleted;
    private boolean modified;


    /**
     * Default ctor.
     */
    public JakeObject() {
    	// default ctor for hibernate
    }

    /**
     * Construct a new <code>JakeObject</code>.
     * @param uuid the <code>uuid</code> of the <code>JakeObject</code>
     * @param project the <code>Project</code> that is associated with the
     * <code>JakeObject</code>
     * @throws IllegalArgumentException if uuid was not valid
     * @see JakeObject#setUuid(UUID)
     */
    protected JakeObject(UUID uuid, Project project) throws
            IllegalArgumentException {
        this.setUuid(uuid);
        this.setProject(project);
    }

    /**
     * Get the <code>uuid</code>.
     * @return the <code>uuid</code> of the <code>JakeObject</code>
     */
    @Transient
    public UUID getUuid() {
        return this.uuid;
    }


    @Id
    @Column(name = "objectId", unique = true, columnDefinition = "char(36)")
    private String getUuidString()
    {
        return this.uuid.toString();
    }


    private void setUuidString(String uuid)
    {
        this.uuid = UUID.fromString(uuid);
    }

    /**
     * Get the associated <code>project</code>.
     * @return the project that is associated with the <code>JakeObject</code>
		 * @throws IllegalArgumentException
     */
    @Transient
    public Project getProject() throws IllegalArgumentException {
        return this.project;
    }

    /**
     * Sets the UUID of this JakeObject to the given uuid object.
     * @param uuid the uuid to be set
     * @throws IllegalArgumentException if null is supplied for
     * <code>uuid</code>
     */
    protected void setUuid(UUID uuid) throws IllegalArgumentException {
    	if (uuid == null) {
    		throw new IllegalArgumentException();
    	}
        this.uuid = uuid;
    }

    public void setProject(Project project) {
        this.project = project;
    }


    @Column(name = "deleted")
    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Column(name = "modified")
    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }


	@Override
	public String toString() {
		return "JakeObject:" + this.getUuid();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (deleted ? 1231 : 1237);
		result = prime * result + (modified ? 1231 : 1237);
		result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		JakeObject other = (JakeObject) obj;
		if (deleted != other.deleted)
			return false;
		if (modified != other.modified)
			return false;
		if (uuid == null) {
			if (other.uuid != null)
				return false;
		} else if (!uuid.equals(other.uuid))
			return false;
		return true;
	}
}
