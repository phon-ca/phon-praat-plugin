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
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.JPanel;

import org.jdesktop.swingx.JXTitledSeparator;

import ca.hedlund.jpraat.binding.fon.*;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.opgraph.OpNodeInfo;
import ca.phon.opgraph.app.GraphDocument;
import ca.phon.opgraph.app.extensions.NodeSettings;
import ca.phon.plugins.praat.*;
import ca.phon.query.db.*;
import ca.phon.query.report.datasource.DefaultTableDataSource;
import ca.phon.session.*;

@OpNodeInfo(
		name="Pitch",
		description="List pitch for search results",
		category="Praat",
		showInLibrary=true)
public class PitchNode extends PraatNode implements NodeSettings {
	
	private final static Logger LOGGER = Logger.getLogger(PitchNode.class.getName());
	
	private PitchSettings pitchSettings = new PitchSettings();
	
	private JPanel settingsPanel;
	private PitchSettingsPanel pitchSettingsPanel;
	
	public PitchNode() {
		super();
		
		putExtension(NodeSettings.class, this);
	}
	
	private Pitch getPitch(Sound sound) throws PraatException {
		final PitchSettings pitchSettings = getPitchSettings();
		Pitch pitch = null;
		if(pitchSettings.isAutoCorrelate()) {
		    // auto-correlate
		    pitch = sound.to_Pitch_ac(pitchSettings.getTimeStep(), pitchSettings.getRangeStart(), 3.0, 
					pitchSettings.getMaxCandidates(), (pitchSettings.isVeryAccurate() ? 1 : 0), pitchSettings.getSilenceThreshold(), 
					pitchSettings.getVoicingThreshold(), pitchSettings.getOctaveCost(), 
					pitchSettings.getOctaveJumpCost(), pitchSettings.getVoicedUnvoicedCost(), pitchSettings.getRangeEnd());
		} else {
		    // cross-correlate
		    pitch = sound.to_Pitch_cc(pitchSettings.getTimeStep(), pitchSettings.getRangeStart(), 3.0, 
					pitchSettings.getMaxCandidates(), (pitchSettings.isVeryAccurate() ? 1 : 0), pitchSettings.getSilenceThreshold(), 
					pitchSettings.getVoicingThreshold(), pitchSettings.getOctaveCost(), 
					pitchSettings.getOctaveJumpCost(), pitchSettings.getVoicedUnvoicedCost(), pitchSettings.getRangeEnd());
		}
		return pitch;
	}

	@Override
	public void addRowToTable(LongSound longSound, TextGrid textGrid, TextInterval textInterval, 
			Session session, SessionPath sessionPath,
			MediaSegment segment, Result result,
			ResultValue rv, Object value, DefaultTableDataSource table) {
		final PitchSettings pitchSettings = getPitchSettings();
		try {
			final double xmin = segment.getStartValue()/1000.0;
			final double xmax = segment.getEndValue()/1000.0;
			
			final Sound sound = longSound.extractPart(xmin, xmax, 1);
			final Pitch pitch = getPitch(sound);
			
			// columns
			int cols = getColumnNames().size();
			
			final Record r = (result.getRecordIndex() < session.getRecordCount() ? session.getRecord(result.getRecordIndex()) : null);
			final Participant speaker = (r != null ? r.getSpeaker() : Participant.UNKNOWN);
			
			Object[] rowData = new Object[cols];
			int colIdx = 0;
			rowData[colIdx++] = sessionPath;
			rowData[colIdx++] = speaker;
			rowData[colIdx++] = (speaker != Participant.UNKNOWN ? speaker.getAge(session.getDate()) : "");
			rowData[colIdx++] = result.getRecordIndex()+1;
			rowData[colIdx++] = result;
			
			if(isUseRecordInterval()) {
				// add nothing
			} else if(isUseTextGridInterval()) {
				rowData[colIdx++] = textInterval.getText();
			} else {
				rowData[colIdx++] = rv.getTierName();
				rowData[colIdx++] = rv.getGroupIndex()+1;
				rowData[colIdx++] = value;
			}
			
			rowData[colIdx++] = textInterval.getXmin();
			rowData[colIdx++] = textInterval.getXmax();
			
			double len = textInterval.getXmax() - textInterval.getXmin();
			double timeStep = len / 10.0;
			for(int i = 1; i <= 9; i++) {
				double f0 = 
						pitch.getValueAtTime(
								textInterval.getXmin() + (i * timeStep), pitchSettings.getUnits().ordinal(), true);
				f0 = pitch.convertToNonlogarithmic(f0, Pitch.LEVEL_FREQUENCY, pitchSettings.getUnits().ordinal());
				rowData[colIdx++] = f0;
			}
			
			table.addRow(rowData);
		} catch (PraatException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	@Override
	public List<String> getColumnNames() {
		final List<String> colNames = new ArrayList<>();
		
		colNames.add("Session");
		colNames.add("Speaker");
		colNames.add("Age");
		colNames.add("Record #");
		colNames.add("Result");
		
		if(isUseRecordInterval()) {
			// no extra tiers
		} else if (isUseTextGridInterval()) {
			colNames.add("Text");
		} else {
			colNames.add("Tier");
			colNames.add("Group #");
			colNames.add(getColumn());
		}
		
		colNames.add("Start Time");
		colNames.add("End Time");
		
		final PitchSettings pitchSettings = getPitchSettings();
		kPitch_unit pitchUnit = pitchSettings.getUnits();
		String unitTxt = "";
		try {
			Pitch pitch = Pitch.create(0.0, 0.0, 0, 0.0, 0.0, 0.0, 0);
			unitTxt = pitch.getUnitText(Pitch.LEVEL_FREQUENCY, pitchUnit, Function.UNIT_TEXT_SHORT);
		} catch (PraatException e) {
			
		}
		for(int i = 10; i < 100; i+=10) {
			colNames.add("P" + i + "("+unitTxt+")");
		}
		
		return colNames;
	}

	@Override
	public Component getComponent(GraphDocument document) {
		if(settingsPanel == null) {
			settingsPanel = (JPanel)super.getComponent(document);
			pitchSettingsPanel = new PitchSettingsPanel();
			pitchSettingsPanel.loadSettings(pitchSettings);

			final GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;
			gbc.gridy = 7;
			gbc.weightx = 1.0;
			gbc.weighty = 0.0;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(5, 2, 2, 2);
			settingsPanel.add(new JXTitledSeparator("Pitch Settings"), gbc);
			
			++gbc.gridy;
			gbc.weightx = 1.0;
			gbc.weighty = 1.0;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.insets = new Insets(2, 2, 2, 2);
			settingsPanel.add(pitchSettingsPanel, gbc);
		}
		return settingsPanel;
	}
	
	public PitchSettings getPitchSettings() {
		return (pitchSettingsPanel != null ? pitchSettingsPanel.getSettings() : this.pitchSettings);
	}
	
	public void setPitchSettings(PitchSettings pitchSettings) {
		this.pitchSettings = pitchSettings;
		if(this.pitchSettingsPanel != null)
			this.pitchSettingsPanel.loadSettings(pitchSettings);
	}

	@Override
	public Properties getSettings() {
		final Properties retVal = super.getSettings();
		
		final PitchSettings pitchSettings = getPitchSettings();
		retVal.put(PitchSettings.AUTOCORRELATE_PROP,
				Boolean.toString(pitchSettings.isAutoCorrelate()));
		retVal.put(PitchSettings.MAX_CANDIDATES_PROP,
				Integer.toString(pitchSettings.getMaxCandidates()));
		retVal.put(PitchSettings.OCTAVE_COST_PROP,
				Double.toString(pitchSettings.getOctaveCost()));
		retVal.put(PitchSettings.OCTAVE_JUMP_COST_PROP,
				Double.toString(pitchSettings.getOctaveJumpCost()));
		retVal.put(PitchSettings.RANGE_END_PROP,
				Double.toString(pitchSettings.getRangeEnd()));
		retVal.put(PitchSettings.RANGE_START_PROP,
				Double.toString(pitchSettings.getRangeStart()));
		retVal.put(PitchSettings.SILENCE_THRESHOLD_PROP,
				Double.toString(pitchSettings.getSilenceThreshold()));
		retVal.put(PitchSettings.VOICED_UNVOICED_COST_PROP,
				Double.toString(pitchSettings.getVoicedUnvoicedCost()));
		retVal.put(PitchSettings.VOICING_THRESHOLD_PROP,
				Double.toString(pitchSettings.getVoicingThreshold()));
		retVal.put(PitchSettings.TIME_STEP_PROP,
				Double.toString(pitchSettings.getTimeStep()));
		retVal.put(PitchSettings.UNITS_PROP,
				pitchSettings.getUnits().toString());
		retVal.put(PitchSettings.VERY_ACCURATE_PROP,
				Boolean.toString(pitchSettings.isVeryAccurate()));
		retVal.put(PitchSettings.DOT_SIZE_PROP,
				Double.toString(pitchSettings.getDotSize()));
		
		return retVal;
	}

	@Override
	public void loadSettings(Properties properties) {
		super.loadSettings(properties);
		
		final PitchSettings pitchSettings = new PitchSettings();
		if(properties.containsKey(PitchSettings.AUTOCORRELATE_PROP))
			pitchSettings.setAutoCorrelate(Boolean.parseBoolean(properties.getProperty(PitchSettings.AUTOCORRELATE_PROP)));
		if(properties.containsKey(PitchSettings.MAX_CANDIDATES_PROP))
			pitchSettings.setMaxCandidates(Integer.parseInt(properties.getProperty(PitchSettings.MAX_CANDIDATES_PROP)));
		if(properties.containsKey(PitchSettings.OCTAVE_COST_PROP))
			pitchSettings.setOctaveCost(Double.parseDouble(properties.getProperty(PitchSettings.OCTAVE_COST_PROP)));
		if(properties.containsKey(PitchSettings.OCTAVE_JUMP_COST_PROP))
			pitchSettings.setOctaveJumpCost(Double.parseDouble(properties.getProperty(PitchSettings.OCTAVE_JUMP_COST_PROP)));
		if(properties.containsKey(PitchSettings.RANGE_END_PROP))
			pitchSettings.setRangeEnd(Double.parseDouble(properties.getProperty(PitchSettings.RANGE_END_PROP)));
		if(properties.containsKey(PitchSettings.RANGE_START_PROP))
			pitchSettings.setRangeStart(Double.parseDouble(properties.getProperty(PitchSettings.RANGE_START_PROP)));
		if(properties.containsKey(PitchSettings.SILENCE_THRESHOLD_PROP))
			pitchSettings.setSilenceThreshold(Double.parseDouble(properties.getProperty(PitchSettings.SILENCE_THRESHOLD_PROP)));
		if(properties.containsKey(PitchSettings.VOICED_UNVOICED_COST_PROP))
			pitchSettings.setVoicedUnvoicedCost(Double.parseDouble(properties.getProperty(PitchSettings.VOICED_UNVOICED_COST_PROP)));
		if(properties.containsKey(PitchSettings.VOICING_THRESHOLD_PROP))
			pitchSettings.setVoicingThreshold(Double.parseDouble(properties.getProperty(PitchSettings.VOICING_THRESHOLD_PROP)));
		if(properties.containsKey(PitchSettings.TIME_STEP_PROP))
			pitchSettings.setTimeStep(Double.parseDouble(properties.getProperty(PitchSettings.TIME_STEP_PROP)));
		if(properties.containsKey(PitchSettings.UNITS_PROP))
			pitchSettings.setUnits(kPitch_unit.fromString(properties.getProperty(PitchSettings.UNITS_PROP)));
		if(properties.containsKey(PitchSettings.VERY_ACCURATE_PROP))
			pitchSettings.setVeryAccurate(Boolean.parseBoolean(properties.getProperty(PitchSettings.VERY_ACCURATE_PROP)));
		if(properties.containsKey(PitchSettings.DOT_SIZE_PROP))
			pitchSettings.setDotSize(Double.parseDouble(properties.getProperty(PitchSettings.DOT_SIZE_PROP)));
		setPitchSettings(pitchSettings);
	}
	
}
