package ca.phon.plugins.praat;

import java.io.File;
import java.io.IOException;

import ca.phon.application.project.IPhonProject;
import ca.phon.exceptions.ParserException;
import ca.phon.plugins.praat.textgrid.TextGrid;
import ca.phon.plugins.praat.textgrid.TextGridReader;
import ca.phon.plugins.praat.textgrid.TextGridWriter;
import ca.phon.system.logger.PhonLogger;

/**
 * Utility class for reading/writing TextGrid files
 * 
 */
public class TextGridManager {

	/**
	 * Location of textgrid files in project folder
	 */
	private final static String TEXTGRID_FOLDER = "__res/plugin_data/textgrid/data";
	
	private final static String TEXTGRID_ENCODING = "UTF-16";
	
	private final static String TEXTGRID_EXT = ".TextGrid";
	
	/**
	 * Project we are managing
	 */
	private IPhonProject project;
	
	public TextGridManager(IPhonProject project) {
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
	public TextGrid loadTextGrid(String corpus, String session, String recordId) {
		final String tgPath = textGridPath(corpus, session, recordId);
		
		TextGrid retVal = null;
		
		try {
			retVal = loadTextGrid(tgPath);
		} catch (IOException e) {
			
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
	public boolean saveTextGrid(TextGrid textgrid, String corpus, String session, String recordId) {
		final String tgPath = textGridPath(corpus, session, recordId);
		boolean retVal = false;
		
		try {
			saveTextGrid(textgrid, tgPath);
			retVal = true;
		} catch (IOException e) {
			e.printStackTrace();
			PhonLogger.severe(e.getMessage());
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
	public static void saveTextGrid(TextGrid textgrid, String file)
		throws IOException {
		
		final File tgFile = new File(file);
		final File tgParent = tgFile.getParentFile();
		if(!tgParent.exists())
			tgParent.mkdirs();
		
		final TextGridWriter tgWriter = new TextGridWriter(textgrid, tgFile, TEXTGRID_ENCODING);
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
	public static TextGrid loadTextGrid(String file) 
		throws IOException {
		final File tgFile = new File(file);
		
		final TextGridReader tgReader = new TextGridReader(tgFile, TEXTGRID_ENCODING);
		TextGrid retVal;
		try {
			retVal = tgReader.readTextGrid();
		} catch (ParserException e) {
			throw new IOException(e);
		}
		tgReader.close();
		
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
	public String textGridPath(String corpus, String session, String recordId) {
		final StringBuilder sb = new StringBuilder();
		sb.append(project.getProjectLocation());
		sb.append(File.separator);
		sb.append(TEXTGRID_FOLDER);
		sb.append(File.separator);
		sb.append(corpus);
		sb.append("_");
		sb.append(session);
		sb.append("_");
		sb.append(recordId);
		sb.append(TEXTGRID_EXT);
		
		return sb.toString();
	}
	
	
}
