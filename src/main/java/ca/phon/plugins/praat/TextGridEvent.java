/*
 * Copyright (C) 2012-2018 Gregory Hedlund
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *    http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.phon.plugins.praat;

import ca.phon.project.*;

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
