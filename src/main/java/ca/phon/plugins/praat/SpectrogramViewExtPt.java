package ca.phon.plugins.praat;

import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisEditorView;
import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisTier;
import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;
import ca.phon.plugin.PhonPlugin;

@PhonPlugin(name="Spectrogram",version="0.1",minPhonVersion="1.7.0")
public class SpectrogramViewExtPt implements IPluginExtensionPoint<SpeechAnalysisTier> {

	@Override
	public Class<?> getExtensionType() {
		return SpeechAnalysisTier.class;
	}

	@Override
	public IPluginExtensionFactory<SpeechAnalysisTier> getFactory() {
		return factory;
	}

	private final IPluginExtensionFactory<SpeechAnalysisTier> factory = new IPluginExtensionFactory<SpeechAnalysisTier>() {
		
		@Override
		public SpeechAnalysisTier createObject(Object... args) {
			final SpeechAnalysisEditorView parent = SpeechAnalysisEditorView.class.cast(args[0]);
			return new SpectrogramViewer(parent);
		}
		
	};
	
}
