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
package ca.phon.plugins.praat.opgraph;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;

import org.jdesktop.swingx.*;

import ca.hedlund.jpraat.*;
import ca.hedlund.jpraat.binding.fon.*;
import ca.hedlund.jpraat.binding.fon.LongSound;
import ca.hedlund.jpraat.binding.sys.*;
import ca.hedlund.jpraat.exceptions.*;
import ca.phon.app.log.*;
import ca.phon.app.opgraph.nodes.table.*;
import ca.phon.extensions.*;
import ca.phon.ipa.*;
import ca.phon.media.*;
import ca.phon.opgraph.*;
import ca.phon.opgraph.app.*;
import ca.phon.opgraph.app.extensions.*;
import ca.phon.opgraph.exceptions.*;
import ca.phon.orthography.*;
import ca.phon.plugins.praat.*;
import ca.phon.project.*;
import ca.phon.query.db.*;
import ca.phon.query.report.datasource.*;
import ca.phon.session.*;
import ca.phon.ui.text.*;

public abstract class PraatNode extends TableOpNode implements NodeSettings {

	private final static Logger LOGGER = Logger.getLogger(PraatNode.class.getName());

	protected final InputField projectInput =
			new InputField("project", "project", Project.class);
	
	protected final OutputField warningsOutput = 
			new OutputField("warnings", "Table of warnings produced by the node", true, TableDataSource.class);

	/**
	 * These options determine what interval is passed
	 * to the implementing subclass in the operate method.
	 */
	private JPanel settingsPanel;
	private JRadioButton recordIntervalBox;
	private JRadioButton textGridTierBox;
	private PromptedTextField textGridTierField;
	private PromptedTextField intervalFilterField;
	private JRadioButton fromColumnBox;
	private PromptedTextField columnField;

	private boolean useRecordInterval = false;
	private boolean useTextGridInterval = false;
	private String textGridTier = "";
	private String intervalFilter = "";
	private boolean useColumnInterval = true;
	private String column = "IPA Actual";
	
	private DefaultTableDataSource warningsTable;

	public PraatNode() {
		super();

		putField(projectInput);
		putField(warningsOutput);
		putExtension(NodeSettings.class, this);
	}


	// get media file
	private File getMediaFile(Project project, Session session) {
		File retVal = null;
		final File mediaFile =
				MediaLocator.findMediaFile(project, session);
		if(mediaFile != null) {
			String mediaName = mediaFile.getName();
			if(!mediaName.endsWith(".wav")) {
				int lastDot = mediaName.lastIndexOf('.');
				if(lastDot > 0) {
					mediaName = mediaName.substring(0, lastDot) + ".wav";
				}
			}
			retVal = new File(mediaFile.getParentFile(), mediaName);
		}

		return retVal;
	}

	private <T extends IExtendable> TextInterval getTextInterval(T obj) {
		TextInterval retVal = obj.getExtension(TextInterval.class);

		if(retVal == null) {
			if(obj instanceof Orthography) {
				final Orthography ortho = (Orthography)obj;
				if(ortho.length() > 0) {
					TextInterval firstInterval = ortho.elementAt(0).getExtension(TextInterval.class);
					TextInterval lastInterval = ortho.elementAt(ortho.length()-1).getExtension(TextInterval.class);

					if(firstInterval != null && lastInterval != null) {
						try {
							retVal = TextInterval.create(firstInterval.getXmin(), lastInterval.getXmax(), "");
						} catch (PraatException e) {
							LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
						}
					}
				}
			} else if(obj instanceof IPATranscript) {
				final IPATranscript ipa = ((IPATranscript)obj).removePunctuation();

				if(ipa.length() > 0) {
					// check first and last phones
					TextInterval firstInterval = ipa.elementAt(0).getExtension(TextInterval.class);
					TextInterval lastInterval = ipa.elementAt(ipa.length()-1).getExtension(TextInterval.class);

					if(firstInterval != null && lastInterval != null) {
						try {
							retVal = TextInterval.create(firstInterval.getXmin(), lastInterval.getXmax(), "");
						} catch (PraatException e) {
							LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
						}
					}
				}
			} else if(obj instanceof TierString) {
				final TierString tierString = (TierString)obj;

				TextInterval interval = tierString.getExtension(TextInterval.class);
				if(interval == null && tierString.numberOfWords() > 0) {
					TierString firstWord = tierString.getWord(0);
					TierString lastWord = tierString.getWord(tierString.numberOfWords()-1);

					TextInterval firstInterval = firstWord.getExtension(TextInterval.class);
					TextInterval lastInterval = lastWord.getExtension(TextInterval.class);
					if(firstInterval != null && lastInterval != null) {
						try {
							retVal = TextInterval.create(firstInterval.getXmin(), lastInterval.getXmax(), "");
						} catch (PraatException e) {
							LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
						}
					}
				}
			}
		}

		return retVal;
	}

	@Override
	public void operate(OpContext context) throws ProcessingException {
		final Project project = (Project)context.get(projectInput);

		final DefaultTableDataSource table = (DefaultTableDataSource)context.get(tableInput);
		final DefaultTableDataSource outputTable = new DefaultTableDataSource();

		warningsTable = new DefaultTableDataSource();
		int col = 0;
		warningsTable.setColumnTitle(col++, "Session");
		warningsTable.setColumnTitle(col++, "Record #");
		warningsTable.setColumnTitle(col++, "Group #");
		warningsTable.setColumnTitle(col++, "Result");
		warningsTable.setColumnTitle(col++, "Warning");
		
//		final String globalTgName = (String)context.get(TextGridNameGlobalOption.TEXTGRIDNAME_KEY);

		final TextGridManager tgManager = new TextGridManager(project);
		final TextGridAnnotator annotator = new TextGridAnnotator();

		final int resultCol = table.getColumnIndex("Result");
		final int sessionNameCol = table.getColumnIndex("Session");

		SessionPath lastSessionName = new SessionPath();
		Session session = null;
		TextGrid textGrid = null;
		LongSound longSound = null;

		List<Integer> recordList = new ArrayList<>();

		for(int row = 0; row < table.getRowCount(); row++) {
			if(super.isCanceled()) throw new BreakpointEncountered(null, this);

			final SessionPath sessionName = (SessionPath)table.getValueAt(row, sessionNameCol);
			if(session == null || !lastSessionName.equals(sessionName)) {
				// clean up old data
				if(textGrid != null) {
					try {
						textGrid.close();
					} catch (Exception e) {
						LogUtil.severe(e);
					}
				}
				if(longSound != null) {
					try {
						longSound.close();
					} catch (Exception e) {
						LogUtil.severe(e);
					}
				}
				try {
					session = project.openSession(sessionName.getCorpus(), sessionName.getSession());
					final Optional<File> textGridFile = tgManager.defaultTextGridFile(session);
					if(!textGridFile.isPresent())
						throw new PraatException("TextGrid not found for " + sessionName);
					
					textGrid = TextGridManager.loadTextGrid(textGridFile.get());
					File mediaFile = getMediaFile(project, session);
					longSound = LongSound.open(MelderFile.fromPath(mediaFile.getAbsolutePath()));
				} catch (IOException | PraatException e) {
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
					throw new ProcessingException(null, e);
				}
			}
			lastSessionName = sessionName;

			final Result result = (Result)table.getValueAt(row, resultCol);
			if(textGrid == null) {
				addToWarningsTable(sessionName, result, "TextGrid not found");
				continue;
			}
			if(longSound == null) {
				addToWarningsTable(sessionName, result, "LongSound not found");
				continue;
			}
			final Record record = session.getRecord(result.getRecordIndex());
			final Tier<MediaSegment> segTier = record.getSegment();
			if(segTier.numberOfGroups() == 0) continue;
			final MediaSegment segment = segTier.getGroup(0);
			double startTime = segment.getStartValue() / 1000.0;
			double endTime = segment.getEndValue() / 1000.0;

			TextInterval textInterval = null;
			if(isUseRecordInterval()) {
				if(recordList.contains(result.getRecordIndex())) continue;
				try {
					textInterval = TextInterval.create(startTime, endTime, ReportHelper.createResultString(result));
					addRowToTable(longSound, textGrid, textInterval, session, sessionName, segment, result, null, null, outputTable);
					recordList.add(result.getRecordIndex());
				} catch (PraatException pe) {
					LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
				}
			} else if(isUseTextGridInterval()) {
				try(final TextGrid recordTextGrid = textGrid.extractPart(startTime, endTime, true)) {
					annotator.annotateRecord(recordTextGrid, record);
					
					if(recordList.contains(result.getRecordIndex())) continue;
					String textGridTier = getTextGridTier();
					final long tierNum = TextGridUtils.tierNumberFromName(recordTextGrid, textGridTier);
					if(tierNum <= 0) continue;
					
					try {
						final IntervalTier intervalTier = recordTextGrid.checkSpecifiedTierIsIntervalTier(tierNum);
						recordList.add(result.getRecordIndex());
						
						for(long i = 1; i <= intervalTier.numberOfIntervals(); i++) {
							final TextInterval interval = intervalTier.interval(i);
							
							// check interval filter
							if(checkFilter(interval)) {
								addRowToTable(longSound, textGrid, interval, session, sessionName, segment, result, null, null, outputTable);
							}
						}
					} catch (PraatException pe) {
						LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
					}
				} catch (Exception e) {
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
					throw new ProcessingException(null, e);
				}
			} else {
				try(final TextGrid recordTextGrid = textGrid.extractPart(startTime, endTime, true)) {
					annotator.annotateRecord(recordTextGrid, record);
					// find correct result value in result
					ResultValue rv = null;
					for(int i = 0; i < result.getNumberOfResultValues(); i++) {
						ResultValue v = result.getResultValue(i);
						if(v.getTierName().equalsIgnoreCase(getColumn())) {
							rv = v;
							break;
						}
					}
					if(rv == null) {
						addToWarningsTable(sessionName, result, "Result value for " + getColumn() + " tier not found");
						continue;
					}
					
					Object tierVal = null;
					SystemTierType systemTier = SystemTierType.tierFromString(rv.getTierName());
					if(systemTier != null) {
						switch(systemTier)
						{
						case Orthography:
							tierVal = record.getOrthography().getGroup(rv.getGroupIndex());
							break;
						case IPATarget:
							tierVal = record.getIPATarget().getGroup(rv.getGroupIndex());
							break;
						case IPAActual:
							tierVal = record.getIPAActual().getGroup(rv.getGroupIndex());
							break;
						case Notes:
							tierVal = record.getNotes().getGroup(0);
							break;
							
						default:
							break;
						}
					} else {
						final Tier<?> tier = record.getTier(rv.getTierName());
						tierVal = tier.getGroup(rv.getGroupIndex());
					}
					if(tierVal == null) {
						addToWarningsTable(sessionName, result, "Tier value for " + rv.getTierName() + " not found");
						continue;
					}
					
					Object resultValue = null;
					if(tierVal instanceof Orthography) {
						Orthography ortho = (Orthography)tierVal;
						
						if(rv.getRange().getFirst() == 0
								&& rv.getRange().getRange() == ortho.toString().length()) {
							resultValue = ortho;
						} else {
							// try to copy whole elements if possible to retain annotations
							int startEleIdx = -1;
							int endEleIdx = -1;
							// rv.getRange().getFirst() must start at the beginning of an element
							int startIdx = 0;
							for(int i = 0; i < ortho.length(); i++) {
								final OrthoElement ele = ortho.elementAt(i);
								if(startIdx == rv.getRange().getFirst()) {
									startEleIdx = i;
									break;
								} else if (rv.getRange().getFirst() > startIdx + ele.text().length()) {
									// continue
									startIdx += ele.text().length()+1;
								} else {
									// inside element, break
									break;
								}
							}
							if(startEleIdx >= 0) {
								// rv.getRange.getLast() must be at the end of an element
								for(int i = startEleIdx; i < ortho.length(); i++) {
									final OrthoElement ele = ortho.elementAt(i);
									if(rv.getRange().getLast() == startIdx + ele.text().length()) {
										endEleIdx = i;
										break;
									} else if(rv.getRange().getLast() > startIdx + ele.text().length()) {
										startIdx += ele.text().length()+1;
									} else {
										break;
									}
								}
							}
							if(startEleIdx >= 0 && endEleIdx >= 0) {
								resultValue = ortho.subsection(startEleIdx, endEleIdx+1);
							} else {
								final String tierTxt = ortho.toString();
								
								final String resultTxt =
										(rv.getRange().getFirst() >= 0 && rv.getRange().getLast() >= rv.getRange().getFirst() ?
												tierTxt.substring(
														Math.max(0, rv.getRange().getFirst()),
														Math.max(0, Math.min(rv.getRange().getLast(), tierTxt.length()))) : "");
								try {
									resultValue = Orthography.parseOrthography(resultTxt);
								} catch (ParseException e) {
									// ignore
								}
							}
						}
					} else if(tierVal instanceof IPATranscript) {
						IPATranscript ipa = (IPATranscript)tierVal;
						
						if(rv.getRange().getFirst() == 0 && rv.getRange().getRange() == ipa.toString().length()) {
							resultValue = ipa;
						} else {
							if(rv.getRange().getRange() > 0) {
								int startPhone = ipa.ipaIndexOf(rv.getRange().getFirst());
								int endPhone = ipa.ipaIndexOf(rv.getRange().getLast());
								resultValue = ipa.subsection(startPhone, endPhone + (rv.getRange().isExcludesEnd() ? 1 : 0) );
							}
						}
					} else if (tierVal instanceof TierString) {
						TierString tierString = (TierString)tierVal;
						
						if(rv.getRange().getFirst() == 0 && rv.getRange().getRange() == tierString.length()) {
							resultValue = tierString;
						} else {
							int startWordIdx = -1;
							int endWordIdx = -1;
							for(int i = 0; i < tierString.numberOfWords(); i++) {
								TierString word = tierString.getWord(i);
								if(rv.getRange().getFirst() == tierString.getWordOffset(i)) {
									startWordIdx = i;
									break;
								} else if(rv.getRange().getFirst() > tierString.getWordOffset(i) + word.length()) {
									continue;
								} else {
									break;
								}
							}
							if(startWordIdx >= 0) {
								for(int i = startWordIdx; i < tierString.numberOfWords(); i++) {
									TierString word = tierString.getWord(i);
									if(rv.getRange().getLast() == tierString.getWordOffset(i) + word.length()) {
										endWordIdx = i;
										break;
									} else if(rv.getRange().getLast() > tierString.getWordOffset(i) + word.length()) {
										continue;
									} else {
										break;
									}
								}
							}
							TierString resultTierString = new TierString(tierString.substring(rv.getRange().getFirst(), rv.getRange().getLast()));
							if(startWordIdx >= 0 && endWordIdx >= 0) {
								// copy TextInverval extensions
								int wIdx = 0;
								for(int i = startWordIdx; i <= endWordIdx && wIdx < resultTierString.numberOfWords(); i++) {
									TierString word = tierString.getWord(i);
									TierString resultWord = resultTierString.getWord(wIdx++);
									resultWord.putExtension(TextInterval.class, word.getExtension(TextInterval.class));
								}
							}
							resultValue = resultTierString;
						}
					} else {
						String txt = tierVal.toString();
						resultValue = txt.substring(rv.getRange().getFirst(), rv.getRange().getLast());
					}
					if(resultValue == null || !(resultValue instanceof IExtendable)) {
						addToWarningsTable(sessionName, result, "Unable to locate subsection for result");
						continue;
					}
					
					IExtendable extendable = (IExtendable)resultValue;
					textInterval = getTextInterval(extendable);
					if(textInterval == null) {
						addToWarningsTable(sessionName, result, "TextInterval not found");
						continue;
					}
					try {
						textInterval.setText(resultValue.toString());
					} catch (PraatException e) {
						LogUtil.warning(e);
					}
					addRowToTable(longSound, textGrid, textInterval, session, sessionName, segment, result, rv, resultValue, outputTable);
					
					// delete textInterval if a new instance was created in getTextInterval(IExtendable)
					if(textInterval != extendable.getExtension(TextInterval.class)) {
						try {
							textInterval.close();
						} catch (Exception e) {
							LogUtil.severe(e);
						}
					}
				} catch (Exception e) {
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
					throw new ProcessingException(null, e);
				}
			}
		}
		
		// cleanup
		if(textGrid != null) {
			try {
				textGrid.close();
			} catch (Exception e) {
				LogUtil.severe(e);
			}
		}
		if(longSound != null) {
			try {
				longSound.close();
			} catch (Exception e) {
				LogUtil.severe(e);
			}
		}

		List<String> colNames = getColumnNames();
		for(int i = 0; i < colNames.size(); i++) {
			outputTable.setColumnTitle(i, colNames.get(i));
		}

		context.put(tableOutput, outputTable);
		context.put(warningsOutput, warningsTable);
	}
	
	protected void addToWarningsTable(SessionPath sp, Result r, String warning) {
		var rowData = new Object[warningsTable.getColumnCount()];
		var col = 0;
		
		rowData[col++] = sp;
		rowData[col++] = r.getRecordIndex()+1;
		rowData[col++] = r.getResultValue(0).getGroupIndex() + 1;
		rowData[col++] = r;
		rowData[col++] = warning;
		warningsTable.addRow(rowData);
	}

	private boolean checkFilter(TextInterval interval) {
		if(getIntervalFilter().trim().length() > 0) {
			return interval.getText().matches(getIntervalFilter());
		} else {
			// no filter
			return true;
		}
	}

	/**
	 * Get tier/column name for analysis
	 * @return
	 */
	public String getColumn() {
		return (this.columnField != null ? this.columnField.getText() : this.column);
	}

	public void setColumn(String column) {
		this.column = column;
		if(this.columnField != null)
			this.columnField.setText(column);
	}

	public boolean isUseRecordInterval() {
		return (this.recordIntervalBox != null ? this.recordIntervalBox.isSelected() : useRecordInterval);
	}

	public void setUseRecordInterval(boolean useRecordInterval) {
		this.useRecordInterval = useRecordInterval;
		if(this.recordIntervalBox != null)
			this.recordIntervalBox.setSelected(useRecordInterval);
	}

	public boolean isUseTextGridInterval() {
		return (this.textGridTierBox != null ? this.textGridTierBox.isSelected() : useTextGridInterval);
	}

	public void setUseTextGridInterval(boolean useTextGridInterval) {
		this.useTextGridInterval = useTextGridInterval;
		if(this.textGridTierBox != null)
			this.textGridTierBox.setSelected(useTextGridInterval);
	}

	public String getIntervalFilter() {
		return (this.intervalFilterField != null ? this.intervalFilterField.getText() : intervalFilter);
	}

	public void setIntervalFilter(String intervalFilter) {
		this.intervalFilter = intervalFilter;
		if(this.intervalFilterField != null)
			this.intervalFilterField.setText(intervalFilter);
	}

	public boolean isUseColumnInterval() {
		return (fromColumnBox != null ? fromColumnBox.isSelected() : useColumnInterval);
	}

	public void setUseColumnInterval(boolean useColumnInterval) {
		this.useColumnInterval = useColumnInterval;
		if(fromColumnBox != null)
			fromColumnBox.setSelected(useColumnInterval);
	}

	public String getTextGridTier() {
		return (this.textGridTierField != null ? this.textGridTierField.getText() : textGridTier);
	}

	public void setTextGridTier(String textGridTier) {
		this.textGridTier = textGridTier;
		if(this.textGridTierField != null)
			this.textGridTierField.setText(textGridTier);
	}

	/**
	 * Add data to output table
	 *
	 * @param longSound
	 * @param textInerval
	 * @param sessionPath
	 * @param result
	 * @param rv
	 * @param value
	 * @param table
	 */
	public abstract void addRowToTable(LongSound longSound, TextGrid textGrid, TextInterval textInerval,
			Session session, SessionPath sessionPath, MediaSegment segment, Result result, ResultValue rv, Object value,
			DefaultTableDataSource table);

	public abstract List<String> getColumnNames();

	private JPanel createSettingsPanel() {
		final JPanel retVal = new JPanel();

		final GridBagLayout layout = new GridBagLayout();
		retVal.setLayout(layout);
		final GridBagConstraints gbc = new GridBagConstraints();

		final ButtonGroup btnGroup = new ButtonGroup();
		recordIntervalBox = new JRadioButton("Use full record segment");
		btnGroup.add(recordIntervalBox);

		textGridTierBox = new JRadioButton("Use intervals from TextGrid tier");
		btnGroup.add(textGridTierBox);

		fromColumnBox = new JRadioButton("Use interval for tier/column value");
		btnGroup.add(fromColumnBox);

		final ActionListener btnListener = (e) -> {
			this.textGridTierField.setEnabled(this.textGridTierBox.isSelected());
			this.intervalFilterField.setEnabled(this.textGridTierBox.isSelected());
			this.columnField.setEnabled(this.fromColumnBox.isSelected());
		};
		recordIntervalBox.addActionListener(btnListener);
		textGridTierBox.addActionListener(btnListener);
		fromColumnBox.addActionListener(btnListener);

		textGridTierField = new PromptedTextField("Enter TextGrid tier name");
		textGridTierField.setText(textGridTier);

		intervalFilterField = new PromptedTextField("Enter interval filter (leave empty to select all)");
		intervalFilterField.setText(intervalFilter);

		columnField = new PromptedTextField("Enter tier/column name");
		columnField.setText(column);

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.weighty = 0.0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(2, 2, 2, 2);

		retVal.add(new JXTitledSeparator("Interval for Analysis"), gbc);

		++gbc.gridy;
		retVal.add(recordIntervalBox, gbc);

		++gbc.gridy;
		retVal.add(textGridTierBox, gbc);
		gbc.insets = new Insets(2, 20, 2, 2);
		++gbc.gridy;
		retVal.add(textGridTierField, gbc);
		++gbc.gridy;
		retVal.add(intervalFilterField, gbc);
		++gbc.gridy;
		gbc.insets = new Insets(2, 2, 2, 2);
		retVal.add(fromColumnBox, gbc);
		++gbc.gridy;
		gbc.insets = new Insets(2, 20, 2, 2);
		retVal.add(columnField, gbc);

		recordIntervalBox.setSelected(useRecordInterval);
		textGridTierBox.setSelected(useTextGridInterval);
		fromColumnBox.setSelected(useColumnInterval);

		return retVal;
	}

	@Override
	public Component getComponent(GraphDocument document) {
		if(settingsPanel == null) {
			settingsPanel = createSettingsPanel();
		}
		return settingsPanel;
	}

	@Override
	public Properties getSettings() {
		Properties retVal = new Properties();
		retVal.put("useRecordInterval", Boolean.toString(isUseRecordInterval()));
		retVal.put("useTextGridInterval", Boolean.toString(isUseTextGridInterval()));
		retVal.put("textGridTier", getTextGridTier());
		retVal.put("intervalFilter", getIntervalFilter());
		retVal.put("useColumnInterval", Boolean.toString(isUseColumnInterval()));
		retVal.put("column", getColumn());
		return retVal;
	}

	@Override
	public void loadSettings(Properties properties) {
		setUseRecordInterval(Boolean.parseBoolean(properties.getProperty("useRecordInterval", "false")));
		setUseTextGridInterval(Boolean.parseBoolean(properties.getProperty("useTextGridInterval", "false")));
		setTextGridTier(properties.getProperty("textGridTier", ""));
		setIntervalFilter(properties.getProperty("intervalFilter", ""));
		setUseColumnInterval(Boolean.parseBoolean(properties.getProperty("useColumnInterval", "true")));
		setColumn(properties.getProperty("column", "IPA Actual"));
	}

}
