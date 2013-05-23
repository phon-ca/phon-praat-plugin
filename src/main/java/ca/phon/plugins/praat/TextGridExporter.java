package ca.phon.plugins.praat;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ca.phon.application.project.IPhonProject;
import ca.phon.application.transcript.Form;
import ca.phon.application.transcript.IDependentTier;
import ca.phon.application.transcript.IMedia;
import ca.phon.application.transcript.IPhoneticRep;
import ca.phon.application.transcript.IUtterance;
import ca.phon.application.transcript.IWord;
import ca.phon.application.transcript.MediaUnit;
import ca.phon.engines.syllabifier.Syllabifier;
import ca.phon.gui.recordeditor.SystemTierType;
import ca.phon.phone.Phone;
import ca.phon.plugins.praat.textgrid.TextGrid;
import ca.phon.plugins.praat.textgrid.TextGridInterval;
import ca.phon.plugins.praat.textgrid.TextGridTier;
import ca.phon.plugins.praat.textgrid.TextGridTierType;
import ca.phon.syllable.Syllable;
import ca.phon.syllable.SyllableConstituentType;
import ca.phon.system.logger.PhonLogger;

/**
 * Utility methods for exporting Phon tier data into
 * TextGrids
 * 
 */
public class TextGridExporter {
	
	public static enum ExportType {
		TIER("Tier"), // full tier in one interval
		GROUP("Group"), // each group in an interval
		WORD("Word"), // each word in an inverval
		SYLLABLE("Syllable"), // (IPA-only) each syllable in an interval
		PHONE("Phone"); // (IPA-only) each phone in an interval
		
		private String tierSuffix;
		
		private ExportType(String suffix) {
			this.tierSuffix = suffix;
		}
		
		@Override
		public String toString() {
			return tierSuffix;
		}
	}
	
	/** Default hash length in ms */
	public final static float MARKER_LENGTH = 0.1f;
	
	/** Default marker interval/point text */
	public final static String MARKER_TEXT = "#";

	/** Default space length in ms */
	public final static float SPACER_LENGTH = 0.01f;
	
	public final static String SPACER_TEXT = " ";
	
	/**
	 * Location of text grid export defaults
	 */
	private final static String EXPORT_DEFAULTS = "__res/plugin_data/textgrid/exportdefaults.xml";
	
	/**
	 * Export phon tier to textgrid 
	 * 
	 * @param utt
	 * @param textgrid
	 * @param entry
	 */
	public void addTierToTextGrid(IUtterance utt, TextGrid textgrid, TextGridExportEntry entry) {
		addTierToTextGrid(utt, entry.getPhonTier(), entry.getExportType(), textgrid, entry.getTextGridTier());
	}
	
	/**
	 * Export the specified tier from the given Phon record
	 * into the given text grid with specified name.
	 * 
	 * @param record
	 * @param tier
	 * @param type
	 * @param textgrid
	 * @param tgName
	 */
	public void addTierToTextGrid(IUtterance utt, String tier,  ExportType type,
			TextGrid textgrid, String tgName) {
		// create the new textgrid tier
		final TextGridTier tgTier = new TextGridTier(tgName, TextGridTierType.INTERVAL);
		textgrid.addTier(tgTier);
		
		final SystemTierType systemTier = SystemTierType.tierFromString(tier);
		final List<String> intervals = new ArrayList<String>();
		
		// handle 'TIER' case here
		if(type == ExportType.TIER) {
			intervals.add("#");
			intervals.add(utt.getTierString(tier));
			intervals.add("#");
		} else {
			if(systemTier != null) {
				
				if(systemTier == SystemTierType.Orthography) {
					switch(type) {
					case GROUP:
						for(IWord word:utt.getWords()) {
							intervals.add(MARKER_TEXT);
							intervals.add(word.getWord());
						}
						intervals.add(MARKER_TEXT);
						break;
						
					default:
						for(IWord group:utt.getWords()) {
							intervals.add(MARKER_TEXT);
							final String[] split = group.getWord().split("\\p{Space}");
							int widx = 0;
							for(String word:split) {
								if(widx++ > 0) intervals.add(SPACER_TEXT);
								intervals.add(word);
							}
						}
						intervals.add(MARKER_TEXT);
						break;
					}
				} else if(systemTier == SystemTierType.IPATarget || systemTier == SystemTierType.IPAActual) {
					switch(type) {
					case GROUP:
						for(IWord word:utt.getWords()) {
							intervals.add(MARKER_TEXT);
							final IPhoneticRep phoRep = word.getPhoneticRepresentation(
									(systemTier == SystemTierType.IPATarget ? Form.Target : Form.Actual));
							intervals.add(phoRep.getTranscription());
						}
						intervals.add(MARKER_TEXT);
						break;
						
					case WORD:
						for(IWord word:utt.getWords()) {
							intervals.add(MARKER_TEXT);
							final IPhoneticRep phoRep = word.getPhoneticRepresentation(
									(systemTier == SystemTierType.IPATarget ? Form.Target : Form.Actual));
							final String group = phoRep.getTranscription();
							final String[] words = group.split("\\p{Space}");
							int widx = 0;
							for(String w:words) {
								if(widx++ > 0) intervals.add(SPACER_TEXT);
								intervals.add(w);
							}
						}
						intervals.add(MARKER_TEXT);
						break;
						
					case SYLLABLE:
						for(IWord word:utt.getWords()) {
							intervals.add(MARKER_TEXT);
							final IPhoneticRep phoRep = word.getPhoneticRepresentation(
									(systemTier == SystemTierType.IPATarget ? Form.Target : Form.Actual));
							
							final List<Phone> currentWord = new ArrayList<Phone>();
							for(Phone p:phoRep.getPhones()) {
								int widx = 0;
								if(p.getScType() == SyllableConstituentType.WordBoundaryMarker) {
									if(currentWord.size() > 0) {
										if(widx++ > 0) intervals.add(SPACER_TEXT);
										final List<Syllable> sylls = Syllabifier.getSyllabification(currentWord);
										for(Syllable syll:sylls) {
											intervals.add(syll.toString());
										}
									}
								}
							}
						}
						intervals.add(MARKER_TEXT);
						break;
						
					case PHONE:
						for(IWord word:utt.getWords()) {
							intervals.add(MARKER_TEXT);
							final IPhoneticRep phoRep = word.getPhoneticRepresentation(
									(systemTier == SystemTierType.IPATarget ? Form.Target : Form.Actual));
							
							final List<Phone> currentInterval = new ArrayList<Phone>();
							for(Phone p:phoRep.getPhones()) {
								if(p.getScType() == SyllableConstituentType.SyllableBoundaryMarker
										|| p.getScType() == SyllableConstituentType.SyllableStressMarker) {
									currentInterval.add(p);
								} else {
									String intervalText = "";
									for(Phone ph:currentInterval) {
										intervalText += ph.getPhoneString();
									}
									intervalText += p.getPhoneString();
									intervals.add(intervalText);
									currentInterval.clear();
								}
							}
						}
						intervals.add(MARKER_TEXT);
						break;
					}
				} else {
					intervals.add("#");
					intervals.add(utt.getTierString(tier));
					intervals.add("#");
				}
			} else {
				// user-defined tier
				if(utt.getWordAlignedTierNames().contains(tier)) {
					for(IWord word:utt.getWords()) {
						intervals.add(MARKER_TEXT);
						final IDependentTier depTier = word.getDependentTier(tier);
						intervals.add(depTier.getTierValue());
					}
					intervals.add(MARKER_TEXT);
				} else {
					intervals.add("#");
					intervals.add(utt.getTierString(tier));
					intervals.add("#");
				}
			}
		}
		
		// create textgrid invervals
		int markerCount = 0;
		int spacerCount = 0;
		for(String interval:intervals) {
			if(interval.equals(MARKER_TEXT)) {
				markerCount++;
			} else if(interval.equals(SPACER_TEXT)) {
				spacerCount++;
			}
		}
		
		final float tgLength = textgrid.getMax() - textgrid.getMin();
		final float intervalsLength = tgLength - (MARKER_LENGTH * markerCount) - (SPACER_LENGTH * spacerCount);
		final float intervalLength = intervalsLength / (intervals.size() - markerCount - spacerCount);
		
		float currentStart = textgrid.getMin();
		for(String interval:intervals) {
			float len = intervalLength;
			if(interval.equals(MARKER_TEXT)) {
				len = MARKER_LENGTH;
			} else if(interval.equals(SPACER_TEXT)) {
				len = SPACER_LENGTH;
			}
			final TextGridInterval tgInt = new TextGridInterval(interval, currentStart, currentStart + len);
			tgTier.addInterval(tgInt);
			currentStart += len;
		}
	}
	
	/**
	 * Create an empty textgrid from the given record.
	 * Initializes the TextGrid with proper min/max times
	 * 
	 * @param utt
	 */
	public TextGrid createEmptyTextGrid(IUtterance utt) {
		final TextGrid retVal = new TextGrid();
		final IMedia media = utt.getMedia();

		if(media != null) {
			float startTime = media.getStartValue();
			if(media.getUnitType() == MediaUnit.Millisecond)
				startTime /= 1000.0f;
			float endTime = media.getEndValue();
			if(media.getUnitType() == MediaUnit.Millisecond)
				endTime /= 1000.0f;
			retVal.setMin(startTime);
			retVal.setMax(endTime);
		}
		
		return retVal;
	}
	
	/**
	 * Setup the given textgrid using settings from the
	 * specified project.
	 * 
	 * @param project
	 * @param utt
	 * @param textgrid
	 */
	public void setupTextGrid(IPhonProject project, IUtterance utt, TextGrid textgrid) {
		final List<TextGridExportEntry> exports = getExports(project);
		for(TextGridExportEntry entry:exports) {
			addTierToTextGrid(utt, textgrid, entry);
		}
	}
	
	/**
	 * Get the list of default text grid export entries
	 * 
	 * @return list of default exports
	 */
	public List<TextGridExportEntry> getDefaultExports() {
		final List<TextGridExportEntry> retVal = new ArrayList<TextGridExportEntry>();
		
		// add orthography tiers
		final TextGridExportEntry ortho1 = new TextGridExportEntry(SystemTierType.Orthography.getTierName(), ExportType.TIER, 
				SystemTierType.Orthography.getTierName() + ": " + ExportType.TIER.toString());
		final TextGridExportEntry ortho2 = new TextGridExportEntry(SystemTierType.Orthography.getTierName(), ExportType.GROUP, 
				SystemTierType.Orthography.getTierName() + ": " + ExportType.GROUP.toString());
		final TextGridExportEntry ortho3 = new TextGridExportEntry(SystemTierType.Orthography.getTierName(), ExportType.WORD, 
				SystemTierType.Orthography.getTierName() + ": " + ExportType.WORD.toString());
		retVal.add(ortho1);
		retVal.add(ortho2);
		retVal.add(ortho3);
		
		// ipa actual
		final TextGridExportEntry ipa1 = new TextGridExportEntry(SystemTierType.IPAActual.getTierName(), ExportType.SYLLABLE, 
				SystemTierType.IPAActual.getTierName() + ": " + ExportType.SYLLABLE.toString());
		final TextGridExportEntry ipa2 = new TextGridExportEntry(SystemTierType.IPAActual.getTierName(), ExportType.PHONE, 
				SystemTierType.IPAActual.getTierName() + ": " + ExportType.PHONE.toString());
		retVal.add(ipa1);
		retVal.add(ipa2);
		
		return retVal;
	}
	
	/**
	 * Get the user defined list of exports.  If no export file is found,
	 * the default list of exports is returned.
	 * 
	 * @param project
	 * @return textgrid export entries
	 */
	public List<TextGridExportEntry> getExports(IPhonProject project) {
		List<TextGridExportEntry> retVal = null;
		
		try {
			final String exportsFile = project.getProjectLocation() + File.separator + EXPORT_DEFAULTS;
			retVal = getExportsFromFile(exportsFile);
		} catch (IOException e) {
			retVal = getDefaultExports();
		}
		
		return retVal;
	}
	
	/**
	 * Save the given exports the the project defaults file
	 * 
	 * @param exports
	 * @param project
	 * 
	 * @return <code>true</code> if successful, <code>false</code> otherwise
	 */
	public boolean saveExports(List<TextGridExportEntry> exports, IPhonProject project) {
		boolean retVal = false;
		try {
			final String exportsFile = project.getProjectLocation() + File.separator + EXPORT_DEFAULTS;
			saveExportsToFile(exports, exportsFile);
		} catch (IOException e) {
			e.printStackTrace();
			PhonLogger.severe(e.getMessage());
		}		
		return retVal;
	}
	
	/**
	 * Get the exports from the given file
	 * 
	 * @param exportsFile
	 * @return entries
	 * @throws IOException
	 */
	private List<TextGridExportEntry> getExportsFromFile(String exportsFile)
		throws IOException {
		final FileInputStream fin = new FileInputStream(exportsFile);
		final XMLDecoder xmlDec = new XMLDecoder(fin);
		
		final List<TextGridExportEntry> retVal = new ArrayList<TextGridExportEntry>();
		
		final Object obj = xmlDec.readObject();
		if(obj != null && obj instanceof ArrayList) {
			final List<?> objList = (List<?>)obj;
			
			for(Object o:objList) {
				if(o instanceof TextGridExportEntry) {
					retVal.add((TextGridExportEntry)o);
				}
			}
		}
		
		return retVal;
	}
	
	/**
	 * Save exports to file
	 * 
	 * @param exportsFile
	 * @throws IOException
	 */
	private void saveExportsToFile(List<TextGridExportEntry> exports, String exportsFile) 
		throws IOException {
		
		final File file = new File(exportsFile);
		final File dir = file.getParentFile();
		if(!dir.exists()) {
			dir.mkdirs();
		}
		
		final FileOutputStream fout = new FileOutputStream(exportsFile);
		final XMLEncoder xmlEnc = new XMLEncoder(fout);
		
		xmlEnc.writeObject(exports);
		xmlEnc.flush();
		xmlEnc.close();
	}
	
}
