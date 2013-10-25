package ca.phon.plugins.praat;

import ca.phon.application.project.IPhonProject;

/**
 * Event sent to TextGridListeners when a TextGrid file
 * is changed.
 */
public class TextGridEvent {
	
	public static enum TextGridEventType {
		TEXTGRID_ADDED,
		TEXTGRID_REMOVED,
		TEXTGRID_CHANGED;
	}

	/**
	 * Project
	 */
	private IPhonProject project;
	
	/**
	 * Corpus
	 */
	private String corpus;
	
	/**
	 * Session
	 */
	private String session;
	
	/**
	 * Record id
	 */
	private String recordID;
	
	/**
	 * TextGridManager
	 */
	private TextGridManager manager;
	
	/**
	 * Type
	 */
	private TextGridEventType type;
	
	public TextGridEvent() {
		super();
	}

	public TextGridEvent(IPhonProject project, String corpus, String session,
			String recordID, TextGridManager manager, TextGridEventType type) {
		super();
		this.project = project;
		this.corpus = corpus;
		this.session = session;
		this.recordID = recordID;
		this.manager = manager;
		this.type = type;
	}

	public TextGridEventType getType() {
		return type;
	}

	public void setType(TextGridEventType type) {
		this.type = type;
	}

	public IPhonProject getProject() {
		return project;
	}

	public void setProject(IPhonProject project) {
		this.project = project;
	}

	public String getCorpus() {
		return corpus;
	}

	public void setCorpus(String corpus) {
		this.corpus = corpus;
	}

	public String getSession() {
		return session;
	}

	public void setSession(String session) {
		this.session = session;
	}

	public String getRecordID() {
		return recordID;
	}

	public void setRecordID(String recordID) {
		this.recordID = recordID;
	}

	public TextGridManager getManager() {
		return manager;
	}

	public void setManager(TextGridManager manager) {
		this.manager = manager;
	}
	
}
