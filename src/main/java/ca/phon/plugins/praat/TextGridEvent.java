/*
 * phon-textgrid-plugin
 * Copyright (C) 2015, Gregory Hedlund <ghedlund@mun.ca>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ca.phon.plugins.praat;

import ca.phon.project.Project;

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
	private Project project;
	
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

	public TextGridEvent(Project project, String corpus, String session,
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

	public Project getProject() {
		return project;
	}

	public void setProject(Project project) {
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
