package ca.phon.plugins.praat;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.phon.plugins.praat.TextGridEvent.TextGridEventType;
import ca.phon.project.Project;
import ca.phon.textgrid.TextGrid;
import ca.phon.textgrid.TextGridReader;
import ca.phon.textgrid.TextGridWriter;
import ca.phon.worker.PhonTask;
import ca.phon.worker.PhonTask.TaskStatus;
import ca.phon.worker.PhonWorker;

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
	
	private static final Map<Project, TextGridManager> managers = 
			Collections.synchronizedMap(new HashMap<Project, TextGridManager>());
	
	public synchronized static TextGridManager getInstance(Project project) {
		TextGridManager retVal = managers.get(project);
		if(retVal == null) {
			retVal = new TextGridManager(project);
			managers.put(project, retVal);
		}
		return retVal;
	}
	
	private TextGridManager(Project project) {
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
			retVal = loadTextGrid(new File(tgPath), DEFAULT_TEXTGRID_ENCODING);
		} catch (ParseException e) {
			try {
				retVal = loadTextGrid(new File(tgPath), ALTERNATE_TEXTGRID_ENCODING);
			} catch (IOException e1) {
				LOGGER.log(Level.SEVERE, e1.getLocalizedMessage(), e1);
			} catch (ParseException e1) {
				LOGGER.log(Level.SEVERE, "Unable to read TextGrid file, unknown encoding.", e);
			}
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
		
		final TextGridWriter tgWriter = new TextGridWriter(textgrid, tgFile, DEFAULT_TEXTGRID_ENCODING);
		tgWriter.writeTextGrid();
		tgWriter.close();
	}
	
	/**
	 * Load textgrid from file
	 * 
	 * @param file
	 * @return textgrid
	 * @throws IOException
	 */
	public static TextGrid loadTextGrid(File tgFile, String encoding) 
		throws IOException, ParseException {
		final TextGridReader tgReader = new TextGridReader(tgFile, encoding);
		TextGrid retVal;
		retVal = tgReader.readTextGrid();
		tgReader.close();
		
		return retVal;
	}
	
	public static TextGrid loadTextGrid(File tgFile)
		throws IOException {
		TextGrid retVal = null;
		
		try {
			TextGridReader reader = new TextGridReader(tgFile, DEFAULT_TEXTGRID_ENCODING);
			retVal = reader.readTextGrid();
			reader.close();
		} catch (ParseException e) {
			try {
				TextGridReader reader = new TextGridReader(tgFile, ALTERNATE_TEXTGRID_ENCODING);
				retVal = reader.readTextGrid();
				reader.close();
			} catch (ParseException e1) {
				LOGGER.log(Level.WARNING, "Unable to reader TextGrid file '" + tgFile.getAbsolutePath() + "', unknown encoding.");
			}
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
