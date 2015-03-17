package ca.phon.plugins.praat;

import java.util.logging.Level;
import java.util.logging.Logger;

import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.IntervalTier;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.extensions.IExtendable;
import ca.phon.ipa.IPAElement;
import ca.phon.ipa.IPATranscript;
import ca.phon.orthography.OrthoElement;
import ca.phon.orthography.OrthoWordExtractor;
import ca.phon.orthography.Orthography;
import ca.phon.session.Record;
import ca.phon.session.Tier;
import ca.phon.syllable.SyllableConstituentType;

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
		final IntervalTier fullOrthoTier = findTextGridIntervalTier(textGrid, orthoTier, "Tier");
		if(fullOrthoTier != null) {
			final long fullTierIntervalIdx = findIntervalForText(fullOrthoTier, orthoTier.toString(), 0);
			if(fullTierIntervalIdx >= 0) {
				orthoTier.putExtension(TextInterval.class, fullOrthoTier.interval(fullTierIntervalIdx));
			}
		}
		
		// groups
		final IntervalTier orthoGroupTier = findTextGridIntervalTier(textGrid, orthoTier, "Group");
		if(orthoGroupTier != null) {
			long gidx = 0;
			for(int i = 0; i < orthoTier.numberOfGroups(); i++) {
				final Orthography ortho = orthoTier.getGroup(i);
				final long gTgi = findIntervalForText(orthoGroupTier, ortho.toString(), gidx);
				if(gTgi >= 0) {
					ortho.putExtension(TextInterval.class, orthoGroupTier.interval(gTgi));
					gidx = gTgi + 1;
				} else {
					LOGGER.warning(
							String.format("Unable to find interval for group '%s'", ortho.toString()));
					break;
				}
			}
		}
		
		// words
		final IntervalTier orthoWordTier = findTextGridIntervalTier(textGrid, orthoTier, "Word");
		if(orthoWordTier != null) {
			long widx = 0;
			for(int i = 0; i < orthoTier.numberOfGroups(); i++) {
				final Orthography ortho = orthoTier.getGroup(i);
				final OrthoWordExtractor wordExtractor = new OrthoWordExtractor();
				ortho.accept(wordExtractor);
				for(OrthoElement ele:wordExtractor.getWordList()) {
					final long wTgi = findIntervalForText(orthoWordTier, ele.toString(), widx);
					if(wTgi >= 0) {
						ele.putExtension(TextInterval.class, orthoWordTier.interval(wTgi));
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
		final IntervalTier fullOrthoTier = findTextGridIntervalTier(textGrid, ipaTier, "Tier");
		if(fullOrthoTier != null) {
			final long fullTierIntervalIdx = findIntervalForText(fullOrthoTier, ipaTier.toString(), 0);
			if(fullTierIntervalIdx >= 0) {
				ipaTier.putExtension(TextInterval.class, fullOrthoTier.interval(fullTierIntervalIdx));
			}
		}
		

		// phones
		final IntervalTier ipaPhoneTier = findTextGridIntervalTier(textGrid, ipaTier, "Phone");
		if(ipaPhoneTier != null) {
			long pidx = 0;
			for(int i = 0; i < ipaTier.numberOfGroups(); i++) {
				final IPATranscript ipaGrp = ipaTier.getGroup(i);
				for(IPAElement ele:ipaGrp.removePunctuation()) {
					final int eleIdx = ipaGrp.indexOf(ele);
					String txt = ele.getText();
					if(eleIdx > 0 && ipaGrp.elementAt(eleIdx-1).getScType() == SyllableConstituentType.SYLLABLESTRESSMARKER) {
						txt = ipaGrp.elementAt(eleIdx-1).getText() + txt;
					}
					final long pTgi = findIntervalForText(ipaPhoneTier, txt, pidx);
					if(pTgi >= 0) {
						ele.putExtension(TextInterval.class, ipaPhoneTier.interval(pTgi));
						pidx = pTgi + 1;
					} else {
						LOGGER.warning(String.format("Unable to find interval for element '%s'", ele.toString()));
						break;
					}
				}
			}
		}
		
		// groups
		final IntervalTier ipaGroupTier = findTextGridIntervalTier(textGrid, ipaTier, "Group");
		if(ipaGroupTier != null) {
			long gidx = 0;
			for(int i = 0; i < ipaTier.numberOfGroups(); i++) {
				final IPATranscript ipaGrp = ipaTier.getGroup(i);
				final long gTgi = findIntervalForText(ipaGroupTier, ipaGrp.toString(), gidx);
				if(gTgi >= 0) {
					ipaGrp.putExtension(TextInterval.class, ipaGroupTier.interval(gTgi));
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
		final IntervalTier ipaWordTier = findTextGridIntervalTier(textGrid, ipaTier, "Word");
		if(ipaWordTier != null) {
			long widx = 0;
			for(int i = 0; i < ipaTier.numberOfGroups(); i++) {
				final IPATranscript ipaGrp = ipaTier.getGroup(i);
				for(IPATranscript word:ipaGrp.words()) {
					final long wTgi = findIntervalForText(ipaWordTier, word.toString(), widx);
					if(wTgi >= 0) {
						word.putExtension(TextInterval.class, ipaWordTier.interval(wTgi));
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
		final IntervalTier ipaSyllTier = findTextGridIntervalTier(textGrid, ipaTier, "Syllable");
		if(ipaSyllTier != null) {
			long sidx = 0;
			for(int i = 0; i < ipaTier.numberOfGroups(); i++) {
				final IPATranscript ipaGrp = ipaTier.getGroup(i);
				for(IPATranscript syll:ipaGrp.syllables()) {
					final long sTgi = findIntervalForText(ipaSyllTier, syll.toString(), sidx);
					if(sTgi >= 0) {
						syll.putExtension(TextInterval.class, ipaSyllTier.interval(sTgi));
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
		
		final TextInterval i1 = ipa.elementAt(firstIdx).getExtension(TextInterval.class);
		final TextInterval i2 = ipa.elementAt(ipa.length() - 1).getExtension(TextInterval.class);
		
		if(i1 != null && i2 != null) {
			try {
				final TextInterval inferred = TextInterval.create(i1.getXmin(), i2.getXmax(), ipa.toString());
				ipa.putExtension(TextInterval.class, inferred);
			}  catch (PraatException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
		}
	}
	
	private IntervalTier findTextGridIntervalTier(TextGrid tg, Tier<?> tier, String classifier) {
		final String tierName = tier.getName() + ": " + classifier;
		for(long i = 0; i < tg.numberOfTiers(); i++) {
			try {
				final IntervalTier tgTier = tg.checkSpecifiedTierIsIntervalTier(i);
				if(tgTier.getName().equals(tierName)) {
					return tgTier;
				}
			} catch (PraatException pe) {
				// not an interval tier
			}
		}
		return null;
	}
	
	private long findIntervalForText(IntervalTier tgTier, String txt, long fromIndex) {
		for(long i = fromIndex; i < tgTier.numberOfIntervals(); i++) {
			final TextInterval interval = tgTier.interval(i);
			if(interval.getText().trim().equals(txt)) {
				return i;
			}
		}
		return -1;
	}
	
}
