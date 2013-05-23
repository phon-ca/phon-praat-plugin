package ca.phon.plugins.praat;

import ca.phon.gui.recordeditor.RecordEditorView;
import ca.phon.system.plugin.IPluginExtensionFactory;
import ca.phon.system.plugin.IPluginExtensionPoint;
import ca.phon.system.plugin.PhonPlugin;

@PhonPlugin(name="Text Grid",version="0.1")
public class TextGridViewExtPt implements IPluginExtensionPoint<RecordEditorView> {

	@Override
	public Class<RecordEditorView> getExtensionType() {
		return RecordEditorView.class;
	}

	@Override
	public IPluginExtensionFactory<RecordEditorView> getFactory() {
		return new TextGridViewExtPtFactory();
	}
	
	/**
	 * Internal factory.
	 */
	private class TextGridViewExtPtFactory implements IPluginExtensionFactory<RecordEditorView> {

		@Override
		public RecordEditorView createObject(Object... args) {
			return new TextGridPanel();
		}
		
	}

}
