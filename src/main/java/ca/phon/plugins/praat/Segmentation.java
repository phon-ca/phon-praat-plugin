package ca.phon.plugins.praat;

public enum Segmentation {
	TIER("Tier"), // full tier in one interval
	GROUP("Group"), // each group in an interval
	WORD("Word"), // each word in an inverval
	SYLLABLE("Syllable"), // (IPA-only) each syllable in an interval
	PHONE("Phone"); // (IPA-only) each phone in an interval
	
	private String tierSuffix;
	
	private Segmentation(String suffix) {
		this.tierSuffix = suffix;
	}
	
	@Override
	public String toString() {
		return tierSuffix;
	}
}