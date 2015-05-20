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
package ca.phon.plugins.praat.importer;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.hedlund.jpraat.binding.fon.IntervalTier;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.ipa.IPATranscript;
import ca.phon.ipa.alignment.PhoneAligner;
import ca.phon.ipa.alignment.PhoneMap;
import ca.phon.orthography.Orthography;
import ca.phon.session.Group;
import ca.phon.session.MediaSegment;
import ca.phon.session.MediaUnit;
import ca.phon.session.Record;
import ca.phon.session.Session;
import ca.phon.session.SessionFactory;
import ca.phon.session.SyllabifierInfo;
import ca.phon.session.SystemTierType;
import ca.phon.session.Tier;
import ca.phon.session.TierDescription;
import ca.phon.syllabifier.Syllabifier;
import ca.phon.syllabifier.SyllabifierLibrary;

public class TextGridImporter {
	
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
				final TierDescription td = factory.createTierDescription(systemTier.getName(), systemTier.isGrouped());
				retVal.put(tierName, td);
				
				mappedTiers.add(systemTier);
			}
		}
		
		return retVal;
	}
	
	public Record createRecordFromTextGrid(Session session, TextGrid textGrid, 
			Map<String, TierDescription> tierMap, Map<String, String> groupMarkers) {
		final SessionFactory factory = SessionFactory.newFactory();
		final Record r = factory.createRecord();
		final MediaSegment segment = factory.createMediaSegment();
		segment.setStartValue((float)(textGrid.getXmin() * 1000.0f));
		segment.setEndValue((float)(textGrid.getXmax() * 1000.0f));
		segment.setUnitType(MediaUnit.Millisecond);
		r.getSegment().addGroup(segment);
		
		final SyllabifierLibrary library = SyllabifierLibrary.getInstance();
		SyllabifierInfo info = session.getExtension(SyllabifierInfo.class);
		if(info == null) {
			info = new SyllabifierInfo(session);
			info.setSyllabifierLanguageForTier(SystemTierType.IPATarget.getName(), library.defaultSyllabifierLanguage());
			info.setSyllabifierLanguageForTier(SystemTierType.IPAActual.getName(), library.defaultSyllabifierLanguage());
			session.putExtension(SyllabifierInfo.class, info);
		}
		final Syllabifier targetSyllabifier = library.getSyllabifierForLanguage(info.getSyllabifierLanguageForTier(SystemTierType.IPATarget.getName()));
		final Syllabifier actualSyllabifier = library.getSyllabifierForLanguage(info.getSyllabifierLanguageForTier(SystemTierType.IPAActual.getName()));
		
		final PhoneAligner aligner = new PhoneAligner();
		
		for(long i = 1; i <= textGrid.numberOfTiers(); i++) {
			try {
				final IntervalTier tier = textGrid.checkSpecifiedTierIsIntervalTier(i);
				final TierDescription td = tierMap.get(tier.getName().toString());
				if(td != null) {
					importTextGridTier(r, tier, td, groupMarkers.get(tier.getName().toString()) == null ? "#" : groupMarkers.get(tier.getName().toString()));
				}
			} catch (PraatException e) {
				// not an interval tier
			}
		}
		
		for(int i = 0; i < r.numberOfGroups(); i++) {
			final Group g = r.getGroup(i);
			
			final IPATranscript ipaT = g.getIPATarget();
			if(targetSyllabifier != null) {
				targetSyllabifier.syllabify(ipaT.toList());
			}
			final IPATranscript ipaA = g.getIPAActual();
			if(actualSyllabifier != null) {
				actualSyllabifier.syllabify(ipaA.toList());
			}
			
			final PhoneMap pm = aligner.calculatePhoneMap(ipaT, ipaA);
			g.setPhoneAlignment(pm);
		}
		
		return r;
	}
	
	public void importTextGridTier(Record r, IntervalTier tgTier, TierDescription td, String groupMarker) {
		final List<String> grpVals = new ArrayList<String>();
		
		if(tgTier.getName().toString().endsWith(": Tier")) {
			String fullTierData = "[]";
			for(long i = 1; i <= tgTier.numberOfIntervals(); i++) {
				final TextInterval interval = tgTier.interval(i);
				if(!interval.getText().equals(groupMarker)) {
					fullTierData = interval.getText();
					break; // only one interval with data expected
				}
			}
			
			final String[] vals = fullTierData.split("\\[");
			for(int i = 1; i < vals.length; i++) {
				String val = vals[i].replaceAll("\\]", "").trim();
				grpVals.add(val);
			}
		} else {
			StringBuffer buffer = new StringBuffer();
			for(long i = 1; i <= tgTier.numberOfIntervals(); i++) {
				final TextInterval interval = tgTier.interval(i);
				if(!interval.getText().equals(groupMarker)) {
					if(buffer.length() > 0) buffer.append(" ");
					buffer.append(interval.getText());
				} else {
					if(i > 1) {
						String val = buffer.toString();
						grpVals.add(val);
						buffer.setLength(0);
					}
				}
			}
		}
		
		if(!r.hasTier(td.getName())) {
			final Tier<String> depTier = SessionFactory.newFactory().createTier(td.getName(), String.class, td.isGrouped());
			r.putTier(depTier);
		}
		
		for(int grpIdx = 0; grpIdx < grpVals.size(); grpIdx++) {
			final String grpVal = grpVals.get(grpIdx);
			
			Group grp = null;
			if(grpIdx >= r.numberOfGroups())
				grp = r.addGroup();
			else
				grp = r.getGroup(grpIdx);
			if(SystemTierType.isSystemTier(td.getName())) {
				final SystemTierType systemTier = SystemTierType.tierFromString(td.getName());
				if(systemTier == SystemTierType.Orthography) {
					try {
						final Orthography ortho = Orthography.parseOrthography(grpVal);
						grp.setOrthography(ortho);
					} catch (ParseException pe) {
						LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
					}
				} else if(systemTier == SystemTierType.IPATarget) {
					try {
						final IPATranscript ipa = IPATranscript.parseIPATranscript(grpVal);
						grp.setIPATarget(ipa);
					} catch (ParseException e) {
						LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
					}
				} else if(systemTier == SystemTierType.IPAActual) {
					try {
						final IPATranscript ipa = IPATranscript.parseIPATranscript(grpVal);
						grp.setIPAActual(ipa);
					} catch (ParseException e) {
						LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
					}
				} else if(systemTier == SystemTierType.Notes) {
					grp.setNotes(new String(grp.getNotes() + " " + grpVal).trim());
				} else {
					LOGGER.warning("Cannot import into tier " + systemTier.getName());
				}
			} else {
				if(td.isGrouped()) {
					grp.setTier(td.getName(), String.class, grpVal);
				} else {
					grp.setTier(td.getName(), String.class, 
							new String(grp.getTier(td.getName(), String.class) + " " + grpVal).trim());
				}
			}
		}
	} 
	
}
