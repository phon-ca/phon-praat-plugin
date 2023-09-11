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
package ca.phon.plugins.praat.importer;

import ca.hedlund.jpraat.binding.fon.*;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.extensions.UnvalidatedValue;
import ca.phon.ipa.IPATranscript;
import ca.phon.ipa.alignment.*;
import ca.phon.orthography.Orthography;
import ca.phon.project.Project;
import ca.phon.session.Record;
import ca.phon.session.*;
import ca.phon.session.tierdata.TierData;
import ca.phon.syllabifier.*;

import java.text.ParseException;
import java.util.*;
import java.util.logging.*;

public class TextGridImporter {

	public static final String IGNORE_EMPTY_INTERVALS_PROP =
			TextGridImporter.class.getName() + ".ignoreEmptyIntervals";

	private final static Logger LOGGER =
			Logger.getLogger(TextGridImporter.class.getName());

	public TextGridImporter() {
		super();
	}

	/**
	 * Setup default tier mapping if any tier names match
	 * default system tiers.
	 *
	 * @param textGridTiers
	 * @return map of text grid tier names to phon tier descriptions
	 */
	public Map<String, TierDescription> setupDefaultTierMapping(Set<String> textGridTiers) {
		final Map<String, TierDescription> retVal = new LinkedHashMap<String, TierDescription>();

		final SessionFactory factory = SessionFactory.newFactory();
		final Set<SystemTierType> mappedTiers = new HashSet<SystemTierType>();

		for(String tierName:textGridTiers) {
			final String tierPart =
					(tierName.indexOf(':') > 0 ? tierName.split(":")[0] : tierName);
			final SystemTierType systemTier = SystemTierType.tierFromString(tierPart);
			if(systemTier != null && systemTier != SystemTierType.Segment && !mappedTiers.contains(systemTier)) {
				final TierDescription td = factory.createTierDescription(systemTier);
				retVal.put(tierName, td);

				mappedTiers.add(systemTier);
			}
		}

		return retVal;
	}

	public List<Record> importTextGridRecords(Project project, Session session, TextGrid textGrid,
			String referenceTier, boolean ignoreEmptyIntervals, Map<String, TierDescription> tierMap) {
		List<Record> retVal = new ArrayList<>();

		long tgTierIdx = 0;
		IntervalTier intervalTier = new IntervalTier();
		for(long tierIdx = 1; tierIdx <= textGrid.numberOfTiers(); tierIdx++) {
			try {
				intervalTier = textGrid.checkSpecifiedTierIsIntervalTier(tierIdx);
				if(intervalTier.getName().equals(referenceTier)) {
					tgTierIdx = tierIdx;
					break;
				}
			} catch (PraatException pe) {}
		}
		if(tgTierIdx <= 0) {
			throw new IllegalArgumentException(referenceTier + " not found");
		}

		for(int i = 1; i <= intervalTier.numberOfIntervals(); i++) {
			TextInterval recordInterval = intervalTier.interval(i);

			if(recordInterval.getText().trim().length() == 0 &&
					ignoreEmptyIntervals) continue;
			try {
				TextGrid tg = textGrid.extractPart(recordInterval.getXmin(), recordInterval.getXmax(), true);
				// create a new record for each interval
				final Record newRecord =
						createRecordFromTextGrid(session, tg, tierMap);
				session.addRecord(newRecord);

				retVal.add(newRecord);
			} catch (PraatException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
		}

		return retVal;
	}

	public Record createRecordFromTextGrid(Session session, TextGrid textGrid,
			Map<String, TierDescription> tierMap) {
		final SessionFactory factory = SessionFactory.newFactory();
		final Record r = factory.createRecord();
		final MediaSegment segment = factory.createMediaSegment();
		segment.setStartValue((float)(textGrid.getXmin() * 1000.0f));
		segment.setEndValue((float)(textGrid.getXmax() * 1000.0f));
		segment.setUnitType(MediaUnit.Millisecond);
		r.setMediaSegment(segment);

		final SyllabifierLibrary library = SyllabifierLibrary.getInstance();
		SyllabifierInfo info = session.getExtension(SyllabifierInfo.class);
		if(info == null) {
			info = new SyllabifierInfo(session);
			info.setSyllabifierLanguageForTier(SystemTierType.IPATarget.getName(), library.defaultSyllabifierLanguage());
			info.setSyllabifierLanguageForTier(SystemTierType.IPAActual.getName(), library.defaultSyllabifierLanguage());
			session.putExtension(SyllabifierInfo.class, info);
		}
		final Syllabifier targetSyllabifier =
				library.getSyllabifierForLanguage(info.getSyllabifierLanguageForTier(SystemTierType.IPATarget.getName()));
		final Syllabifier actualSyllabifier = library.getSyllabifierForLanguage(info.getSyllabifierLanguageForTier(SystemTierType.IPAActual.getName()));

		final PhoneAligner aligner = new PhoneAligner();

		for(long i = 1; i <= textGrid.numberOfTiers(); i++) {
			try {
				final IntervalTier tier = textGrid.checkSpecifiedTierIsIntervalTier(i);
				final TierDescription td = tierMap.get(tier.getName().toString());
				if(td != null) {
					importTextGridTier(r, tier, td);
				}
			} catch (PraatException e) {
				// not an interval tier
			}
		}

		final IPATranscript ipaT = r.getIPATarget();
		if(targetSyllabifier != null) {
			targetSyllabifier.syllabify(ipaT.toList());
		}
		final IPATranscript ipaA = r.getIPAActual();
		if(actualSyllabifier != null) {
			actualSyllabifier.syllabify(ipaA.toList());
		}

		final PhoneAlignment phoneAlignment = PhoneAlignment.fromTiers(r.getIPATargetTier(), r.getIPAActualTier());
		r.setPhoneAlignment(phoneAlignment);

		return r;
	}

	// FIXME
	public void importTextGridTier(Record r, IntervalTier tgTier, TierDescription td) {
//		if(tgTier.getName().toString().endsWith(": Tier")) {
//			String fullTierData =
//					(tgTier.numberOfIntervals() > 0 ? tgTier.interval(1).getText() : "[]");
//
//			final String[] vals = fullTierData.split("\\[");
//			for(int i = 1; i < vals.length; i++) {
//				String val = vals[i].replaceAll("\\]", "").trim();
//				grpVals.add(val);
//			}
//		} else {
//			StringBuffer buffer = new StringBuffer();
//			for(long i = 1; i <= tgTier.numberOfIntervals(); i++) {
//				final TextInterval interval = tgTier.interval(i);
//				if(buffer.length() > 0) buffer.append(" ");
//				buffer.append(interval.getText().trim());
//			}
//			if(buffer.length() > 0)
//				grpVals.add(buffer.toString());
//		}
//
//		if(!SystemTierType.isSystemTier(td.getName()) && !r.hasTier(td.getName())) {
//			final Tier<TierData> depTier = SessionFactory.newFactory().createTier(td.getName(), TierData.class);
//			r.putTier(depTier);
//		}
//
//		for(int grpIdx = 0; grpIdx < grpVals.size(); grpIdx++) {
//			final String grpVal = grpVals.get(grpIdx);
//
//			Group grp = null;
//			if(grpIdx >= r.numberOfGroups())
//				grp = r.addGroup();
//			else
//				grp = r.getGroup(grpIdx);
//			if(SystemTierType.isSystemTier(td.getName())) {
//				final SystemTierType systemTier = SystemTierType.tierFromString(td.getName());
//				if(systemTier == SystemTierType.Orthography) {
//					try {
//						final Orthography ortho = Orthography.parseOrthography(grpVal);
//						grp.setOrthography(ortho);
//					} catch (ParseException pe) {
//						final Orthography ortho = new Orthography();
//						final UnvalidatedValue uv = new UnvalidatedValue(grpVal, pe);
//						ortho.putExtension(UnvalidatedValue.class, uv);
//						grp.setOrthography(ortho);
//						LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
//					}
//				} else if(systemTier == SystemTierType.IPATarget) {
//					try {
//						final IPATranscript ipa = IPATranscript.parseIPATranscript(grpVal);
//						grp.setIPATarget(ipa);
//					} catch (ParseException e) {
//						final IPATranscript ipa = new IPATranscript();
//						final UnvalidatedValue uv = new UnvalidatedValue(grpVal, e);
//						ipa.putExtension(UnvalidatedValue.class, uv);
//						grp.setIPATarget(ipa);
//						LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
//					}
//				} else if(systemTier == SystemTierType.IPAActual) {
//					try {
//						final IPATranscript ipa = IPATranscript.parseIPATranscript(grpVal);
//						grp.setIPAActual(ipa);
//					} catch (ParseException e) {
//						final IPATranscript ipa = new IPATranscript();
//						final UnvalidatedValue uv = new UnvalidatedValue(grpVal, e);
//						ipa.putExtension(UnvalidatedValue.class, uv);
//						grp.setIPAActual(ipa);
//						LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
//					}
//				} else if(systemTier == SystemTierType.Notes) {
//					grp.setNotes(
//							new TierString(new String(grp.getNotes() + " " + grpVal).trim()));
//				} else {
//					LOGGER.warning("Cannot import into tier " + systemTier.getName());
//				}
//			} else {
//				if(td.isGrouped()) {
//					grp.setTier(td.getName(), TierString.class, new TierString(grpVal));
//				} else {
//					grp.setTier(td.getName(), TierString.class,
//							new TierString( (grp.getTier(td.getName(), TierString.class) + " " + new TierString(grpVal)).trim()));
//				}
//			}
//		}
	}

}
