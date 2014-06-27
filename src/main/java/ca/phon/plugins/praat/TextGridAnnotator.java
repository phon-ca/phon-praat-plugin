package ca.phon.plugins.praat;

import java.util.logging.Logger;

import ca.phon.extensions.IExtendable;
import ca.phon.ipa.IPAElement;
import ca.phon.ipa.IPATranscript;
import ca.phon.orthography.OrthoElement;
import ca.phon.orthography.OrthoWordExtractor;
import ca.phon.orthography.Orthography;
import ca.phon.session.Record;
import ca.phon.session.Tier;
import ca.phon.syllable.SyllableConstituentType;
import ca.phon.textgrid.TextGrid;
import ca.phon.textgrid.TextGridInterval;
import ca.phon.textgrid.TextGridTier;

public class TextGridAnnotator {
	
	private static final Logger LOGGER = Logger
			.getLogger(TextGridAnnotator.class.getName());
	
	public void annotateRecord(TextGrid textGrid, Record record) {
		annotateTier(textGrid, record.getOrthography());
		annotateTier(textGrid, record.getIPAActual());
		annotateTier(textGrid, record.getIPATarget());
	}

	@SuppressWarnings("unchecked")
	public void annotateTier(TextGrid textGrid, Tier<?> tier) {
		if(tier.getDeclaredType() == Orthography.class) {
			annotateOrthographyTier(textGrid, (Tier<Orthography>)tier);
		} else if(tier.getDeclaredType() == IPATranscript.class) {
			annotateIPATier(textGrid, (Tier<IPATranscript>)tier);
		} else if(tier.getDeclaredType().isAssignableFrom(IExtendable.class)) {
			
		} else {
			LOGGER.warning("Cannot annotate tier of type " + tier.getDeclaredType().toString());
		}
	}
	
	private void annotateOrthographyTier(TextGrid textGrid, Tier<Orthography> orthoTier) {
		// full tier
		final TextGridTier fullOrthoTier = findTextGridTier(textGrid, orthoTier, "Tier");
		if(fullOrthoTier != null) {
			final int fullTierIntervalIdx = findIntervalForText(fullOrthoTier, orthoTier.toString(), 0);
			if(fullTierIntervalIdx >= 0) {
				orthoTier.putExtension(TextGridInterval.class, fullOrthoTier.getIntervalAt(fullTierIntervalIdx));
			}
		}
		
		// groups
		final TextGridTier orthoGroupTier = findTextGridTier(textGrid, orthoTier, "Group");
		if(orthoGroupTier != null) {
			int gidx = 0;
			for(int i = 0; i < orthoTier.numberOfGroups(); i++) {
				final Orthography ortho = orthoTier.getGroup(i);
				final int gTgi = findIntervalForText(orthoGroupTier, ortho.toString(), gidx);
				if(gTgi >= 0) {
					ortho.putExtension(TextGridInterval.class, orthoGroupTier.getIntervalAt(gTgi));
					gidx = gTgi + 1;
				} else {
					LOGGER.warning(
							String.format("Unable to find interval for group '%s'", ortho.toString()));
					break;
				}
			}
		}
		
		// words
		final TextGridTier orthoWordTier = findTextGridTier(textGrid, orthoTier, "Word");
		if(orthoWordTier != null) {
			int widx = 0;
			for(int i = 0; i < orthoTier.numberOfGroups(); i++) {
				final Orthography ortho = orthoTier.getGroup(i);
				final OrthoWordExtractor wordExtractor = new OrthoWordExtractor();
				ortho.accept(wordExtractor);
				for(OrthoElement ele:wordExtractor.getWordList()) {
					final int wTgi = findIntervalForText(orthoWordTier, ele.toString(), widx);
					if(wTgi >= 0) {
						ele.putExtension(TextGridInterval.class, orthoWordTier.getIntervalAt(wTgi));
						widx = wTgi+1;
					} else {
						LOGGER.warning(
								String.format("Unable to find interval for word '%s'", ele.toString()));
					}
				}
			}
		}
	}
	
	private void annotateIPATier(TextGrid textGrid, Tier<IPATranscript> ipaTier) {
		// full tier
		final TextGridTier fullOrthoTier = findTextGridTier(textGrid, ipaTier, "Tier");
		if(fullOrthoTier != null) {
			final int fullTierIntervalIdx = findIntervalForText(fullOrthoTier, ipaTier.toString(), 0);
			if(fullTierIntervalIdx >= 0) {
				ipaTier.putExtension(TextGridInterval.class, fullOrthoTier.getIntervalAt(fullTierIntervalIdx));
			}
		}
		

		// phones
		final TextGridTier ipaPhoneTier = findTextGridTier(textGrid, ipaTier, "Phone");
		if(ipaPhoneTier != null) {
			int pidx = 0;
			for(int i = 0; i < ipaTier.numberOfGroups(); i++) {
				final IPATranscript ipaGrp = ipaTier.getGroup(i);
				for(IPAElement ele:ipaGrp.removePunctuation()) {
					final int eleIdx = ipaGrp.indexOf(ele);
					String txt = ele.getText();
					if(eleIdx > 0 && ipaGrp.elementAt(eleIdx-1).getScType() == SyllableConstituentType.SYLLABLESTRESSMARKER) {
						txt = ipaGrp.elementAt(eleIdx-1).getText() + txt;
					}
					final int pTgi = findIntervalForText(ipaPhoneTier, txt, pidx);
					if(pTgi >= 0) {
						ele.putExtension(TextGridInterval.class, ipaPhoneTier.getIntervalAt(pTgi));
						pidx = pTgi + 1;
					} else {
						LOGGER.warning(String.format("Unable to find interval for element '%s'", ele.toString()));
						break;
					}
				}
			}
		}
		
		// groups
		final TextGridTier ipaGroupTier = findTextGridTier(textGrid, ipaTier, "Group");
		if(ipaGroupTier != null) {
			int gidx = 0;
			for(int i = 0; i < ipaTier.numberOfGroups(); i++) {
				final IPATranscript ipaGrp = ipaTier.getGroup(i);
				final int gTgi = findIntervalForText(ipaGroupTier, ipaGrp.toString(), gidx);
				if(gTgi >= 0) {
					ipaGrp.putExtension(TextGridInterval.class, ipaGroupTier.getIntervalAt(gTgi));
					gidx = gTgi + 1;
				} else {
					LOGGER.warning(
							String.format("Unable to find interval for group '%s'", ipaGrp.toString()));
					break;
				}
			}
		} else {
			for(IPATranscript ipa:ipaTier) {
				inferInterval(ipa);
			}
		}

		// words
		final TextGridTier ipaWordTier = findTextGridTier(textGrid, ipaTier, "Word");
		if(ipaWordTier != null) {
			int widx = 0;
			for(int i = 0; i < ipaTier.numberOfGroups(); i++) {
				final IPATranscript ipaGrp = ipaTier.getGroup(i);
				for(IPATranscript word:ipaGrp.words()) {
					final int wTgi = findIntervalForText(ipaWordTier, word.toString(), widx);
					if(wTgi >= 0) {
						word.putExtension(TextGridInterval.class, ipaWordTier.getIntervalAt(wTgi));
						widx = wTgi + 1;
					} else {
						LOGGER.warning(
								String.format("Unable to find interval for word '%s'", ipaGrp.toString()));
					}
				}
			}
		} else {
			// infer intervals from phone objects
			for(IPATranscript ipaGrp:ipaTier) {
				for(IPATranscript word:ipaGrp.words()) {
					inferInterval(word);
				}
			}
		}
		
		// syllables
		final TextGridTier ipaSyllTier = findTextGridTier(textGrid, ipaTier, "Syllable");
		if(ipaSyllTier != null) {
			int sidx = 0;
			for(int i = 0; i < ipaTier.numberOfGroups(); i++) {
				final IPATranscript ipaGrp = ipaTier.getGroup(i);
				for(IPATranscript syll:ipaGrp.syllables()) {
					final int sTgi = findIntervalForText(ipaSyllTier, syll.toString(), sidx);
					if(sTgi >= 0) {
						syll.putExtension(TextGridInterval.class, ipaSyllTier.getIntervalAt(sTgi));
						sidx = sTgi + 1;
					} else {
						LOGGER.warning(
								String.format("Unable to find interval for syllablle '%s'", ipaGrp.toString()));
					}
				}
			}
		} else {
			// infer intervals from phone objects
			for(IPATranscript ipaGrp:ipaTier) {
				for(IPATranscript syll:ipaGrp.syllables()) {
					inferInterval(syll);
				}
			}
		}
	}
	
	private void inferInterval(IPATranscript ipa) {
		if(ipa.length() == 0) return;
		
		int firstIdx = (ipa.elementAt(0).getScType() == SyllableConstituentType.SYLLABLESTRESSMARKER ? 1 : 0);
		if(firstIdx >= ipa.length()) return;
		
		final TextGridInterval i1 = ipa.elementAt(firstIdx).getExtension(TextGridInterval.class);
		final TextGridInterval i2 = ipa.elementAt(ipa.length() - 1).getExtension(TextGridInterval.class);
		
		if(i1 != null && i2 != null) {
			final TextGridInterval inferred = new TextGridInterval(ipa.toString(), i1.getStart(), i2.getEnd());
			ipa.putExtension(TextGridInterval.class, inferred);
		}
	}
	
	private TextGridTier findTextGridTier(TextGrid tg, Tier<?> tier, String classifier) {
		final String tierName = tier.getName() + ": " + classifier;
		for(int i = 0; i < tg.getNumberOfTiers(); i++) {
			final TextGridTier tgTier = tg.getTier(i);
			if(tgTier.getTierName().equals(tierName)) {
				return tgTier;
			}
		}
		return null;
	}
	
	private int findIntervalForText(TextGridTier tgTier, String txt, int fromIndex) {
		for(int i = fromIndex; i < tgTier.getNumberOfIntervals(); i++) {
			final TextGridInterval interval = tgTier.getIntervalAt(i);
			if(interval.getLabel().trim().equals(txt)) {
				return i;
			}
		}
		return -1;
	}
}
