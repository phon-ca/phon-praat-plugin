package ca.phon.plugins.praat;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.phon.application.IPhonFactory;
import ca.phon.application.project.IPhonProject;
import ca.phon.application.transcript.Form;
import ca.phon.application.transcript.IDependentTier;
import ca.phon.application.transcript.IMedia;
import ca.phon.application.transcript.IPhoneticRep;
import ca.phon.application.transcript.ITranscript;
import ca.phon.application.transcript.IUtterance;
import ca.phon.application.transcript.IWord;
import ca.phon.application.transcript.MediaUnit;
import ca.phon.application.transcript.UtteranceFilter;
import ca.phon.engines.syllabifier.Syllabifier;
import ca.phon.gui.recordeditor.SystemTierType;
import ca.phon.media.util.MediaLocator;
import ca.phon.phone.Phone;
import ca.phon.syllable.Syllable;
import ca.phon.syllable.SyllableConstituentType;
import ca.phon.system.logger.PhonLogger;
import ca.phon.textgrid.TextGrid;
import ca.phon.textgrid.TextGridInterval;
import ca.phon.textgrid.TextGridTier;
import ca.phon.textgrid.TextGridTierType;
import ca.phon.textgrid.TextGridWriter;

/**
 * Utility methods for exporting Phon tier data into
 * TextGrids
 * 
 */
public class TextGridExporter {
	
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
	public void addTierToTextGrid(IUtterance utt, String tier, Segmentation type,
			TextGrid textgrid, String tgName) {
		// create the new textgrid tier
		final TextGridTier tgTier = new TextGridTier(tgName, TextGridTierType.INTERVAL);
		textgrid.addTier(tgTier);
		
		// check if we a processing a built-in tier
		final SystemTierType systemTier = SystemTierType.tierFromString(tier);
		
		// calculate some values for interval times
		final int numHashes = utt.getWords().size() + 1;
		final float totalTime = textgrid.getMax() - textgrid.getMin();
		final float hashLength = MARKER_LENGTH * numHashes;
		final float dataLength = totalTime - hashLength;
		final float groupLength = dataLength / utt.getWords().size();
		
		// if the exportType is TIER, we create a 3-interval tier
		// this takes care of all flat tiers
		if(type == Segmentation.TIER) {
			setupThreeIntervalTier(textgrid, tgTier, utt.getTierString(tier));
		} else {
			float currentStart = textgrid.getMin();
			float dataEnd = textgrid.getMax() - MARKER_LENGTH;
			
			final List<IWord> words = utt.getWords();
			for(int i = 0; i < words.size(); i++) {
				// add group marker
				final TextGridInterval marker = new TextGridInterval(MARKER_TEXT, currentStart, currentStart + MARKER_LENGTH);
				tgTier.addInterval(marker);
				currentStart += MARKER_LENGTH;
				
				final IWord word = words.get(i);
				final float groupStart = currentStart;
				final float groupEnd = (i == words.size() - 1 ? dataEnd : groupStart + groupLength);
				if(type == Segmentation.GROUP) {
					String data = "";
					if(systemTier != null) {
						if(systemTier == SystemTierType.Orthography) {
							data = word.getWord();
						} else if(systemTier == SystemTierType.IPATarget) {
							data = word.getPhoneticRepresentation(Form.Target).getTranscription();
						} else if(systemTier == SystemTierType.IPAActual) {
							data = word.getPhoneticRepresentation(Form.Actual).getTranscription();
						}
					} else {
						data = word.getDependentTier(tier).getTierValue();
					}
					
					addGroup(tgTier, data, currentStart, groupEnd);
					currentStart = groupEnd;
				} else if(type == Segmentation.WORD) {
					String data = "";
					if(systemTier != null) {
						if(systemTier == SystemTierType.Orthography) {
							data = word.getWord();
						} else if(systemTier == SystemTierType.IPATarget) {
							data = word.getPhoneticRepresentation(Form.Target).getTranscription();
						} else if(systemTier == SystemTierType.IPAActual) {
							data = word.getPhoneticRepresentation(Form.Actual).getTranscription();
						}
					} else {
						data = word.getDependentTier(tier).getTierValue();
					}
					
					addWords(tgTier, data, currentStart, groupEnd);
					currentStart = groupEnd;
				} else if(type == Segmentation.SYLLABLE) {
					if(systemTier == SystemTierType.IPATarget ||
							systemTier == SystemTierType.IPAActual) {
						final Form form = (systemTier == SystemTierType.IPATarget ? Form.Target : Form.Actual);
						final IPhoneticRep phoRep = word.getPhoneticRepresentation(form);
						
						addSyllables(tgTier, phoRep, currentStart, groupEnd);
						currentStart = groupEnd;
					}
				} else if(type == Segmentation.PHONE) {
					if(systemTier == SystemTierType.IPATarget ||
							systemTier == SystemTierType.IPAActual) {
						final Form form = (systemTier == SystemTierType.IPATarget ? Form.Target : Form.Actual);
						final IPhoneticRep phoRep = word.getPhoneticRepresentation(form);
						
						addPhones(tgTier, phoRep, currentStart, groupEnd);
						currentStart = groupEnd;
					}
				}
			}
			// add final marker
			final TextGridInterval marker = new TextGridInterval(MARKER_TEXT, dataEnd, textgrid.getMax());
			tgTier.addInterval(marker);
		}
	}
	
	private void addGroup(TextGridTier tier, String groupData, float start, float end) {
		final TextGridInterval interval = new TextGridInterval(groupData, start, end);
		tier.addInterval(interval);
	}
	
	private void addWords(TextGridTier tier, String groupData, float start, float end) {
		final String[] words = groupData.split("\\p{Space}");
		final float wordLength = (end - start) / words.length;
		
		float currentStart = start;
		for(int i = 0; i < words.length; i++) {
			String word = words[i];
			final TextGridInterval interval = new TextGridInterval(word, 
					currentStart, (i == words.length - 1 ? end : currentStart + wordLength));
			currentStart += wordLength;
			
			tier.addInterval(interval);
		}
	}
	
	private void addSyllables(TextGridTier tier, IPhoneticRep ipaGroup, float start, float end) {
		// break into words first so we know where to add spaces
		final List<List<Phone>> ipaWords = new ArrayList<List<Phone>>();
		
		List<Phone> currentWord = new ArrayList<Phone>();
		for(Phone p:ipaGroup.getPhones()) {
			if(p.getScType() == SyllableConstituentType.WordBoundaryMarker) {
				if(currentWord.size() > 0) {
					ipaWords.add(currentWord);
					currentWord = new ArrayList<Phone>();
				}
			} else {
				currentWord.add(p);
			}
		}
		if(currentWord.size() > 0) {
			ipaWords.add(currentWord);
		}
		
		// calculate length of each word
		final float dataLength = (end - start);
		final float wordLength = dataLength / ipaWords.size();
		
		// add intervals to tier
		float currentStart = start;
		for(int i = 0; i < ipaWords.size(); i++) {
			if(i > 0) {
				// add space to previous interval
				final TextGridInterval lastInterval =
						(tier.getNumberOfIntervals() > 0 ? tier.getIntervalAt(tier.getNumberOfIntervals()-1) : null);
				if(lastInterval != null) {
					lastInterval.setLabel(lastInterval.getLabel() + SPACER_TEXT);
				}
			}
			
			final List<Phone> word = ipaWords.get(i);
			final List<Syllable> sylls = Syllabifier.getSyllabification(word);
			
			final float wordStart = currentStart;
			final float wordEnd = (i == ipaWords.size() - 1 ? end : wordStart + wordLength);
			final float syllLength = wordLength / sylls.size();
			
			for(int j = 0; j < sylls.size(); j++) {
				final Syllable syll = sylls.get(j);
				float intEnd = (j == sylls.size() - 1 ? wordEnd : currentStart + syllLength);
				final TextGridInterval interval = new TextGridInterval(syll.toString(),
						currentStart, intEnd);
				tier.addInterval(interval);
				currentStart = intEnd;
			}
		}
	}
	
	private void addPhones(TextGridTier tier, IPhoneticRep ipaGroup, float start, float end) {
		// break into words first so we know where to add spaces
		final List<List<Phone>> ipaWords = new ArrayList<List<Phone>>();
		
		List<Phone> currentWord = new ArrayList<Phone>();
		for(Phone p:ipaGroup.getPhones()) {
			if(p.getScType() == SyllableConstituentType.WordBoundaryMarker) {
				if(currentWord.size() > 0) {
					ipaWords.add(currentWord);
					currentWord = new ArrayList<Phone>();
				}
			} else {
				currentWord.add(p);
			}
		}
		if(currentWord.size() > 0) {
			ipaWords.add(currentWord);
		}
		
		// calculate length of each word
		final float dataLength = (end - start);
		final float wordLength = dataLength / ipaWords.size();
		
		// add intervals to tier
		float currentStart = start;
		for(int i = 0; i < ipaWords.size(); i++) {
			if(i > 0) {
				// add space to previous interval
				final TextGridInterval lastInterval =
						(tier.getNumberOfIntervals() > 0 ? tier.getIntervalAt(tier.getNumberOfIntervals()-1) : null);
				if(lastInterval != null) {
					lastInterval.setLabel(lastInterval.getLabel() + SPACER_TEXT);
				}
			}
			
			final List<Phone> word = ipaWords.get(i);
			final List<Syllable> sylls = Syllabifier.getSyllabification(word);
			
			final float wordStart = currentStart;
			final float wordEnd = (i == ipaWords.size() - 1 ? end : currentStart + wordLength);
			final float syllLength = wordLength / sylls.size();
			
			for(int j = 0; j < sylls.size(); j++) {
				final Syllable syll = sylls.get(j);
				final float syllStart = currentStart;
				final float syllEnd = (j == sylls.size() - 1 ? wordEnd: syllStart + syllLength);
				
				final List<String> intervals = new ArrayList<String>();
				Phone prevPhone = null;
				for(Phone p:syll.getPhones()) {
					if(p.getScType() == SyllableConstituentType.SyllableBoundaryMarker ||
							p.getScType() == SyllableConstituentType.SyllableStressMarker) {
						prevPhone = p;
					} else {
						String text = p.getPhoneString();
						if(prevPhone != null) {
							text = prevPhone.getPhoneString() + text;
							prevPhone = null;
						}
						intervals.add(text);
					}
				}
				
				// add phone intervals
				final float phoneLength = syllLength / intervals.size();
				
				for(int k = 0; k < intervals.size(); k++) {
					final String text = intervals.get(k);
					final float phoneStart = currentStart;
					final float phoneEnd = (k == intervals.size() - 1 ? syllEnd : currentStart + phoneLength);
					final TextGridInterval interval = new TextGridInterval(text, 
							phoneStart, phoneEnd);
					tier.addInterval(interval);
					currentStart = phoneEnd;
				}
			}
		}
	}
	
	/*
	 * Helper methods for adding tiers to TextGrids
	 */
	private void setupThreeIntervalTier(TextGrid textgrid, TextGridTier tier, String data) {
		final float start = textgrid.getMin();
		
		float currentStart = start;
		final TextGridInterval firstHash = new TextGridInterval(MARKER_TEXT, start, start+MARKER_LENGTH);
		currentStart += MARKER_LENGTH;
		
		final float dataEnd = textgrid.getMax() - MARKER_LENGTH;
		final TextGridInterval dataInterval = new TextGridInterval(data, currentStart, dataEnd);
		currentStart = dataEnd;
		
		final TextGridInterval lastHash = new TextGridInterval(MARKER_TEXT, currentStart, textgrid.getMax());
		
		tier.addInterval(firstHash);
		tier.addInterval(dataInterval);
		tier.addInterval(lastHash);
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
	public void exportTextGrids(ITranscript session, UtteranceFilter recordFilter, List<TextGridExportEntry> exports, String outputFolder) 
		throws IOException {
		final File folder = new File(outputFolder);
		if(!folder.exists()) {
			folder.mkdirs();
		}
		if(!folder.isDirectory()) {
			throw new IllegalArgumentException("Specified path is not a folder: " + outputFolder);
		}
		
		for(int i = 0; i < session.getNumberOfUtterances(); i++) {
			final IUtterance utt = session.getUtterance(i);
			if(recordFilter.checkUtterance(utt)) {
				// export text grid
				final TextGrid tg = new TextGrid();
				setupTextGrid(utt, tg, exports);
				
				// save text grid to file
				final String tgFilename = 
						utt.getID() + (utt.getSpeaker() != null ? utt.getSpeaker().getName() : "") + ".TextGrid";
				final File tgFile = new File(folder, tgFilename);
				
				TextGridManager.saveTextGrid(tg, tgFile.getAbsolutePath());
			}
		}
	}
	
	public void exportTextGrids(IPhonProject project, ITranscript session, UtteranceFilter recordFilter, List<TextGridExportEntry> exports, boolean overwrite) 
		throws IOException {
		final TextGridManager tgManager = TextGridManager.getInstance(project);
		
		for(int i = 0; i < session.getNumberOfUtterances(); i++) {
			final IUtterance utt = session.getUtterance(i);
			if(recordFilter.checkUtterance(utt)) {
				TextGrid tg = tgManager.loadTextGrid(session.getCorpus(), session.getID(), utt.getID());
				if(tg != null && !overwrite) {
					continue;
				}
				
				// export text grid
				tg = createEmptyTextGrid(utt);
				setupTextGrid(utt, tg, exports);
				
				tgManager.saveTextGrid(tg, session.getCorpus(), session.getID(), utt.getID());
			}
		}
	}
	
	/**
	 * Get the location of the audio file.
	 * 
	 */
	public File getAudioFile(IPhonProject project, ITranscript session) {
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
	
	public void exportTextGrids(IPhonProject project, ITranscript session, UtteranceFilter recordFilter, List<TextGridExportEntry> exports, 
			String outputFolder, boolean copyExisting) 
			throws IOException {
		final TextGridManager tgManager = TextGridManager.getInstance(project);
		
		final File folder = new File(outputFolder);
		if(!folder.exists()) {
			folder.mkdirs();
		}
		if(!folder.isDirectory()) {
			throw new IllegalArgumentException("Specified path is not a folder: " + outputFolder);
		}
		
		for(int i = 0; i < session.getNumberOfUtterances(); i++) {
			final IUtterance utt = session.getUtterance(i);
			if(recordFilter.checkUtterance(utt)) {
				TextGrid tg = tgManager.loadTextGrid(session.getCorpus(), session.getID(), utt.getID());
				
				// export text grid
				if(tg == null || (tg != null && !copyExisting)) {
					tg = createEmptyTextGrid(utt);
					setupTextGrid(utt, tg, exports);
				}
				
				// save text grid to file
				final String tgPrefix = utt.getID() + (utt.getSpeaker() != null ? utt.getSpeaker().getName() : "");
				final String tgFilename = tgPrefix + ".TextGrid";
				final File tgFile = new File(folder, tgFilename);
				
				final PraatScript ps = new PraatScript();
				final Map<String, Object> map = new HashMap<>();
				File mediaFile =
						getAudioFile(project, session);
				if(mediaFile == null) continue;

				final IMedia media = utt.getMedia();
				if(media == null) continue;
				
				map.put("soundFile", mediaFile.getAbsolutePath());
				map.put("tgFile", tgFilename);
				map.put("interval", media);
				final String script = ps.generateScript("ca/phon/plugins/praat/OpenTextGrid.vm", map);
				
				TextGridManager.saveTextGrid(tg, tgFile.getAbsolutePath());
				
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
	public void setupTextGrid(IPhonProject project, IUtterance utt, TextGrid textgrid) {
		final List<TextGridExportEntry> exports = getExports(project);
		setupTextGrid(utt, textgrid, exports);
	}
	
	public void setupTextGrid(IUtterance utt, TextGrid textgrid, List<TextGridExportEntry> exports) {
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
		final TextGridExportEntry ortho1 = new TextGridExportEntry(SystemTierType.Orthography.getTierName(), Segmentation.TIER, 
				SystemTierType.Orthography.getTierName() + ": " + Segmentation.TIER.toString());
		final TextGridExportEntry ortho2 = new TextGridExportEntry(SystemTierType.Orthography.getTierName(), Segmentation.GROUP, 
				SystemTierType.Orthography.getTierName() + ": " + Segmentation.GROUP.toString());
		final TextGridExportEntry ortho3 = new TextGridExportEntry(SystemTierType.Orthography.getTierName(), Segmentation.WORD, 
				SystemTierType.Orthography.getTierName() + ": " + Segmentation.WORD.toString());
		retVal.add(ortho1);
		retVal.add(ortho2);
		retVal.add(ortho3);
		
		// ipa actual
		final TextGridExportEntry ipa1 = new TextGridExportEntry(SystemTierType.IPAActual.getTierName(), Segmentation.SYLLABLE, 
				SystemTierType.IPAActual.getTierName() + ": " + Segmentation.SYLLABLE.toString());
		final TextGridExportEntry ipa2 = new TextGridExportEntry(SystemTierType.IPAActual.getTierName(), Segmentation.PHONE, 
				SystemTierType.IPAActual.getTierName() + ": " + Segmentation.PHONE.toString());
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
