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
import ca.phon.plugin.IPluginEntryPoint;
import ca.phon.plugin.PhonPlugin;
import ca.phon.plugins.praat.importer.TextGridImportWizard;
import ca.phon.project.Project;
import ca.phon.ui.CommonModuleFrame;

@PhonPlugin(name=PluginInfo.PLUGIN_NAME,version=PluginInfo.PLUGIN_VERSION,author=PluginInfo.PLUGIN_AUTHOR)
public class ImportTextGridsEP implements IPluginEntryPoint {

	private final static String EP_NAME = "ImportTextGrids";
	
	@Override
	public String getName() {
		return EP_NAME;
	}

	@Override
	public void pluginStart(Map<String, Object> args) {
		final EntryPointArgs epArgs = new EntryPointArgs(args);
		final ProjectWindow projectWindow = (ProjectWindow)epArgs.get("projectWindow");
		if(projectWindow == null) return;
		
		final Project project = projectWindow.getProject();
		if(project == null) return;
		
		final TextGridImportWizard wizard = new TextGridImportWizard(project);
		wizard.setSize(500, 550);
		wizard.centerWindow();
		wizard.setLocationRelativeTo(CommonModuleFrame.getCurrentFrame());
		wizard.setVisible(true);
	}

}
