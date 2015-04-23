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

import java.util.Map;

import ca.phon.app.modules.EntryPointArgs;
import ca.phon.app.project.ProjectWindow;
import ca.phon.app.session.editor.SessionEditor;
import ca.phon.plugin.IPluginEntryPoint;
import ca.phon.plugin.PhonPlugin;
import ca.phon.plugins.praat.export.TextGridExportWizard;
import ca.phon.project.Project;
import ca.phon.session.Session;

@PhonPlugin(name="phon-textgrid-plugin",version="0.1",author="Greg J. Hedlund")
public class GenerateTextGridsEP implements IPluginEntryPoint {

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
		Session session = args.getSession();
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
	}

}
