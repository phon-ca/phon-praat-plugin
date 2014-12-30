package ca.phon.plugins.praat;

import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisEditorView;
import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisTier;
import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;

@ca.phon.plugin.PhonPlugin(name="Text Grid",version="0.1",minPhonVersion="1.6.2")
public class TextGridViewerExtPt implements IPluginExtensionPoint<SpeechAnalysisTier> {

	@Override
	public Class<?> getExtensionType() {
		return SpeechAnalysisTier.class;
	}

	@Override
	public IPluginExtensionFactory<SpeechAnalysisTier> getFactory() {
		return new TextGridViewerFactory();
	}

	private class TextGridViewerFactory implements IPluginExtensionFactory<SpeechAnalysisTier> {

		@Override
		public SpeechAnalysisTier createObject(Object... arg0) {
			final SpeechAnalysisEditorView parent = SpeechAnalysisEditorView.class.cast(arg0[0]);
			return new TextGridViewer(parent);
		}
		
	}
}
