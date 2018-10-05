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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

//import ca.phon.jsendpraat.SendPraat;
import ca.phon.plugin.IPluginEntryPoint;
import ca.phon.plugin.PhonPlugin;

/**
 * Plugin entry point for sending scripts to Praat.
 * 
 * The pluginStart method expects the following arguments:
 * 
 *  * script -> String: the script contents as a string
 *    or 
 *    scriptFile -> File: the script contents as a file
 *  * batchMode -> boolean: run script in batch mode. Optional, default = true.
 *  * output -> OutputStream: output stream for Praat return data
 *    or
 *    outputFile -> File: output file for Praat return data
 */
@PhonPlugin(name="phon-textgrid-plugin",version="0.1",author="Greg J. Hedlund")
public class SendPraatEP implements IPluginEntryPoint {
	
	private static final Logger LOGGER = Logger
			.getLogger(SendPraatEP.class.getName());
	
	private final static String SCRIPT_ARG = "script";
	private final static String SCRIPTFILE_ARG = "scriptFile";
	
	private final static String BATCHMODE_ARG = "batchMode";
	
	private final static String OUTPUT_ARG = "output";
	private final static String OUTPUTFILE_ARG = "outputFile";

	@Override
	public void pluginStart(Map<String, Object> args) {
		/* 
		 * Check args
		 */
		if(args.get(SCRIPT_ARG) == null && args.get(SCRIPTFILE_ARG) ==  null) {
			throw new IllegalArgumentException("[SendPraat] No script given");
		}
		String script = (String)args.get(SCRIPT_ARG);
		
		if(args.get(OUTPUT_ARG) == null && args.get(OUTPUTFILE_ARG) == null) {
			throw new IllegalArgumentException("[SendPraat] No output given");
		}
		OutputStream os = (OutputStream)args.get(OUTPUT_ARG);
		
		boolean runInBatch = true;
		if(args.get(BATCHMODE_ARG) != null) {
			runInBatch = (Boolean)args.get(BATCHMODE_ARG);
		}
		
		String retVal = "";
//		if(runInBatch)
//			retVal = SendPraat.sendPraatInBatch(script);
//		else
//			retVal = SendPraat.sendPraat(script);
		
		if(retVal != null) {
			try {
				os.write(retVal.getBytes());
				os.flush();
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
		}
	}

	@Override
	public String getName() {
		return "SendPraat";
	}
}
