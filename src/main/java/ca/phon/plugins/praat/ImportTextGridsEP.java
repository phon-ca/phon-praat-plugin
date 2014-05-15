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
