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

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.hedlund.jpraat.binding.fon.IntervalTier;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.ipa.IPAElement;
import ca.phon.ipa.IPATranscript;
import ca.phon.media.util.MediaLocator;
import ca.phon.plugins.praat.Segmentation;
import ca.phon.plugins.praat.TextGridManager;
import ca.phon.plugins.praat.script.PraatScript;
import ca.phon.plugins.praat.script.PraatScriptContext;
import ca.phon.project.Project;
import ca.phon.session.Group;
import ca.phon.session.MediaSegment;
import ca.phon.session.MediaUnit;
import ca.phon.session.Record;
import ca.phon.session.RecordFilter;
import ca.phon.session.Session;
import ca.phon.session.SystemTierType;
import ca.phon.session.Tier;
import ca.phon.syllable.SyllableConstituentType;

/**
 * Utility methods for exporting Phon tier data into
 * TextGrids
 * 
 */
public class TextGridExporter {
	
	private static final Logger LOGGER = Logger
			.getLogger(TextGridExporter.class.getName());
	
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
	public void addTierToTextGrid(Record utt, TextGrid textgrid, TextGridExportEntry entry) {
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
	public void addTierToTextGrid(Record record, String tier, Segmentation type,
			TextGrid textgrid, String tgName) {
		// create the new textgrid tier
		IntervalTier tgTier = null;
		try {
			tgTier = IntervalTier.create(textgrid.getXmin(), textgrid.getXmax());
			tgTier.removeInterval(1);
			tgTier.setName(tgName);
		} catch (PraatException pe) {
			LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
			return;
		}
		// check if we a processing a built-in tier
		final SystemTierType systemTier = SystemTierType.tierFromString(tier);
		
		// calculate some values for interval times
		final int numHashes = record.numberOfGroups() + 1;
		final double totalTime = textgrid.getXmax() - textgrid.getXmin();
		final float hashLength = MARKER_LENGTH * numHashes;
		final double dataLength = totalTime - hashLength;
		final double groupLength = dataLength / record.numberOfGroups();
		
		// if the exportType is TIER, we create a 3-interval tier
		// this takes care of all flat tiers
		if(type == Segmentation.TIER) {
			final Tier<String> t = record.getTier(tier, String.class);
			final String tierData = (t == null ? "" : t.toString());
			setupThreeIntervalTier(textgrid, tgTier, tierData);
		} else {
			double currentStart = textgrid.getXmin();
			double dataEnd = textgrid.getXmax() - MARKER_LENGTH;
			
			for(int i = 0; i < record.numberOfGroups(); i++) {
				final Group group = record.getGroup(i);
				
				// add group marker
				tgTier.addInterval(currentStart, currentStart + MARKER_LENGTH, MARKER_TEXT);
				currentStart += MARKER_LENGTH;
				
				final double groupStart = currentStart;
				final double groupEnd = (i == record.numberOfGroups() - 1 ? dataEnd : groupStart + groupLength);
				if(type == Segmentation.GROUP) {
					String data = "";
					if(systemTier != null) {
						if(systemTier == SystemTierType.Orthography) {
							data = group.getOrthography().toString();
						} else if(systemTier == SystemTierType.IPATarget) {
							data = group.getIPATarget().toString();
						} else if(systemTier == SystemTierType.IPAActual) {
							data = group.getIPAActual().toString();
						}
					} else {
						if(group.getTier(tier) != null)
							data = group.getTier(tier, String.class);
					}
					
					addGroup(tgTier, data, currentStart, groupEnd);
					currentStart = groupEnd;
				} else if(type == Segmentation.WORD) {
					String data = "";
					if(systemTier != null) {
						if(systemTier == SystemTierType.Orthography) {
							data = "";
							for(int wIdx = 0; wIdx < group.getAlignedWordCount(); wIdx++) {
								final String orthoWord = group.getAlignedWord(wIdx).getOrthography().toString();
								data += (data.length() > 0 ? " " : "") + orthoWord;
							}
						} else if(systemTier == SystemTierType.IPATarget) {
							data = group.getIPATarget().toString();
						} else if(systemTier == SystemTierType.IPAActual) {
							data = group.getIPAActual().toString();
						}
					} else {
						if(group.getTier(tier) != null)
							data = group.getTier(tier, String.class);
					}
					
					addWords(tgTier, data, currentStart, groupEnd);
					currentStart = groupEnd;
				} else if(type == Segmentation.SYLLABLE) {
					if(systemTier == SystemTierType.IPATarget ||
							systemTier == SystemTierType.IPAActual) {
						addSyllables(tgTier, systemTier == SystemTierType.IPATarget ? group.getIPATarget() : group.getIPAActual(), currentStart, groupEnd);
						currentStart = groupEnd;
					}
				} else if(type == Segmentation.PHONE) {
					if(systemTier == SystemTierType.IPATarget ||
							systemTier == SystemTierType.IPAActual) {
						addPhones(tgTier, systemTier == SystemTierType.IPATarget ? group.getIPATarget() : group.getIPAActual(), currentStart, groupEnd);
						currentStart = groupEnd;
					}
				}
			}
			// add final marker
			tgTier.addInterval(dataEnd, textgrid.getXmax(), MARKER_TEXT);
		}
		try {
			textgrid.addTier(tgTier);
		} catch (PraatException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	private void addGroup(IntervalTier tier, String groupData, double start, double end) {
		tier.addInterval(start, end, groupData);
	}
	
	private void addWords(IntervalTier tier, String groupData, double start, double end) {
		final String[] words = groupData.split("\\p{Space}");
		final double wordLength = (end - start) / words.length;
		
		double currentStart = start;
		for(int i = 0; i < words.length; i++) {
			String word = words[i];
			tier.addInterval( 
					currentStart, (i == words.length - 1 ? end : currentStart + wordLength), word);
			currentStart += wordLength;
		}
	}
	
	private void addSyllables(IntervalTier tier, IPATranscript ipaGroup, double start, double end) {
		final List<IPATranscript> ipaWords = ipaGroup.words();
		
		// calculate length of each word
		final double dataLength = (end - start);
		final double wordLength = dataLength / ipaWords.size();
		
		// add intervals to tier
		double currentStart = start;
		for(int i = 0; i < ipaWords.size(); i++) {
			if(i > 0) {
				// add space to previous interval
				final TextInterval lastInterval =
						(tier.numberOfIntervals() > 0 ? tier.interval(tier.numberOfIntervals()) : null);
				if(lastInterval != null) {
					try {
						lastInterval.setText(lastInterval.getText() + SPACER_TEXT);
					} catch (PraatException e) {
						LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
					}
				}
			}
			
			final IPATranscript word = ipaWords.get(i);
			final List<IPATranscript> sylls = word.syllables();
			
			final double wordStart = currentStart;
			final double wordEnd = (i == ipaWords.size() - 1 ? end : wordStart + wordLength);
			final double syllLength = wordLength / sylls.size();
			
			for(int j = 0; j < sylls.size(); j++) {
				final IPATranscript syll = sylls.get(j);
				double intEnd = (j == sylls.size() - 1 ? wordEnd : currentStart + syllLength);
				tier.addInterval(currentStart, intEnd, syll.toString());
				currentStart = intEnd;
			}
		}
	}
	
	private void addPhones(IntervalTier tier, IPATranscript ipaGroup, double start, double end) {
		final List<IPATranscript> ipaWords = ipaGroup.words();
		
		// calculate length of each word
		final double dataLength = (end - start);
		final double wordLength = dataLength / ipaWords.size();
		
		// add intervals to tier
		double currentStart = start;
		for(int i = 0; i < ipaWords.size(); i++) {
			if(i > 0) {
				// add space to previous interval
				final TextInterval lastInterval =
						(tier.numberOfIntervals() > 0 ? tier.interval(tier.numberOfIntervals()) : null);
				if(lastInterval != null) {
					try {
						lastInterval.setText(lastInterval.getText() + SPACER_TEXT);
					} catch (PraatException e) {
						LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
					}
				}
			}
			
			final IPATranscript word = ipaWords.get(i);
			final List<IPATranscript> sylls = word.syllables();
			
			final double wordStart = currentStart;
			final double wordEnd = (i == ipaWords.size() - 1 ? end : currentStart + wordLength);
			final double syllLength = wordLength / sylls.size();
			
			for(int j = 0; j < sylls.size(); j++) {
				final IPATranscript syll = sylls.get(j);
				final double syllStart = currentStart;
				final double syllEnd = (j == sylls.size() - 1 ? wordEnd: syllStart + syllLength);
				
				final List<String> intervals = new ArrayList<String>();
				IPAElement prevPhone = null;
				for(IPAElement p:syll) {
					if(p.getScType() == SyllableConstituentType.SYLLABLEBOUNDARYMARKER ||
							p.getScType() == SyllableConstituentType.SYLLABLESTRESSMARKER) {
						prevPhone = p;
					} else {
						String text = p.toString();
						if(prevPhone != null) {
							text = prevPhone.toString() + text;
							prevPhone = null;
						}
						intervals.add(text);
					}
				}
				
				// add phone intervals
				final double phoneLength = syllLength / intervals.size();
				
				for(int k = 0; k < intervals.size(); k++) {
					final String text = intervals.get(k);
					final double phoneStart = currentStart;
					final double phoneEnd = (k == intervals.size() - 1 ? syllEnd : currentStart + phoneLength);
					tier.addInterval(phoneStart, phoneEnd, text);
					currentStart = phoneEnd;
				}
			}
		}
	}
	
	/*
	 * Helper methods for adding tiers to TextGrids
	 */
	private void setupThreeIntervalTier(TextGrid textgrid, IntervalTier tier, String data) {
		final double start = textgrid.getXmin();
		
		double currentStart = start;
		tier.addInterval(start, start+MARKER_LENGTH, MARKER_TEXT);
		currentStart += MARKER_LENGTH;
		
		final double dataEnd = textgrid.getXmax() - MARKER_LENGTH;
		tier.addInterval(currentStart, dataEnd, data);
		currentStart = dataEnd;
		
		tier.addInterval(currentStart, textgrid.getXmax(), MARKER_TEXT);
	}
	
//	public void addTierToTextGrid(IUtterance utt, String tier,  ExportType type,
//			TextGrid textgrid, String tgName) {
//		// create the new textgrid tier
//		final TextGridTier tgTier = new TextGridTier(tgName, TextGridTierType.INTERVAL);
//		textgrid.addTier(tgTier);
//		
//		final SystemTierType systemTier = SystemTierType.tierFromString(tier);
//		final List<String> intervals = new ArrayList<String>();
//		
//		// handle 'TIER' case here
//		if(type == ExportType.TIER) {
//			intervals.add("#");
//			intervals.add(utt.getTierString(tier));
//			intervals.add("#");
//		} else {
//			if(systemTier != null) {
//				
//				if(systemTier == SystemTierType.Orthography) {
//					switch(type) {
//					case GROUP:
//						for(IWord word:utt.getWords()) {
//							intervals.add(MARKER_TEXT);
//							intervals.add(word.getWord());
//						}
//						intervals.add(MARKER_TEXT);
//						break;
//						
//					default:
//						for(IWord group:utt.getWords()) {
//							intervals.add(MARKER_TEXT);
//							final String[] split = group.getWord().split("\\p{Space}");
//							int widx = 0;
//							for(String word:split) {
//								if(widx++ > 0) intervals.add(SPACER_TEXT);
//								intervals.add(word);
//							}
//						}
//						intervals.add(MARKER_TEXT);
//						break;
//					}
//				} else if(systemTier == SystemTierType.IPATarget || systemTier == SystemTierType.IPAActual) {
//					switch(type) {
//					case GROUP:
//						for(IWord word:utt.getWords()) {
//							intervals.add(MARKER_TEXT);
//							final IPhoneticRep phoRep = word.getPhoneticRepresentation(
//									(systemTier == SystemTierType.IPATarget ? Form.Target : Form.Actual));
//							intervals.add(phoRep.getTranscription());
//						}
//						intervals.add(MARKER_TEXT);
//						break;
//						
//					case WORD:
//						for(IWord word:utt.getWords()) {
//							intervals.add(MARKER_TEXT);
//							final IPhoneticRep phoRep = word.getPhoneticRepresentation(
//									(systemTier == SystemTierType.IPATarget ? Form.Target : Form.Actual));
//							final String group = phoRep.getTranscription();
//							final String[] words = group.split("\\p{Space}");
//							int widx = 0;
//							for(String w:words) {
//								if(widx++ > 0) intervals.add(SPACER_TEXT);
//								intervals.add(w);
//							}
//						}
//						intervals.add(MARKER_TEXT);
//						break;
//						
//					case SYLLABLE:
//						for(IWord word:utt.getWords()) {
//							intervals.add(MARKER_TEXT);
//							final IPhoneticRep phoRep = word.getPhoneticRepresentation(
//									(systemTier == SystemTierType.IPATarget ? Form.Target : Form.Actual));
//							
//							final List<Phone> currentWord = new ArrayList<Phone>();
//							for(Phone p:phoRep.getPhones()) {
//								int widx = 0;
//								if(p.getScType() == SyllableConstituentType.WordBoundaryMarker) {
//									if(currentWord.size() > 0) {
//										if(widx++ > 0) intervals.add(SPACER_TEXT);
//										final List<Syllable> sylls = Syllabifier.getSyllabification(currentWord);
//										for(Syllable syll:sylls) {
//											intervals.add(syll.toString());
//										}
//									}
//								}
//							}
//						}
//						intervals.add(MARKER_TEXT);
//						break;
//						
//					case PHONE:
//						for(IWord word:utt.getWords()) {
//							intervals.add(MARKER_TEXT);
//							final IPhoneticRep phoRep = word.getPhoneticRepresentation(
//									(systemTier == SystemTierType.IPATarget ? Form.Target : Form.Actual));
//							
//							final List<Phone> currentInterval = new ArrayList<Phone>();
//							for(Phone p:phoRep.getPhones()) {
//								if(p.getScType() == SyllableConstituentType.SyllableBoundaryMarker
//										|| p.getScType() == SyllableConstituentType.SyllableStressMarker) {
//									currentInterval.add(p);
//								} else {
//									String intervalText = "";
//									for(Phone ph:currentInterval) {
//										intervalText += ph.getPhoneString();
//									}
//									intervalText += p.getPhoneString();
//									intervals.add(intervalText);
//									currentInterval.clear();
//								}
//							}
//						}
//						intervals.add(MARKER_TEXT);
//						break;
//					}
//				} else {
//					intervals.add("#");
//					intervals.add(utt.getTierString(tier));
//					intervals.add("#");
//				}
//			} else {
//				// user-defined tier
//				if(utt.getWordAlignedTierNames().contains(tier)) {
//					for(IWord word:utt.getWords()) {
//						intervals.add(MARKER_TEXT);
//						final IDependentTier depTier = word.getDependentTier(tier);
//						intervals.add(depTier.getTierValue());
//					}
//					intervals.add(MARKER_TEXT);
//				} else {
//					intervals.add("#");
//					intervals.add(utt.getTierString(tier));
//					intervals.add("#");
//				}
//			}
//		}
//		
//		// create textgrid invervals
//		int markerCount = 0;
//		int spacerCount = 0;
//		for(String interval:intervals) {
//			if(interval.equals(MARKER_TEXT)) {
//				markerCount++;
//			} else if(interval.equals(SPACER_TEXT)) {
//				spacerCount++;
//			}
//		}
//		
//		final float tgLength = textgrid.getMax() - textgrid.getMin();
//		final float intervalsLength = tgLength - (MARKER_LENGTH * markerCount) - (SPACER_LENGTH * spacerCount);
//		final float intervalLength = intervalsLength / (intervals.size() - markerCount - spacerCount);
//		
//		float currentStart = textgrid.getMin();
//		for(String interval:intervals) {
//			float len = intervalLength;
//			if(interval.equals(MARKER_TEXT)) {
//				len = MARKER_LENGTH;
//			} else if(interval.equals(SPACER_TEXT)) {
//				len = SPACER_LENGTH;
//			}
//			final TextGridInterval tgInt = new TextGridInterval(interval, currentStart, currentStart + len);
//			tgTier.addInterval(tgInt);
//			currentStart += len;
//		}
//	}
	
	/**
	 * Create an empty textgrid from the given record.
	 * Initializes the TextGrid with proper min/max times
	 * 
	 * @param utt
	 */
	public TextGrid createEmptyTextGrid(Record utt) {
		double startTime = Integer.MAX_VALUE, endTime = Integer.MIN_VALUE;
		
		final Tier<MediaSegment> segmentTier = utt.getSegment();
		if(segmentTier.numberOfGroups() == 0) return null;
		final MediaSegment media = segmentTier.getGroup(0);
		
		if(media != null) {
			double st = media.getStartValue();
			if(media.getUnitType() == MediaUnit.Millisecond)
				st /= 1000.0f;
			double et = media.getEndValue();
			if(media.getUnitType() == MediaUnit.Millisecond)
				et /= 1000.0f;
			
			startTime = Math.min(startTime, st);
			endTime = Math.max(endTime, et);
		}
		
		try {
			return TextGrid.createWithoutTiers(startTime, endTime);
		} catch (PraatException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			return null;
		}
	}
	
	/**
	 * Export text grids for the specified session to the
	 * given folder.
	 * 
	 * @param session
	 * @param recordFilter
	 * @param exports
	 * @param outputFolder
	 * 
	 * @throws IOException
	 */
	public void exportTextGrids(Session session, RecordFilter recordFilter, List<TextGridExportEntry> exports, String outputFolder) 
		throws IOException {
		final File folder = new File(outputFolder);
		if(!folder.exists()) {
			folder.mkdirs();
		}
		if(!folder.isDirectory()) {
			throw new IllegalArgumentException("Specified path is not a folder: " + outputFolder);
		}
		
		for(int i = 0; i < session.getRecordCount(); i++) {
			final Record utt = session.getRecord(i);
			if(recordFilter.checkRecord(utt)) {
				// export text grid
				final TextGrid tg = new TextGrid();
				setupTextGrid(utt, tg, exports);
				
				// save text grid to file
				final String tgFilename = 
						utt.getUuid().toString() + (utt.getSpeaker() != null ? utt.getSpeaker().getName() : "") + ".TextGrid";
				final File tgFile = new File(folder, tgFilename);
				
				TextGridManager.saveTextGrid(tg, tgFile);
			}
		}
	}
	
	public void exportTextGrids(Project project, Session session, RecordFilter recordFilter, List<TextGridExportEntry> exports, boolean overwrite) 
		throws IOException {
		final TextGridManager tgManager = new TextGridManager(project);
		
		for(int i = 0; i < session.getRecordCount(); i++) {
			final Record utt = session.getRecord(i);
			if(recordFilter.checkRecord(utt)) {
				TextGrid tg = tgManager.loadTextGrid(utt.getUuid().toString());
				if(tg != null && !overwrite) {
					continue;
				}
				
				// export text grid
				tg = createEmptyTextGrid(utt);
				setupTextGrid(utt, tg, exports);
				
				tgManager.saveTextGrid(tg, utt.getUuid().toString());
			}
		}
	}
	
	/**
	 * Get the location of the audio file.
	 * 
	 */
	public File getAudioFile(Project project, Session session) {
		File selectedMedia = 
				MediaLocator.findMediaFile(project, session);
		if(selectedMedia == null) return null;
		File audioFile = null;
		
		int lastDot = selectedMedia.getName().lastIndexOf('.');
		String mediaName = selectedMedia.getName();
		if(lastDot >= 0) {
			mediaName = mediaName.substring(0, lastDot);
		}
		if(!selectedMedia.isAbsolute()) selectedMedia = 
			MediaLocator.findMediaFile(session.getMediaLocation(), project, session.getCorpus());
		
		if(selectedMedia != null) {
			File parentFile = selectedMedia.getParentFile();
			audioFile = new File(parentFile, mediaName + ".wav");
			
			if(!audioFile.exists()) {
				audioFile = null;
			}
		}
		return audioFile;
	}
	
	public void exportTextGrids(Project project, Session session, RecordFilter recordFilter, List<TextGridExportEntry> exports, 
			String outputFolder, boolean copyExisting) 
			throws IOException {
		final TextGridManager tgManager = new TextGridManager(project);
		
		final File folder = new File(outputFolder);
		if(!folder.exists()) {
			folder.mkdirs();
		}
		if(!folder.isDirectory()) {
			throw new IllegalArgumentException("Specified path is not a folder: " + outputFolder);
		}
		
		for(int i = 0; i < session.getRecordCount(); i++) {
			final Record utt = session.getRecord(i);
			if(recordFilter.checkRecord(utt)) {
				TextGrid tg = tgManager.loadTextGrid(utt.getUuid().toString());
				
				// export text grid
				if(tg == null || (tg != null && !copyExisting)) {
					tg = createEmptyTextGrid(utt);
					setupTextGrid(utt, tg, exports);
				}
				
				// save text grid to file
				final String tgPrefix = utt.getUuid().toString() + (utt.getSpeaker() != null ? utt.getSpeaker().getName() : "");
				final String tgFilename = tgPrefix + ".TextGrid";
				final File tgFile = new File(folder, tgFilename);
				
				final PraatScript ps = new PraatScript("ca/phon/plugins/praat/OpenTextGrid.vm");
				final PraatScriptContext map = new PraatScriptContext();
				File mediaFile =
						getAudioFile(project, session);
				if(mediaFile == null) continue;

				final Tier<MediaSegment> mediaTier = utt.getSegment();
				if(mediaTier.numberOfGroups()  == 0) continue;
				final MediaSegment media = mediaTier.getGroup(0);
				
				map.put("soundFile", mediaFile.getAbsolutePath());
				map.put("tgFile", tgFilename);
				map.put("interval", media);
				final String script = ps.generateScript(map);
				
				TextGridManager.saveTextGrid(tg, tgFile);
				
				final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(new File(folder, tgPrefix + ".praatscript")));
				out.write(script.getBytes());
				out.flush();
				out.close();
			}
		}
	}
	
	/**
	 * Setup the given textgrid using settings from the
	 * specified project.
	 * 
	 * @param project
	 * @param utt
	 * @param textgrid
	 */
	public void setupTextGrid(Project project, Record utt, TextGrid textgrid) {
		final List<TextGridExportEntry> exports = getExports(project);
		setupTextGrid(utt, textgrid, exports);
	}
	
	public void setupTextGrid(Record utt, TextGrid textgrid, List<TextGridExportEntry> exports) {
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
		final TextGridExportEntry ortho1 = new TextGridExportEntry(SystemTierType.Orthography.getName(), Segmentation.TIER, 
				SystemTierType.Orthography.getName() + ": " + Segmentation.TIER.toString());
		final TextGridExportEntry ortho2 = new TextGridExportEntry(SystemTierType.Orthography.getName(), Segmentation.GROUP, 
				SystemTierType.Orthography.getName() + ": " + Segmentation.GROUP.toString());
		final TextGridExportEntry ortho3 = new TextGridExportEntry(SystemTierType.Orthography.getName(), Segmentation.WORD, 
				SystemTierType.Orthography.getName() + ": " + Segmentation.WORD.toString());
		retVal.add(ortho1);
		retVal.add(ortho2);
		retVal.add(ortho3);
		
		// ipa actual
		final TextGridExportEntry ipa1 = new TextGridExportEntry(SystemTierType.IPAActual.getName(), Segmentation.SYLLABLE, 
				SystemTierType.IPAActual.getName() + ": " + Segmentation.SYLLABLE.toString());
		final TextGridExportEntry ipa2 = new TextGridExportEntry(SystemTierType.IPAActual.getName(), Segmentation.PHONE, 
				SystemTierType.IPAActual.getName() + ": " + Segmentation.PHONE.toString());
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
	public List<TextGridExportEntry> getExports(Project project) {
		List<TextGridExportEntry> retVal = null;
		
		try {
			final String exportsFile = project.getLocation() + File.separator + EXPORT_DEFAULTS;
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
	public boolean saveExports(List<TextGridExportEntry> exports, Project project) {
		boolean retVal = false;
		try {
			final String exportsFile = project.getLocation() + File.separator + EXPORT_DEFAULTS;
			saveExportsToFile(exports, exportsFile);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
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
