package ca.phon.plugins.praat;

import ca.phon.gui.recordeditor.SegmentPanelTier;
import ca.phon.system.plugin.IPluginExtensionFactory;
import ca.phon.system.plugin.IPluginExtensionPoint;
import ca.phon.system.plugin.PhonPlugin;

@PhonPlugin(name="Text Grid",version="0.1",minPhonVersion="1.6.2")
public class TextGridViewerExtPt implements IPluginExtensionPoint<SegmentPanelTier> {

	@Override
	public Class<?> getExtensionType() {
		return SegmentPanelTier.class;
	}

	@Override
	public IPluginExtensionFactory<SegmentPanelTier> getFactory() {
		return new TextGridViewerFactory();
	}

	private class TextGridViewerFactory implements IPluginExtensionFactory<SegmentPanelTier> {

		@Override
		public SegmentPanelTier createObject(Object... arg0) {
			return new TextGridViewer();
		}
		
	}
}
