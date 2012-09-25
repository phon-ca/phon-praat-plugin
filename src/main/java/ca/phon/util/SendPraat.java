/*
 * Phon - An open source tool for research in phonology.
 * Copyright (C) 2008 The Phon Project, Memorial University <http://phon.ling.mun.ca>
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
package ca.phon.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.phon.system.logger.PhonLogger;
import ca.phon.util.PhonUtilities;

/**
 * Java native interface to sendpraat.
 * 
 */
public class SendPraat {
	
	private final static String _PRAAT_EXE_ = "/Applications/Praat.app/Contents/MacOS/Praat";

	private final static String _PHONNATIVE_LIB_NAME = "plugins/phon_praat_plugin/lib/libsendpraat.jnilib";
	
	// assume library is there unless we get an exception
	private static boolean libraryFound = true;
	
	static {
		if(PhonUtilities.isMacOs()) {
			try {
				File libFile = new File(_PHONNATIVE_LIB_NAME);
				if(libFile.exists()) {
					System.load(libFile.getAbsolutePath());
					libraryFound = true;
				} else {
					PhonLogger.severe("[SendPraat] File not found: " + libFile.getAbsolutePath());
					libraryFound = false;
				}
			} catch (UnsatisfiedLinkError ex) {
				PhonLogger.warning("Can't load native library: " + ex.toString());
				libraryFound = false;
			}
		}
	}
	
	/**
	 * Send a script to the praat application.
	 * 
	 * @param script
	 * @return the error message on error or <CODE>null</CODE>
	 * if no error occurred.
	 */
	public static native String sendPraat(String script);

	/**
	 * Send a script to praat in batch mode (no-ui)
	 * @param scrpt
	 * @return the output from Praat
	 */
	public static String sendPraatInBatch(String script) {
		List<String> cmd = new ArrayList<String>();
		cmd.add(_PRAAT_EXE_);
		cmd.add(script);

		String output = new String();

		ProcessBuilder pb = new ProcessBuilder(cmd);
		try {
			Process praatProcess = pb.start();

			InputStream stdout = praatProcess.getInputStream();
			InputStream stderr = praatProcess.getErrorStream();

			BufferedReader in = new BufferedReader(new InputStreamReader(stdout));
			String line = null;
			while((line = in.readLine()) != null) {
				output += line + "\n";
			}
			in.close();
		} catch (IOException ex) {
			Logger.getLogger(SendPraat.class.getName()).log(Level.SEVERE, null, ex);
		}

		return output;
	}

}
