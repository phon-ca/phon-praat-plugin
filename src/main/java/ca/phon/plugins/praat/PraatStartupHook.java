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

import java.util.logging.Level;
import java.util.logging.Logger;

import ca.hedlund.jpraat.binding.Praat;
import ca.hedlund.jpraat.binding.sys.PraatVersion;
import ca.phon.app.hooks.PhonStartupHook;
import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;
import ca.phon.plugin.PluginException;
import ca.phon.util.PrefHelper;

import com.sun.jna.NativeLibrary;

/**
 * Init Praat library on startup.
 *
 */
public class PraatStartupHook implements PhonStartupHook, IPluginExtensionPoint<PhonStartupHook> {

	private static final Logger LOGGER = Logger
			.getLogger(PraatStartupHook.class.getName());
	
	public static final String PRAAT_SEARCH_FOLDER = PraatStartupHook.class.getName() + 
			".praatSearchFolder";
	
	@Override
	public void startup() throws PluginException {
		
		try {
			LOGGER.info("Initializing Praat library");
			
			final String praatSearchFolder = 
					PrefHelper.get(PRAAT_SEARCH_FOLDER, null);
			if(praatSearchFolder != null) {
				NativeLibrary.addSearchPath("praat", praatSearchFolder);
			}
			
			Praat.INSTANCE.praat_lib_init();
			
			final PraatVersion praatVersion = PraatVersion.getVersion();
			// print version information to log
			final StringBuilder sb = new StringBuilder();
			sb.append("Praat version: ").append(praatVersion.versionStr);
			sb.append(" ").append(praatVersion.day).append('-').append(praatVersion.month).append('-').append(praatVersion.year);
			LOGGER.info(sb.toString());
		} catch (UnsatisfiedLinkError e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	@Override
	public Class<?> getExtensionType() {
		return PhonStartupHook.class;
	}

	@Override
	public IPluginExtensionFactory<PhonStartupHook> getFactory() {
		return (args) -> this;
	}
	
}
