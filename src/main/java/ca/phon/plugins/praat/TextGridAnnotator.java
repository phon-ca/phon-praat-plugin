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

import ca.hedlund.jpraat.binding.fon.*;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.ipa.*;
import ca.phon.orthography.*;
import ca.phon.session.Record;
import ca.phon.session.*;
import ca.phon.syllable.SyllableConstituentType;

import java.util.logging.*;

public class TextGridAnnotator {
	
	private static final Logger LOGGER = Logger
			.getLogger(TextGridAnnotator.class.getName());
	
	public void annotateRecord(TextGrid textGrid, Record record) {
		annotateTier(textGrid, record.getOrthography());
		annotateTier(textGrid, record.getIPAActual());
		annotateTier(textGrid, record.getIPATarget());
		
		for(String tierName:record.getExtraTierNames()) {
			if(record.getTierType(tierName) == TierString.class) {
				annotateTier(textGrid, record.getTier(tierName));
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void annotateTier(TextGrid textGrid, Tier<?> tier) {
		if(tier.getDeclaredType() == Orthography.class) {
			annotateOrthographyTier(textGrid, (Tier<Orthography>)tier);
		} else if(tier.getDeclaredType() == IPATranscript.class) {
			annotateIPATier(textGrid, (Tier<IPATranscript>)tier);
		} else if(tier.getDeclaredType() == TierString.class) {
			annotateTextTier(textGrid, (Tier<TierString>)tier);
		} else {
			LOGGER.warning("Cannot annotate tier of type " + tier.getDeclaredType().toString());
		}
	}
	
	private void annotateOrthographyTier(TextGrid textGrid, Tier<Orthography> orthoTier) {
		// full tier
		final IntervalTier fullOrthoTier = findTextGridIntervalTier(textGrid, orthoTier, "Tier");
		if(fullOrthoTier != null) {
			final long fullTierIntervalIdx = findIntervalForText(fullOrthoTier, orthoTier.toString(), 1);
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
				final long gTgi = findIntervalForText(orthoGroupTier, ortho.toString(), gidx+1);
				if(gTgi >= 0) {
					ortho.putExtension(TextInterval.class, orthoGroupTier.interval(gTgi));
					gidx = gTgi;
				} else {
					LOGGER.info(
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
					final long wTgi = findIntervalForText(orthoWordTier, ele.toString(), widx+1);
					if(wTgi >= 0) {
						ele.putExtension(TextInterval.class, orthoWordTier.interval(wTgi));
						widx = wTgi;
					} else {
						LOGGER.info(
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
			final long fullTierIntervalIdx = findIntervalForText(fullOrthoTier, ipaTier.toString(), 1);
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
				for(IPAElement ele:ipaGrp) {
					if(ele.getScType() == SyllableConstituentType.WORDBOUNDARYMARKER
							|| ele.getScType() == SyllableConstituentType.SYLLABLEBOUNDARYMARKER
							|| ele.getScType() == SyllableConstituentType.SYLLABLESTRESSMARKER) continue;
					final int eleIdx = ipaGrp.indexOf(ele);
					String txt = ele.getText();
					long pTgi = -1L;
					if(eleIdx > 0 && ipaGrp.elementAt(eleIdx-1).getScType() == SyllableConstituentType.SYLLABLESTRESSMARKER) {
						var t = ipaGrp.elementAt(eleIdx-1).getText() + txt;
						pTgi = findIntervalForText(ipaPhoneTier, t, pidx+1);
					}
					if(pTgi < 0) {
						pTgi = findIntervalForText(ipaPhoneTier, txt, pidx+1);
					}
					if(pTgi >= 0) {
						ele.putExtension(TextInterval.class, ipaPhoneTier.interval(pTgi));
						pidx = pTgi;
					} else {
						LOGGER.info(String.format("Unable to find interval for element '%s'", ele.toString()));
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
				final long gTgi = findIntervalForText(ipaGroupTier, ipaGrp.toString(), gidx+1);
				if(gTgi >= 0) {
					ipaGrp.putExtension(TextInterval.class, ipaGroupTier.interval(gTgi));
					gidx = gTgi;
				} else {
					LOGGER.info(
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
					final long wTgi = findIntervalForText(ipaWordTier, word.toString(), widx+1);
					if(wTgi >= 0) {
						word.putExtension(TextInterval.class, ipaWordTier.interval(wTgi));
						widx = wTgi;
					} else {
						LOGGER.info(
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
					final long sTgi = findIntervalForText(ipaSyllTier, syll.toString(), sidx+1);
					if(sTgi >= 0) {
						syll.putExtension(TextInterval.class, ipaSyllTier.interval(sTgi));
						sidx = sTgi;
					} else {
						LOGGER.info(
								String.format("Unable to find interval for syllable '%s'", ipaGrp.toString()));
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
	
	private void annotateTextTier(TextGrid textGrid, Tier<TierString> textTier) {
		// full tier
		final IntervalTier fullTextTier = findTextGridIntervalTier(textGrid, textTier, "Tier");
		if(fullTextTier != null) {
			final long fullTierIntervalIdx = findIntervalForText(fullTextTier, textTier.toString(), 1);
			if(fullTierIntervalIdx >= 0) {
				textTier.putExtension(TextInterval.class, fullTextTier.interval(fullTierIntervalIdx));
			}
		}
		
		// groups
		final IntervalTier groupTier = findTextGridIntervalTier(textGrid, textTier, "Group");
		if(groupTier != null) {
			long gidx = 0;
			for(int i = 0; i < textTier.numberOfGroups(); i++) {
				final TierString text = textTier.getGroup(i);
				final long gTgi = findIntervalForText(groupTier, text.toString(), gidx+1);
				if(gTgi >= 0) {
					text.putExtension(TextInterval.class, groupTier.interval(gTgi));
					gidx = gTgi;
				} else {
					LOGGER.info(
							String.format("Unable to find interval for group '%s'", text.toString()));
					break;
				}
			}
		}
		
		// words
		final IntervalTier wordTier = findTextGridIntervalTier(textGrid, textTier, "Word");
		if(wordTier != null) {
			long widx = 0;
			for(int i = 0; i < textTier.numberOfGroups(); i++) {
				final TierString groupText = textTier.getGroup(i);
				for(TierString wordText:groupText.getWords()) {
					final long wTgi = findIntervalForText(wordTier, wordText.toString(), widx+1);
					if(wTgi >= 0) {
						wordText.putExtension(TextInterval.class, wordTier.interval(wTgi));
						widx = wTgi;
					} else {
						LOGGER.info(
								String.format("Unable to find interval for word '%s'", wordText.toString()));
					}
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
		for(long i = 1; i <= tg.numberOfTiers(); i++) {
			try {
				final IntervalTier tgTier = tg.checkSpecifiedTierIsIntervalTier(i);
				if(tgTier.getName().toString().equals(tierName)) {
					return tgTier;
				}
			} catch (PraatException pe) {
				// not an interval tier
			}
		}
		return null;
	}
	
	private long findIntervalForText(IntervalTier tgTier, String txt, long fromIndex) {
		for(long i = fromIndex; i <= tgTier.numberOfIntervals(); i++) {
			final TextInterval interval = tgTier.interval(i);
			if(interval.getText().trim().equals(txt)) {
				return i;
			}
		}
		return -1;
	}
	
}
