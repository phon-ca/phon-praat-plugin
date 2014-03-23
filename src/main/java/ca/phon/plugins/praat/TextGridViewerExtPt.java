package ca.phon.plugins.praat;

import ca.phon.app.session.editor.view.waveform.WaveformEditorView;
import ca.phon.app.session.editor.view.waveform.WaveformTier;
import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;

@ca.phon.plugin.PhonPlugin(name="Text Grid",version="0.1",minPhonVersion="1.6.2")
public class TextGridViewerExtPt implements IPluginExtensionPoint<WaveformTier> {

	@Override
	public Class<?> getExtensionType() {
		return WaveformTier.class;
	}

	@Override
	public IPluginExtensionFactory<WaveformTier> getFactory() {
		return new TextGridViewerFactory();
	}

	private class TextGridViewerFactory implements IPluginExtensionFactory<WaveformTier> {

		@Override
		public WaveformTier createObject(Object... arg0) {
			final WaveformEditorView parent = WaveformEditorView.class.cast(arg0[0]);
			return new TextGridViewer(parent);
		}
		
	}
}
