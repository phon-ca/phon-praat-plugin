package ca.phon.plugins.praat.db;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

import com.orientechnologies.orient.core.db.ODatabase;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;

import ca.phon.project.Project;

/**
 * Information obtained through praat scripts are stored
 * in a NoSQL database inside the project __res folder.
 * This class is responsible for creating, opening, inserting records,
 * and querying the database.
 * 
 */
public class PraatDbManager {

	/**
	 * Folder location (in project) of Praat database
	 */
	private final static String DB_PATH = "__res/plugin_data/praat/db";
	
	private final static Map<Project, PraatDbManager> instanceMap = new WeakHashMap<Project, PraatDbManager>();
	
	public static PraatDbManager getInstance(Project project) {
		PraatDbManager retVal = instanceMap.get(project);
		if(retVal == null) {
			retVal = new PraatDbManager(project);
			instanceMap.put(project, retVal);
		}
		return retVal;
	}
	
	/**
	 * Project we are managing data for
	 */
	private final Project project;
	
	private PraatDbManager(Project project) {
		super();
		this.project = project;
	}
	
	/**
	 * Check if database exists.
	 * 
	 * @return <code>true</code> if database exists
	 */
	public boolean databaseExists() {
		final File dbFolder = new File(getDatabasePath());
		return dbFolder.exists();
	}
	
	/**
	 * Create database
	 * 
	 * @return created database instance
	 */
	public ODatabaseDocumentTx createDatabase() {
		final String dbURI = "local:" + getDatabasePath();
		return new ODatabaseDocumentTx(dbURI).create();	
	}
	
	/**
	 * Open database
	 */
	public ODatabaseDocumentTx openDatabase() {
		final String dbURI = "local:" + getDatabasePath();
		return new ODatabaseDocumentTx(dbURI).open("admin", "admin");
	}
	
	/**
	 * Return the database path
	 * 
	 */
	public String getDatabasePath() {
		return project.getLocation() + "/" + DB_PATH;
	}
	
	/**
	 * Insert a new record from a JSON document.
	 * 
	 * @param json
	 */
	public void insertDocument(String json) {
		final ODocument doc = new ODocument();
		doc.fromJSON(json);
		doc.save();
	}
	
	/**
	 * Get documents for given record.
	 * 
	 * @param record
	 */
	public void documentsForRecord() {
		
	}
}
