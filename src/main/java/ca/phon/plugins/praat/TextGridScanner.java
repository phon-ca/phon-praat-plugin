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
import java.io.FileFilter;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.sys.Data;
import ca.hedlund.jpraat.binding.sys.MelderFile;
import ca.hedlund.jpraat.exceptions.PraatException;

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
				tg = Data.readFromFile(TextGrid.class, MelderFile.fromPath(f.getAbsolutePath()));
				for(long i = 0; i < tg.numberOfTiers(); i++) {
					final Function tgTier = tg.tier(i);
					retVal.add(tgTier.getName().toString());
				}
			} catch (PraatException e) {
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
