package ca.phon.plugins.praat;

import java.io.Serializable;


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
