/*
 * Copyright (C) 2012-2018 Gregory Hedlund
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *    http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.phon.plugins.praat;

import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisEditorView;
import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisTier;
import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;

@ca.phon.plugin.PhonPlugin(name="Text Grid",version="0.1",minPhonVersion="1.6.2")
public class TextGridSATierExtPt implements IPluginExtensionPoint<SpeechAnalysisTier> {

	@Override
	public Class<?> getExtensionType() {
		return SpeechAnalysisTier.class;
	}

	@Override
	public IPluginExtensionFactory<SpeechAnalysisTier> getFactory() {
		return (Object... args) -> { 
			if(args.length < 0)
				throw new IllegalArgumentException();
			if(!(args[0] instanceof SpeechAnalysisEditorView))
				throw new IllegalArgumentException();
			
			final SpeechAnalysisEditorView parent = SpeechAnalysisEditorView.class.cast(args[0]);
			return new TextGridSpeechAnalysisTier(parent);
		};
	}
	
}
