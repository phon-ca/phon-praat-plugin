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
