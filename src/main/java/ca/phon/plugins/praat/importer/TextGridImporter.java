package ca.phon.plugins.praat.importer;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.phon.ipa.IPATranscript;
import ca.phon.ipa.alignment.PhoneAligner;
import ca.phon.ipa.alignment.PhoneMap;
import ca.phon.orthography.Orthography;
import ca.phon.plugins.praat.TextGridManager;
import ca.phon.project.Project;
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
import ca.phon.textgrid.TextGrid;
import ca.phon.textgrid.TextGridInterval;
import ca.phon.textgrid.TextGridReader;
import ca.phon.textgrid.TextGridTier;

public class TextGridImporter {
	
	private final static Logger LOGGER = 
			Logger.getLogger(TextGridImporter.class.getName());
	
	public TextGridImporter() {
		super();
	}
	
	/**
	 * Scan all text grids in the given folder and
	 * return the set of tier names.
	 * 
	 * @param folder
	 * 
	 * @return tier names
	 */
	public Set<String> scanTextGrids(File folder) {
		// look for files with the .TextGrid extension
		final FileFilter filter = new FileFilter() {
			
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(".TextGrid");
			}
			
		};
		final Set<String> retVal = new LinkedHashSet<String>();
		for(File f:folder.listFiles(filter)) {
			try {
				// load text grid
				final TextGridReader reader = new TextGridReader(f, "UTF-16");
				final TextGrid tg = reader.readTextGrid();
				for(int i = 0; i < tg.getNumberOfTiers(); i++) {
					final TextGridTier tier = tg.getTier(i);
					retVal.add(tier.getTierName());
				}
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			} catch (ParseException e) {
				try {
					final TextGridReader reader = new TextGridReader(f, "UTF-8");
					final TextGrid tg = reader.readTextGrid();
					for(int i = 0; i < tg.getNumberOfTiers(); i++) {
						final TextGridTier tier = tg.getTier(i);
						retVal.add(tier.getTierName());
					}
				} catch (IOException ex) {
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), ex);
				} catch (ParseException ex) {
					LOGGER.log(Level.SEVERE, "Unable to read TextGrid", ex);
				}
			}
		}
		return retVal;
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
	
	public void importTextGrid(Project project, Session session, TextGrid textGrid, 
			Map<String, TierDescription> tierMap, Map<String, String> groupMarkers) {
		final SessionFactory factory = SessionFactory.newFactory();
		final Record r = factory.createRecord();
		final MediaSegment segment = factory.createMediaSegment();
		segment.setStartValue(textGrid.getMin() * 1000.0f);
		segment.setEndValue(textGrid.getMax() * 1000.0f);
		segment.setUnitType(MediaUnit.Millisecond);
		r.getSegment().addGroup(segment);
		session.addRecord(r);
		
		final TextGridManager tgManager = TextGridManager.getInstance(project);
		tgManager.saveTextGrid(textGrid, r.getUuid().toString());
		
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
		
		for(int i = 0; i < textGrid.getNumberOfTiers(); i++) {
			final TextGridTier tier = textGrid.getTier(i);
			final TierDescription td = tierMap.get(tier.getTierName());
			if(td != null) {
				importTextGridTier(r, tier, td, groupMarkers.get(tier.getTierName()) == null ? "#" : groupMarkers.get(tier.getTierName()));
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
	}
	
	public void importTextGridTier(Record r, TextGridTier tgTier, TierDescription td, String groupMarker) {
		final List<String> grpVals = new ArrayList<String>();
		
		if(tgTier.getTierName().endsWith(": Tier")) {
			String fullTierData = "[]";
			for(int i = 0; i < tgTier.getNumberOfIntervals(); i++) {
				final TextGridInterval interval = tgTier.getIntervalAt(i);
				if(!interval.getLabel().equals(groupMarker)) {
					fullTierData = interval.getLabel();
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
			for(int i = 0; i < tgTier.getNumberOfIntervals(); i++) {
				final TextGridInterval interval = tgTier.getIntervalAt(i);
				if(!interval.getLabel().equals(groupMarker)) {
					if(buffer.length() > 0) buffer.append(" ");
					buffer.append(interval.getLabel());
				} else {
					if(i > 0) {
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
					final Orthography ortho = Orthography.parseOrthography(grpVal);
					grp.setOrthography(ortho);
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
