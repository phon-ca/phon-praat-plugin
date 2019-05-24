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
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.IntervalTier;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.binding.fon.TextPoint;
import ca.hedlund.jpraat.binding.fon.TextTier;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.ipa.IPAElement;
import ca.phon.ipa.IPATranscript;
import ca.phon.ipa.IntraWordPause;
import ca.phon.media.sampled.PCMSampled;
import ca.phon.media.sampled.Sampled;
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
import ca.phon.session.Word;
import ca.phon.syllable.SyllableConstituentType;

/**
 * Utility methods for exporting Phon tier data into
 * TextGrids
 *
 */
public class TextGridExporter {

	private static final Logger LOGGER = Logger
			.getLogger(TextGridExporter.class.getName());

	/** Default space length in ms */
	public final static float SPACER_LENGTH = 0.01f;

	public final static String SPACER_TEXT = " ";

	/**
	 * Location of text grid export defaults
	 */
	private final static String EXPORT_DEFAULTS = "__res" + File.separator +
		"textgrids" + File.separator + "exportdefaults.xml";

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

	public IntervalTier findIntervalTier(TextGrid textGrid, String tgName) {
		for(long i = 1; i <= textGrid.numberOfTiers(); i++) {
			try {
				IntervalTier it = textGrid.checkSpecifiedTierIsIntervalTier(i);
				if(it.getName().toString().equals(tgName)) {
					return it;
				}
			} catch (PraatException pe) {
				// ignore, could be just a point tier
			}
		}
		return null;
	}

	public TextTier findPointTier(TextGrid textGrid, String tgName) {
		for(long i = 1; i <= textGrid.numberOfTiers(); i++) {
			try {
				TextTier tt = textGrid.checkSpecifiedTierIsPointTier(i);
				if(tt.getName().toString().equals(tgName)) {
					return tt;
				}
			} catch (PraatException pe) {
				// ignore, could be just an interval tier
			}
		}
		return null;
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
		IntervalTier tgTier = findIntervalTier(textgrid, tgName);
		if(tgTier == null) return;

		final MediaSegment mediaSeg = record.getSegment().getGroup(0);
		if(mediaSeg == null || (mediaSeg.getEndValue() - mediaSeg.getStartValue()) <= 0) return;
		double startTime = mediaSeg.getStartValue() / 1000.0;
		double endTime = mediaSeg.getEndValue() / 1000.0;

		final TextInterval lastInterval =
				(tgTier.numberOfIntervals() > 0 ? tgTier.interval(tgTier.numberOfIntervals()) : null);
		final double lastIntervalEnd =
				(lastInterval != null ? lastInterval.getXmax() : 0.0);
		if(lastIntervalEnd < startTime) {
			// add a new empty interval
			tgTier.addInterval(lastIntervalEnd, startTime, "");
		} else if(lastIntervalEnd > startTime) {
			// adjust start time for this record
			startTime = lastIntervalEnd;
		}

		// check if we a processing a built-in tier
		final SystemTierType systemTier = SystemTierType.tierFromString(tier);

		// calculate some values for interval times
		final double totalTime = endTime - startTime;
		final double groupLength = totalTime / record.numberOfGroups();

		// if the exportType is TIER, we create a 3-interval tier
		// this takes care of all flat tiers
		if(type == Segmentation.TIER) {
			final Tier<String> t = record.getTier(tier, String.class);
			final String tierData = (t == null ? "" : t.toString());
			setupSingleIntervalTier(textgrid, tgTier, tierData, startTime, endTime);
		} else {
			double currentStart = startTime;
			double dataEnd = endTime;

			for(int i = 0; i < record.numberOfGroups(); i++) {
				final Group group = record.getGroup(i);

				final double groupStart = currentStart;
				final double groupEnd = (i == record.numberOfGroups() - 1 ? dataEnd : groupStart + groupLength);

				if(type == Segmentation.GROUP) {
					String data = "";
					if(systemTier != null) {
						if(systemTier == SystemTierType.Orthography) {
							data = (group.getOrthography() != null ? group.getOrthography().toString() : "");
						} else if(systemTier == SystemTierType.IPATarget) {
							data = (group.getIPATarget() != null ? group.getIPATarget().toString() : "");
						} else if(systemTier == SystemTierType.IPAActual) {
							data = (group.getIPAActual() != null ? group.getIPAActual().toString() : "");
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
							for(int wIdx = 0; wIdx < group.getAlignedWordCount(); wIdx++) {
								final Word alignedWord = group.getAlignedWord(wIdx);
								final String orthoWord = (alignedWord != null && alignedWord.getOrthography() != null ? 
										alignedWord.getOrthography().toString() : "");
								data += (orthoWord.length() > 0 ? (data.length() > 0 ? " " : "") + orthoWord : "");
							}
						} else if(systemTier == SystemTierType.IPATarget) {
							data = (group.getIPATarget() != null ? group.getIPATarget().toString() : "");
						} else if(systemTier == SystemTierType.IPAActual) {
							data = (group.getIPAActual() != null ? group.getIPAActual().toString() : "");
						}
					} else {
						if(group.getTier(tier) != null)
							data = (group.getTier(tier) != null ? group.getTier(tier, String.class) : "");
					}

					addWords(tgTier, data, currentStart, groupEnd);
					currentStart = groupEnd;
				} else if(type == Segmentation.SYLLABLE) {
					if(systemTier == SystemTierType.IPATarget ||
							systemTier == SystemTierType.IPAActual) {
						final IPATranscript ipa =
								(systemTier == SystemTierType.IPATarget ? group.getIPATarget() : group.getIPAActual());
						if(ipa.length() == 0) {
							tgTier.addInterval(groupStart, groupEnd, "");
						} else {
							addSyllables(tgTier, systemTier == SystemTierType.IPATarget ? group.getIPATarget() : group.getIPAActual(), currentStart, groupEnd);
						}
						currentStart = groupEnd;
					}
				} else if(type == Segmentation.PHONE) {
					if(systemTier == SystemTierType.IPATarget ||
							systemTier == SystemTierType.IPAActual) {
						final IPATranscript ipa =
								(systemTier == SystemTierType.IPATarget ? group.getIPATarget() : group.getIPAActual());
						if(ipa.length() == 0) {
							tgTier.addInterval(groupStart, groupEnd, "");
						} else {
							addPhones(tgTier, systemTier == SystemTierType.IPATarget ? group.getIPATarget() : group.getIPAActual(), currentStart, groupEnd);
						}
						currentStart = groupEnd;
					}
				}
			}
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
			final List<IPATranscript> sylls = word.syllablesAndPauses();

			if(sylls.size() > 0) {
				final double wordStart = currentStart;
				final double wordEnd = (i == ipaWords.size() - 1 ? end : wordStart + wordLength);
				final double syllLength = wordLength / sylls.size();

				for(int j = 0; j < sylls.size(); j++) {
					final IPATranscript syll = sylls.get(j);
					double intEnd = (j == sylls.size() - 1 ? wordEnd : currentStart + syllLength);
					tier.addInterval(currentStart, intEnd, syll.toString());
					currentStart = intEnd;
				}
			} else {
				// add interval for entire word - this is usually a pause
				tier.addInterval(currentStart, currentStart+wordLength, word.toString());
				currentStart = currentStart+wordLength;
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
			final List<IPATranscript> sylls = word.syllablesAndPauses();

			if(sylls.size() > 0) {
				final double wordStart = currentStart;
				final double wordEnd = (i == ipaWords.size() - 1 ? end : wordStart + wordLength);
				final double syllLength = wordLength / sylls.size();

				for(int j = 0; j < sylls.size(); j++) {
					final IPATranscript syll = sylls.get(j);
					final double syllStart = currentStart;
					final double syllEnd = (j == sylls.size() - 1 ? wordEnd: syllStart + syllLength);

					final List<String> intervals = new ArrayList<String>();
					IPAElement prevPhone = null;
					for(IPAElement p:syll) {
						if(!(p instanceof IntraWordPause) && (p.getScType() == SyllableConstituentType.SYLLABLEBOUNDARYMARKER ||
								p.getScType() == SyllableConstituentType.SYLLABLESTRESSMARKER)) {
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
			} else {
				// add interval for entire word - this is usually a pause
				tier.addInterval(currentStart, currentStart+wordLength, word.toString());
				currentStart = currentStart+wordLength;
			}
		}
	}

	/*
	 * Helper methods for adding tiers to TextGrids
	 */
	private void setupSingleIntervalTier(TextGrid textgrid, IntervalTier tier, String data, double startTime, double endTime) {
		double currentStart = startTime;
		final double dataEnd = endTime;
		tier.addInterval(currentStart, dataEnd, data);
	}

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
	 * Add record to TextGrid.
	 *
	 * @param record
	 * @param textGrid
	 * @param exports
	 * @param overwrite
	 *
	 * @throws IOException
	 */
	public void addRecordToTextGrid(Record record, TextGrid textGrid, List<TextGridExportEntry> exports, boolean overwrite) {
		final MediaSegment seg = record.getSegment().getGroup(0);
		if(seg == null) return;

		double startTime = (double)(seg.getStartValue() / 1000.0);
		double endTime = (double)(seg.getEndValue() / 1000.0);
		if(startTime < textGrid.getXmin() || endTime > textGrid.getXmax()) return;

		for(TextGridExportEntry entry:exports) {
			addTierToTextGrid(record, textGrid, entry);
		}
	}

	/**
	 * Create text grid for the specified session with given name.
	 *
	 * @param session
	 * @param recordFilter
	 * @param exports
	 * @param name
	 * @param appendTiers if a TextGrid already exists at <code>file</code> add
	 *  tiers to the existing TextGrid
	 *
	 * @throws IOException
	 */
	public void generateTextGrid(Project project, Session session, RecordFilter recordFilter,
			List<TextGridExportEntry> exports, File file, boolean appendTiers) throws IOException {
		// get audio file
		final File audioFile = getAudioFile(project, session);
		if(audioFile == null) throw new IOException("Unable to open audio");

		final Sampled sampled = new PCMSampled(audioFile);
		if(sampled.getLength() == 0) throw new IOException("Unable to read audio file");

		final TextGridManager manager = new TextGridManager(project);

		final AtomicReference<TextGrid> textGridRef = new AtomicReference<TextGrid>(null);
		if(file.exists() && appendTiers) {
			textGridRef.set(TextGridManager.loadTextGrid(file));
		} else {
			try {
				textGridRef.set(TextGrid.createWithoutTiers(0.0, (double)sampled.getLength()));
			} catch (PraatException e) {
				throw new IOException(e);
			}
		}

		try {
			setupTextGridTiers(textGridRef.get(), exports);

			for(Record record:session.getRecords()) {
				if(recordFilter != null && !recordFilter.checkRecord(record)) continue;

				exports.forEach( (TextGridExportEntry entry) -> {
					addTierToTextGrid(record, textGridRef.get(), entry);
				} );
			}

			for(int tierIdx = 1; tierIdx <= textGridRef.get().numberOfTiers(); tierIdx++) {
				try {
					final IntervalTier intervalTier =
							textGridRef.get().checkSpecifiedTierIsIntervalTier(tierIdx);
					final TextInterval lastInterval =
							(intervalTier.numberOfIntervals() > 0 ? intervalTier.interval(intervalTier.numberOfIntervals()) : null);
					double lastIntervalStart =
							(lastInterval != null ? lastInterval.getXmax() : 0.0);
					double lastIntervalEnd = intervalTier.getXmax();
					if(lastIntervalStart < lastIntervalEnd) {
						intervalTier.addInterval(lastIntervalStart, lastIntervalEnd, "");
					}
				} catch (PraatException e) {
					// ignore
				}
			}

			TextGridManager.saveTextGrid(textGridRef.get(), file);
		} catch (PraatException pe) {
			throw new IOException(pe);
		}
	}

	/**
	 * Copy textgrid data from the old textgrid object to the new one.
	 * Will only copy tiers which exist in the new textgrid.  The method
	 * returns true if any TextGrid data is copied, <code>false</code>
	 * otherwise.
	 *
	 * @param oldTextGrid
	 * @param textGrid
	 * @param record
	 *
	 * throws PraatException
	 */
	private boolean copyRecordData(TextGrid oldTextGrid, TextGrid textGrid, Record record)
		throws PraatException {
		boolean retVal = false;

		final MediaSegment mediaSeg = record.getSegment().getGroup(0);
		if(mediaSeg == null ||
				(mediaSeg.getEndValue() - mediaSeg.getStartValue()) <= 0) {
			return retVal;
		}

		double startTime = mediaSeg.getStartValue() / 1000.0;
		double endTime = mediaSeg.getEndValue() / 1000.0;

		final TextGrid recordTextGrid = oldTextGrid.extractPart(startTime, endTime, true);
		for(long tierIdx = 1; tierIdx <= recordTextGrid.numberOfTiers(); tierIdx++) {
			try {
				final IntervalTier oldTier = recordTextGrid.checkSpecifiedTierIsIntervalTier(tierIdx);
				final IntervalTier newTier = findIntervalTier(textGrid, oldTier.getName().toString());

				if(newTier != null) {
					for(long intervalIdx = 1; intervalIdx <= oldTier.numberOfIntervals(); intervalIdx++) {
						final TextInterval oldInterval = oldTier.interval(intervalIdx);
						newTier.addInterval(oldInterval.getXmin(), oldInterval.getXmax(), oldInterval.getText());
						retVal = true;
					}
				}
			} catch (PraatException pe) {
				final TextTier oldTier = recordTextGrid.checkSpecifiedTierIsPointTier(tierIdx);
				final TextTier newTier = findPointTier(textGrid, oldTier.getName().toString());

				if(newTier != null) {
					for(long pointIdx = 1; pointIdx <= oldTier.numberOfPoints(); pointIdx++) {
						final TextPoint textPoint = oldTier.point(pointIdx);
						newTier.addPoint(textPoint.getNumber(), textPoint.getText());
						retVal = true;
					}
				}
			}
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
	@Deprecated
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
				try {
					// export text grid
					final TextGrid tg = new TextGrid();
					setupTextGrid(utt, tg, exports);

					// save text grid to file
					final String tgFilename =
							utt.getUuid().toString() + (utt.getSpeaker() != null ? utt.getSpeaker().getName() : "") + ".TextGrid";
					final File tgFile = new File(folder, tgFilename);

					TextGridManager.saveTextGrid(tg, tgFile);
				} catch (PraatException pe) {
					throw new IOException(pe);
				}
			}
		}
	}

	@Deprecated
	public void exportTextGrids(Project project, Session session,
			RecordFilter recordFilter, List<TextGridExportEntry> exports, boolean overwrite)
		throws IOException {
		final TextGridManager tgManager = new TextGridManager(project);

		for(int i = 0; i < session.getRecordCount(); i++) {
			final Record utt = session.getRecord(i);
			if(recordFilter.checkRecord(utt)) {
				try {
					TextGrid tg = tgManager.loadTextGrid(utt.getUuid().toString());
					if(tg != null && !overwrite) {
						continue;
					}

					// export text grid
					tg = createEmptyTextGrid(utt);
					setupTextGrid(utt, tg, exports);

					tgManager.saveTextGrid(tg, utt.getUuid().toString());
				} catch (PraatException pe) {
					throw new IOException(pe);
				}
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
					try {
						setupTextGrid(utt, tg, exports);
					} catch (PraatException pe) {
						throw new IOException(pe);
					}
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
	public void setupTextGrid(Project project, Record utt, TextGrid textgrid)
		throws PraatException {
		final List<TextGridExportEntry> exports = getExports(project);
		setupTextGrid(utt, textgrid, exports);
	}

	public void setupTextGrid(Record utt, TextGrid textgrid, List<TextGridExportEntry> exports)
		throws PraatException {
		setupTextGridTiers(textgrid, exports);
		for(TextGridExportEntry entry:exports) {
			addTierToTextGrid(utt, textgrid, entry);
		}
	}

	private boolean checkForTier(TextGrid textGrid, String tierName) {
		boolean tierExists = false;

		for(int i = 1; i <= textGrid.numberOfTiers(); i++) {
			final Function tier = textGrid.tier(i);
			if(tier.getName().equals(tierName)) {
				tierExists = true;
				break;
			}
		}

		return tierExists;
	}

	public void setupTextGridTiers(TextGrid textGrid, List<TextGridExportEntry> exports)
		throws PraatException {
		// setup tiers
		for(TextGridExportEntry exportEntry:exports) {

			// fix name if necessary
			String tierName = exportEntry.getTextGridTier();
			int idx = 0;
			while(checkForTier(textGrid, tierName)) {
				tierName = exportEntry.getTextGridTier() + "(" + (++idx) + ")";
			}
			exportEntry.setTextGridTier(tierName);

			final IntervalTier intervalTier = IntervalTier
					.create(textGrid.getXmin(), textGrid.getXmax());
			// remove default interval
			intervalTier.removeInterval(1);
			intervalTier.setName(tierName);
			intervalTier.setForgetOnFinalize(false);
			textGrid.addTier(intervalTier);
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
//		final TextGridExportEntry ortho1 = new TextGridExportEntry(SystemTierType.Orthography.getName(), Segmentation.TIER,
//				SystemTierType.Orthography.getName() + ": " + Segmentation.TIER.toString());
		final TextGridExportEntry ortho2 = new TextGridExportEntry(SystemTierType.Orthography.getName(), Segmentation.GROUP,
				SystemTierType.Orthography.getName() + ": " + Segmentation.GROUP.toString());
		final TextGridExportEntry ortho3 = new TextGridExportEntry(SystemTierType.Orthography.getName(), Segmentation.WORD,
				SystemTierType.Orthography.getName() + ": " + Segmentation.WORD.toString());
//		retVal.add(ortho1);
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
