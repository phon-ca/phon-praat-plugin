package ca.phon.plugins.praat;

import java.util.Map;

import ca.phon.application.project.IPhonProject;
import ca.phon.application.transcript.ITranscript;
import ca.phon.gui.recordeditor.RecordEditor;
import ca.phon.system.plugin.IPluginEntryPoint;
import ca.phon.system.plugin.PhonPlugin;

@PhonPlugin(name="phon-textgrid-plugin",version="0.1",author="Greg J. Hedlund")
public class GenerateTextGridsEP implements IPluginEntryPoint {

	private final static String EP_NAME = "GenerateTextGrids";
	
	@Override
	public String getName() {
		return EP_NAME;
	}

	@Override
	public void pluginStart(Map<String, Object> arg0) {
		final RecordEditor editor = (RecordEditor)arg0.get("editor");
		if(editor == null) 
			throw new NullPointerException(EP_NAME + ": editor");
		final IPhonProject project = editor.getProject();
		final ITranscript session = editor.getModel().getSession();
		
		final TextGridExportWizard wizard = new TextGridExportWizard(project, session);
		wizard.setSize(500, 550);
		wizard.centerWindow();
		wizard.setLocationRelativeTo(editor);
		wizard.setVisible(true);
	}

}
