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

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;

import ca.phon.app.modules.*;
import ca.phon.app.project.*;
import ca.phon.app.session.editor.*;
import ca.phon.plugin.*;
import ca.phon.plugins.praat.export.*;
import ca.phon.project.*;
import ca.phon.session.*;

@PhonPlugin(name="phon-textgrid-plugin",version="0.1",author="Greg J. Hedlund")
public class GenerateTextGridsEP implements IPluginEntryPoint {
	
	private final static Logger LOGGER = Logger.getLogger(GenerateTextGridsEP.class.getName());

	private final static String EP_NAME = "GenerateTextGrids";
	
	@Override
	public String getName() {
		return EP_NAME;
	}

	@Override
	public void pluginStart(Map<String, Object> arg0) {
		final EntryPointArgs args = new EntryPointArgs(arg0);
		final SessionEditor editor = (SessionEditor)args.get("editor");
		Project project = args.getProject();
		if(project == null && args.get("projectWindow") != null) {
			final ProjectWindow pw = (ProjectWindow)args.get("projectWindow");
			project = pw.getProject();
		}
		Session session;
		try {
			session = args.getSession();
			if(session == null && editor != null) {
				project = editor.getProject();
				session = editor.getSession();
			}
			
			TextGridExportWizard wizard = null;
			if(session != null) {
				wizard = new TextGridExportWizard(project, session);
			} else {
				wizard = new TextGridExportWizard(project);
			}
			wizard.setSize(500, 550);
			wizard.centerWindow();
			if(editor != null)
				wizard.setLocationRelativeTo(editor);
			wizard.showWizard();
		} catch (IOException e) {
			Toolkit.getDefaultToolkit().beep();
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

}
