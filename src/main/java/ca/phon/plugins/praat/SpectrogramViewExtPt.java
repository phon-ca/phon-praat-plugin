package ca.phon.plugins.praat;

import ca.phon.app.session.editor.view.waveform.WaveformEditorView;
import ca.phon.app.session.editor.view.waveform.WaveformTier;
import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;
import ca.phon.plugin.PhonPlugin;

@PhonPlugin(name="Spectrogram",version="0.1",minPhonVersion="1.7.0")
public class SpectrogramViewExtPt implements IPluginExtensionPoint<WaveformTier> {

	@Override
	public Class<?> getExtensionType() {
		return WaveformTier.class;
	}

	@Override
	public IPluginExtensionFactory<WaveformTier> getFactory() {
		return factory;
	}

	private final IPluginExtensionFactory<WaveformTier> factory = new IPluginExtensionFactory<WaveformTier>() {
		
		@Override
		public WaveformTier createObject(Object... args) {
			final WaveformEditorView parent = WaveformEditorView.class.cast(args[0]);
			return new SpectrogramViewer(parent);
		}
		
	};
	
}
