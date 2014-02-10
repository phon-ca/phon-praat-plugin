package ca.phon.plugins.praat;

import java.util.Map;

import ca.phon.app.session.editor.SessionEditor;
import ca.phon.plugin.IPluginEntryPoint;
import ca.phon.plugin.PhonPlugin;
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
		final SessionEditor editor = (SessionEditor)arg0.get("editor");
		if(editor == null) 
			throw new NullPointerException(EP_NAME + ": editor");
		final Project project = editor.getProject();
		final Session session = editor.getSession();
		
		final TextGridExportWizard wizard = new TextGridExportWizard(project, session);
		wizard.setSize(500, 550);
		wizard.centerWindow();
		wizard.setLocationRelativeTo(editor);
		wizard.setVisible(true);
	}

}
