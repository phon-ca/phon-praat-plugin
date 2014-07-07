package ca.phon.plugins.praat;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.phon.textgrid.TextGrid;
import ca.phon.textgrid.TextGridTier;

/**
 * Utilties methods for scanning a directory of .TextGrid files.
 *
 */
public class TextGridScanner {
	
	private final static Logger LOGGER = Logger
			.getLogger(TextGridScanner.class.getName());
	
	private final File textGridFolder;
	
	public TextGridScanner(String path) {
		this(new File(path));
	}
	
	public TextGridScanner(File folder) {
		super();
		this.textGridFolder = folder;
	}
	
	/**
	 * Get a set of all tiers defined in the text grid folder
	 */
	public Set<String> collectTierNames() {
		final Set<String> retVal = new HashSet<String>();
		for(File f:textGridFolder.listFiles(tgFilter)) {
			TextGrid tg = null;
			try {
				tg = TextGridManager.loadTextGrid(f);
				for(int i = 0; i < tg.getNumberOfTiers(); i++) {
					final TextGridTier tgTier = tg.getTier(i);
					retVal.add(tgTier.getTierName());
				}
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
		}
		return retVal;
	}
	
	private final FileFilter tgFilter = new FileFilter() {
		
		@Override
		public boolean accept(File pathname) {
			return pathname.getName().endsWith(".TextGrid");
		}
		
	};

}
