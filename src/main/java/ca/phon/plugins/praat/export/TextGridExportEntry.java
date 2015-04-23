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
package ca.phon.plugins.praat.export;

import java.io.Serializable;

import ca.phon.plugins.praat.Segmentation;


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
