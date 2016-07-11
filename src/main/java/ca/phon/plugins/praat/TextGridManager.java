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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.hedlund.jpraat.binding.fon.IntervalTier;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.binding.fon.TextPoint;
import ca.hedlund.jpraat.binding.fon.TextTier;
import ca.hedlund.jpraat.binding.sys.Daata;
import ca.hedlund.jpraat.binding.sys.MelderFile;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.project.Project;
import ca.phon.session.Record;
import ca.phon.session.Session;

/**
 * Utility class for reading/writing TextGrid files
 * 
 */
public class TextGridManager {
	
	private final static Logger LOGGER = Logger.getLogger(TextGridManager.class.getName());
    
	/**
	 * Location of textgrid files in project folder for records
	 * 
	 * @deprecated since ver 15
	 */
	private final static String RECORD_TEXTGRID_FOLDER = "plugin_data/textgrid/data";
	
	/**
	 * Location of textgrid files in project folder for sessions
	 */
	private final static String SESSION_TEXTGRID_FOLDER = "textgrids/";
	
	public final static String DEFAULT_TEXTGRID_NAME = "default";
	
	private final static String TEXTGRID_EXT = ".TextGrid";
	
	/**
	 * Project we are managing
	 */
	private Project project;
	
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
	 *  
	 * @deprecated since version 15
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
	 *  
	 * @deprecated since version 15
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
	 * List available TextGrids for a given session.
	 * 
	 * @param corpus
	 * @param session
	 * 
	 * @return list of textgrid files available
	 */
	public List<File> textGridFilesForSession(String corpus, String session) {
		List<File> retVal = new ArrayList<>();
		
		final Path textGridFolderPath = Paths.get(textGridFolder(corpus, session));
		if(Files.exists(textGridFolderPath)) {
			 try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(textGridFolderPath)) {
				 for(Path subFile:directoryStream) {
					 if(subFile.getFileName().toString().endsWith(TEXTGRID_EXT)) {
						 retVal.add(subFile.toFile());
					 }
				 }
			 } catch (IOException e) {
				 LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			 }
		}
		
		return retVal;
	}
	
	/**
	 * List of TextGrid names for a given session.
	 * 
	 * @param corpus
	 * @param session
	 * 
	 * @return textgrid names
	 */
	public List<String> textGridNamesForSession(String corpus, String session) {
		List<String> retVal = new ArrayList<>();
		
		textGridFilesForSession(corpus, session).forEach(
			(File file) -> {
				String name = file.getName();
				name = name.substring(0, name.length()-TEXTGRID_EXT.length());
				retVal.add(name);
			} 
		);
		
		return retVal;
	}
	
	/**
	 * Get the default TextGrid file for a given session.  This will be 
	 * the TextGrid file named 'default.TextGrid' or the first TextGrid
	 * found in the text grid folder.
	 * 
	 * @param corpus
	 * @param session
	 * 
	 * @param TextGrid file or <code>null</code> if not found
	 */
	public File defaultTextGridFile(String corpus, String session) {
		List<File> textGridFiles = textGridFilesForSession(corpus, session);
		
		File retVal = null;
		for(File textGridFile:textGridFiles) {
			if(retVal == null)
				retVal = textGridFile;
			if(textGridFile.getName().equals(DEFAULT_TEXTGRID_NAME + TEXTGRID_EXT)) {
				retVal = textGridFile;
				break;
			}
		}
		
		return retVal;
	}
	
	/**
	 * Default TextGrid name for session.
	 * 
	 * @param corpus
	 * @param session
	 * 
	 * @return TextGrid name or <code>null</code> if not found
	 */
	public String defaultTextGridName(String corpus, String session) {
		final File defaultTextGridFile = defaultTextGridFile(corpus, session);
		
		String retVal = null;
		if(defaultTextGridFile != null) {
			retVal = defaultTextGridFile.getName();
			retVal = retVal.substring(0, retVal.length()-TEXTGRID_EXT.length());
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
			retVal = Daata.readFromFile(TextGrid.class, MelderFile.fromPath(tgFile.getAbsolutePath()));
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
	 * 
	 * @deprecated since version 15
	 */
	public String textGridPath(String recordId) {
		final StringBuilder sb = new StringBuilder();
		sb.append(project.getResourceLocation());
		sb.append(File.separator);
		sb.append(RECORD_TEXTGRID_FOLDER);
		sb.append(File.separator);
		sb.append(recordId);
		sb.append(TEXTGRID_EXT);
		
		return sb.toString();
	}
	
	/**
	 * TextGrid folder for the given session.
	 * 
	 * @param corpus
	 * @param session
	 * 
	 * @return TextGrid folder for given session
	 */
	public String textGridFolder(String corpus, String session) {
		final StringBuilder sb = new StringBuilder();
		
		sb.append(project.getResourceLocation()).append(File.separator);
		sb.append(SESSION_TEXTGRID_FOLDER).append(File.separator);
		sb.append(corpus).append(File.separator);
		sb.append(session);
		
		return sb.toString();
	}
	
	public String textGridPath(String corpus, String session, String name) {
		final StringBuilder sb = new StringBuilder();
		
		sb.append(textGridFolder(corpus, session)).append(File.separator);
		sb.append(name).append(TEXTGRID_EXT);
		
		return sb.toString();
	}
	
	/**
	 * TextGrid path for given session and TextGrid name.
	 * 
	 * @param corpus
	 * @param session
	 * @param name
	 * 
	 * @return TextGrid if exists.
	 * 
	 * @throws IOException if TextGrid could not be opened
	 */
	public TextGrid openTextGrid(String corpus, String session, String name) 
		throws IOException {
		final String textGridPath = textGridPath(corpus, session, name);
		final File textGridFile = new File(textGridPath);
		
		return loadTextGrid(textGridFile);
	}
	
	/**
	 * Save TextGrid with given name for session.
	 * 
	 * @param corpus
	 * @param session
	 * @param textGrid
	 * @param name
	 * 
	 * @throws IOException
	 */
	public void saveTextGrid(String corpus, String session, TextGrid textGrid, String name) 
		throws IOException {
		final String textGridPath = textGridPath(corpus, session, name);
		final File textGridFile = new File(textGridPath);
		
		saveTextGrid(textGrid, textGridFile);
	}
	
	/**
	 * Merge text grids for a session.
	 * 
	 * @param session
	 * 
	 * @return single TextGrid for merged session
	 */
	public TextGrid mergeTextGrids(Session session) 
		throws IOException {
		// get xmin and xmax
		double xmin = 0;
		double xmax = 0;
		
		List<TextGrid> textGrids = new ArrayList<>();
		for(Record record:session.getRecords()) {
			final String id = record.getUuid().toString();
			
			final String textGridPath = textGridPath(id);
			final File textGridFile = new File(textGridPath);
			if(textGridFile.exists()) {
				try {
					TextGrid tg = loadTextGrid(textGridFile);
	
					xmin = Math.min(xmin, tg.getXmin());
					xmax = Math.max(xmax, tg.getXmax());
					
					textGrids.add(tg);
				} catch (IOException e) {
					// TODO - show warning to user
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
		}
		
		try {
			TextGrid retVal = TextGrid.createWithoutTiers(xmin, xmax);
			
			Map<String, Long> intervalTiers = new LinkedHashMap<>();
			Map<String, Long> pointTiers = new LinkedHashMap<>();
			
			for(TextGrid tg:textGrids) {
				for(long i = 1; i <= tg.numberOfTiers(); i++) {
					try {
						IntervalTier intervalTier = tg.checkSpecifiedTierIsIntervalTier(i);
						
						IntervalTier fullIntervalTier = null;
						if(!intervalTiers.keySet().contains(intervalTier.getName().toString())) {
							fullIntervalTier = IntervalTier.create(xmin, xmax);
							fullIntervalTier.setForgetOnFinalize(false);
							fullIntervalTier.setName(intervalTier.getName());
							fullIntervalTier.removeInterval(1);
							
							retVal.addTier(fullIntervalTier);
							intervalTiers.put(intervalTier.getName().toString(), retVal.numberOfTiers());
						} 
						Long tierNum = intervalTiers.get(intervalTier.getName().toString());
						if(tierNum != null && tierNum > 0 && tierNum <= retVal.numberOfTiers()) {
							try {
								fullIntervalTier = retVal.checkSpecifiedTierIsIntervalTier(tierNum);
							} catch (PraatException e) {
								LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
							}
						}
						
						if(fullIntervalTier != null) {
							for(long j = 1; j <= intervalTier.numberOfIntervals(); j++) {
								TextInterval interval = intervalTier.interval(j);
								fullIntervalTier.addInterval(interval.getXmin(), interval.getXmax(), interval.getText());
							}
						}
						
					} catch (PraatException e) {
						try {
							TextTier pointTier = tg.checkSpecifiedTierIsPointTier(i);
							
							TextTier fullPointTier = null;
							if(!pointTiers.keySet().contains(pointTier.getName().toString())) {
								fullPointTier = TextTier.create(xmin, xmax);
								// don't delete native tier object when reference goes out of scope
								fullPointTier.setForgetOnFinalize(false);
								fullPointTier.setName(pointTier.getName());
								fullPointTier.removePoint(1);
								
								retVal.addTier(fullPointTier);
								pointTiers.put(pointTier.getName().toString(), retVal.numberOfTiers());
							}
							Long tierNum = pointTiers.get(pointTier.getName().toString());
							if(tierNum != null && tierNum > 0 && tierNum <= retVal.numberOfTiers()) {
								try {
									fullPointTier = retVal.checkSpecifiedTierIsPointTier(tierNum);
								} catch (PraatException ex) {
									LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
								}
							}
							
							if(fullPointTier != null) {
								for(long j = 1; j < pointTier.numberOfPoints(); j++) {
									TextPoint tp = pointTier.point(j);
									fullPointTier.addPoint(tp.getNumber(), tp.getText());
								}
							}
							
						} catch (PraatException e1) {
							LOGGER.log(Level.SEVERE, e1.getLocalizedMessage(), e1);
						}
					}
				}
			}
			return retVal;
		} catch (PraatException e) {
			throw new IOException(e);
		}
	}
	
}
