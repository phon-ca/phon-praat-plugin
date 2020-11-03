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
package ca.phon.plugins.praat.export;

import java.io.*;

import ca.phon.plugins.praat.*;


/**
 * Container for exporting a Phon tier to a TextGrid tier
 * with specified segmentation type and tier name.
 */
public class TextGridExportEntry implements Serializable {
	
	private static final long serialVersionUID = -9106712182602896260L;

	private String phonTier;
	
	private Segmentation exportType;
	
	private String textGridTier;
	
	public TextGridExportEntry() {
		super();
	}

	public TextGridExportEntry(String phonTier, Segmentation exportType,
			String textGridTier) {
		super();
		this.phonTier = phonTier;
		this.exportType = exportType;
		this.textGridTier = textGridTier;
	}

	public String getPhonTier() {
		return phonTier;
	}

	public void setPhonTier(String phonTier) {
		this.phonTier = phonTier;
	}

	public Segmentation getExportType() {
		return exportType;
	}

	public void setExportType(Segmentation exportType) {
		this.exportType = exportType;
	}

	public String getTextGridTier() {
		return textGridTier;
	}

	public void setTextGridTier(String textGridTier) {
		this.textGridTier = textGridTier;
	}
	
}
