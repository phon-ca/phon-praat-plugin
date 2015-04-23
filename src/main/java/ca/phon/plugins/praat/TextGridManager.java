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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.sys.Data;
import ca.hedlund.jpraat.binding.sys.MelderFile;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.project.Project;

/**
 * Utility class for reading/writing TextGrid files
 * 
 */
public class TextGridManager {
	
	private final static Logger LOGGER = Logger.getLogger(TextGridManager.class.getName());
    
	/**
	 * Location of textgrid files in project folder
	 */
	private final static String TEXTGRID_FOLDER = "__res/plugin_data/textgrid/data";
	
	private final static String ALTERNATE_TEXTGRID_ENCODING = "UTF-8";
	
	private final static String DEFAULT_TEXTGRID_ENCODING = "UTF-16";
	
	private final static String TEXTGRID_EXT = ".TextGrid";
	
	/**
	 * Project we are managing
	 */
	private Project project;
	
	private List<TextGridListener> listeners = Collections.synchronizedList(new ArrayList<TextGridListener>());

	public TextGridManager(Project project) {
		super();
		
		this.project = project;
	}
	
	/**
	 * Load the TextGrid for the given corpus, session
	 * and recordId
	 * 
	 * @param corpus
	 * @param session
	 * @param recordId
	 * 
	 * @return the textgrid or <code>null</code> if not
	 *  found/loaded
	 */
	public TextGrid loadTextGrid(String recordId) {
		final String tgPath = textGridPath(recordId);
		
		TextGrid retVal = null;
		
		try {
			retVal = loadTextGrid(new File(tgPath));
		} catch(IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		
		return retVal;
	}
	
	/**
	 * Save text grid
	 * 
	 * @param textgrid
	 * @param corpus
	 * @param session
	 * @param recordId
	 * 
	 * @returns <code>true</code> if successful, <code>false</code>
	 *  otherwise
	 */
	public boolean saveTextGrid(TextGrid textgrid, String recordId) {
		final String tgPath = textGridPath(recordId);
		boolean retVal = false;
		
		try {
			saveTextGrid(textgrid, new File(tgPath));
			retVal = true;
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		
		return retVal;
	}
	
	/**
	 * Save textgrid to file
	 * 
	 * @param textgrid
	 * @param file
	 * 
	 * @throws IOException
	 */
	public static void saveTextGrid(TextGrid textgrid, File tgFile)
		throws IOException {
		
		final File tgParent = tgFile.getParentFile();
		if(!tgParent.exists())
			tgParent.mkdirs();
		
		try {
			textgrid.writeToTextFile(MelderFile.fromPath(tgFile.getAbsolutePath()));
		} catch (PraatException e) {
			throw new IOException(e);
		}
	}
	
	/**
	 * Load textgrid from file
	 * 
	 * @param file
	 * @return textgrid
	 * @throws IOException
	 */
	public static TextGrid loadTextGrid(File tgFile)
		throws IOException {
		TextGrid retVal = null;
		
		try {
			LOGGER.info(tgFile.getAbsolutePath());
			retVal = Data.readFromFile(TextGrid.class, MelderFile.fromPath(tgFile.getAbsolutePath()));
		} catch (PraatException e) {
			throw new IOException(e);
		}
		
		return retVal;
	}
	
	/**
	 * Get the location of the textgrid file
	 *
	 * @param corpus
	 * @param session
	 * @param recordId
	 * 
	 * @return textgrid path
	 */
	public String textGridPath(String recordId) {
		final StringBuilder sb = new StringBuilder();
		sb.append(project.getLocation());
		sb.append(File.separator);
		sb.append(TEXTGRID_FOLDER);
		sb.append(File.separator);
//		sb.append(corpus);
//		sb.append("_");
//		sb.append(session);
//		sb.append("_");
		sb.append(recordId);
		sb.append(TEXTGRID_EXT);
		
		return sb.toString();
	}
	
	/**
	 * Add a listener
	 * 
	 * @param listener
	 */
	public void addTextGridListener(TextGridListener listener) {
		if(!listeners.contains(listener)) {
			listeners.add(listener);
			
//			if(listeners.size() == 1) {
//				startWatcher();
//			}
		}
	}
	
	public void removeTextGridListener(TextGridListener listener) {
		listeners.remove(listener);
//		if(listeners.size() == 0) {
//			watcher.shutdown();
//		}
	}

	public List<TextGridListener> getTextGridListeners() {
		return Collections.unmodifiableList(listeners);
	}
	
	public void fireTextGridEvent(TextGridEvent evt) {
		for(TextGridListener listener:getTextGridListeners()) {
			listener.textGridEvent(evt);
		}
	}
	
//	private WatchService watchService;
//	
//	private void startWatcher() {
//		try {
//			watchService = FileSystems.getDefault().newWatchService();
//			
//			final Path dir = FileSystems.getDefault().getPath(project.getLocation(), TEXTGRID_FOLDER);
//			final WatchKey key = dir.register(watchService, 
//					StandardWatchEventKinds.ENTRY_CREATE,
//					StandardWatchEventKinds.ENTRY_DELETE, 
//					StandardWatchEventKinds.ENTRY_MODIFY);
//			final PhonWorker worker = PhonWorker.createWorker();
//			worker.setFinishWhenQueueEmpty(true);
//			worker.invokeLater(watcher);
//			worker.start();
//			
//		} catch (IOException e) {
//			LOGGER.log(Level.SEVERE, e.getMessage(), e);
//		}
//	}
	
//	private final PhonTask watcher = new PhonTask() {
//		
//		@Override
//		public void performTask() {
//			setStatus(TaskStatus.RUNNING);
//			
//			for(;;) {
//				if(super.isShutdown())
//					break;
//				
//				WatchKey key = null;
//				try {
//					key = watchService.take();
//				} catch (InterruptedException e) {
//					LOGGER.log(Level.SEVERE, e.getMessage(), e);
//					super.err = e;
//					setStatus(TaskStatus.ERROR);
//					return;
//				}
//				
//				if(key == null) continue;
//				
//				for(WatchEvent<?> evt:key.pollEvents()) {
//					final WatchEvent.Kind<?> kind = evt.kind();
//					if(kind == StandardWatchEventKinds.OVERFLOW
//							|| !(evt.context() instanceof Path)) {
//						continue;
//					}
//					
//					
//					final Path path = (Path)evt.context();
//					final String filename = path.getFileName().toString();
//					
////					LOGGER.info(filename);
//					
//					final String regex = "([^_]+)_([^_]+)_(u[0-9]+)\\.TextGrid";
//					final Pattern pattern = Pattern.compile(regex);
//					final Matcher matcher = pattern.matcher(filename);
//					
//					if(matcher.matches()) {
//						final String corpus = matcher.group(1);
//						final String session = matcher.group(2);
//						final String recordID = matcher.group(3);
//						
//						TextGridEvent.TextGridEventType type = null;
//						if(kind == StandardWatchEventKinds.ENTRY_CREATE) 
//							type = TextGridEventType.TEXTGRID_ADDED;
//						else if(kind == StandardWatchEventKinds.ENTRY_DELETE)
//							type = TextGridEventType.TEXTGRID_REMOVED;
//						else
//							type = TextGridEventType.TEXTGRID_CHANGED;
//						
//						final TextGridEvent tgEvent = 
//								new TextGridEvent(project, corpus, session, recordID, TextGridManager.this, type);
//						fireTextGridEvent(tgEvent);
//					}
//					
//					boolean valid = key.reset();
//					if(!valid) {
//						break;
//					}
//				}
//			}
//			
//			setStatus(TaskStatus.FINISHED);
//		}
//	};
}
