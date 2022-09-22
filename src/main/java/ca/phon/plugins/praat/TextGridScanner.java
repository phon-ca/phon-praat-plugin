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

import ca.hedlund.jpraat.binding.fon.*;
import ca.hedlund.jpraat.binding.sys.*;
import ca.hedlund.jpraat.exceptions.PraatException;

import java.io.*;
import java.util.*;
import java.util.logging.*;

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
				tg = Daata.readFromFile(TextGrid.class, MelderFile.fromPath(f.getAbsolutePath()));
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
