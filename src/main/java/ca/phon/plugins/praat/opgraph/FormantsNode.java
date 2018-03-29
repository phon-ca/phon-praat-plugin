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
package ca.phon.plugins.praat.opgraph;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.JPanel;

import org.jdesktop.swingx.JXTitledSeparator;

import ca.gedge.opgraph.OpNodeInfo;
import ca.gedge.opgraph.app.GraphDocument;
import ca.gedge.opgraph.app.extensions.NodeSettings;
import ca.hedlund.jpraat.binding.fon.*;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.plugins.praat.*;
import ca.phon.query.db.*;
import ca.phon.query.report.datasource.DefaultTableDataSource;
import ca.phon.session.*;

/**
 * OpGraph node which will retrieve the formants
 * for a given interval in a sound file.
 *
 */
@OpNodeInfo(
		name="Formants",
		category="Praat",
		description="List formants for a given column.",
		showInLibrary=true
)
public class FormantsNode extends PraatNode implements NodeSettings {
	
	private final static Logger LOGGER = Logger.getLogger(FormantsNode.class.getName());
	
	// settings
	private String column = "IPA Actual";
	private FormantSettings formantSettings = new FormantSettings();
	
	private JPanel settingsPanel;
	private FormantSettingsPanel formantSettingsPanel;
	
	public FormantsNode() {
		super();
		
		putExtension(NodeSettings.class, this);
	}
	
	public FormantSettings getFormantSettings() {
		return (this.formantSettingsPanel != null ? 
				this.formantSettingsPanel.getSettings() : formantSettings);
	}

	public void setFormantSettings(FormantSettings formantSettings) {
		this.formantSettings = formantSettings;
		if(this.formantSettingsPanel != null)
			this.formantSettingsPanel.loadSettings(formantSettings);
	}
	
	@Override
	public void addRowToTable(LongSound longSound, TextGrid textGrid, TextInterval textInterval,
			SessionPath sessionPath, MediaSegment segment, Result result, ResultValue rv, Object value,
			DefaultTableDataSource table) {
		final FormantSettings formantSettings = getFormantSettings();
		
		try {
			final double xmin = segment.getStartValue()/1000.0;
			final double xmax = segment.getEndValue()/1000.0;
			
			final Sound sound = longSound.extractPart(xmin, xmax, 1);
			final Formant formants = sound.to_Formant_burg(
					formantSettings.getTimeStep(),
					formantSettings.getNumFormants(),
					formantSettings.getMaxFrequency(),
					formantSettings.getWindowLength(),
					formantSettings.getPreEmphasis());
			
			// columns
			int cols = getColumnNames().size();
			
			Object[] rowData = new Object[cols];
			int colIdx = 0;
			rowData[colIdx++] = sessionPath;
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
			for(int formant = 1; formant <= formantSettings.getNumFormants(); formant++) {
				for(int i = 10; i < 100; i+=10) {
					double time = textInterval.getXmin() + (timeStep * (i/10));
					double fval = formants.getValueAtTime(formant, time, 0);
					rowData[colIdx++] = fval;
					
					if(formantSettings.isIncludeBandwidths()) {
						double band = formants.getBandwidthAtTime(formant, time, 0);
						rowData[colIdx++] = band;
					}
				}
			}
			
			table.addRow(rowData);
		} catch (PraatException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	@Override
	public List<String> getColumnNames() {
		final List<String> colNames = new ArrayList<>();
		final FormantSettings formantSettings = getFormantSettings();
		
		colNames.add("Session");
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
		
//		if(formantSettings.isIncludeNumFormants()) {
//			colNames.add("# Formants");
//		}
//		
//		if(formantSettings.isIncludeIntensity()) {
//			colNames.add("Intensity");
//		}
		
		for(int formant = 1; formant <= formantSettings.getNumFormants(); formant++) {
			for(int i = 10; i < 100; i += 10) {
				String fNum = (new StringBuffer()).append(formant).append(i).toString();
				colNames.add("F" + fNum);
				if(formantSettings.isIncludeBandwidths()) {
					colNames.add("B" + fNum);
				}
			}
		}
		
		return colNames;
	}

	@Override
	public Component getComponent(GraphDocument document) {
		if(settingsPanel == null) {
			final GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;
			gbc.gridy = 7;
			gbc.weightx = 1.0;
			gbc.weighty = 0.0;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(5, 2, 2, 2);
			
			settingsPanel = (JPanel)super.getComponent(document);
			settingsPanel.add(new JXTitledSeparator("Formant Settings"), gbc);
			formantSettingsPanel = new FormantSettingsPanel(formantSettings);
			
			++gbc.gridy;
			gbc.weightx = 1.0;
			gbc.weighty = 1.0;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.insets = new Insets(2, 2, 2, 2);
			
			settingsPanel.add(formantSettingsPanel, gbc);
		}
		return settingsPanel;
	}

	@Override
	public Properties getSettings() {
		Properties retVal = super.getSettings();
		
		retVal.put("column", getColumn());
		
		retVal.put(FormantSettings.NUM_FORMANTS_PROP, 
				Integer.toString(getFormantSettings().getNumFormants()));
		retVal.put(FormantSettings.MAX_FREQ_PROP, 
				Double.toString(getFormantSettings().getMaxFrequency()));
		retVal.put(FormantSettings.TIME_STEP_PROP, 
				Double.toString(getFormantSettings().getTimeStep()));
		retVal.put(FormantSettings.PREEPHASIS_PROP, 
				Double.toString(getFormantSettings().getPreEmphasis()));
		retVal.put(FormantSettings.WINDOW_LENGTH_PROP, 
				Double.toString(getFormantSettings().getWindowLength()));
		retVal.put(FormantSettings.DYNAMIC_RANGE_PROP, 
				Double.toString(getFormantSettings().getDynamicRange()));
		retVal.put(FormantSettings.DOT_SIZE_PROP, 
				Double.toString(getFormantSettings().getDotSize()));
		retVal.put(FormantSettings.INCLUDE_NUMFORMANTS_PROP, 
				Boolean.toString(getFormantSettings().isIncludeNumFormants()));
		retVal.put(FormantSettings.INCLUDE_INTENSITY_PROP,
				Boolean.toString(getFormantSettings().isIncludeIntensity()));
		retVal.put(FormantSettings.INCLUDE_BANDWIDTHS_PROP,
				Boolean.toString(getFormantSettings().isIncludeBandwidths()));
		
		return retVal;
	}

	@Override
	public void loadSettings(Properties properties) {
		super.loadSettings(properties);
		
		final FormantSettings settings = new FormantSettings();
		
		if(properties.containsKey(FormantSettings.NUM_FORMANTS_PROP))
			settings.setNumFormants(Integer.parseInt(properties.getProperty(FormantSettings.NUM_FORMANTS_PROP)));
		if(properties.containsKey(FormantSettings.MAX_FREQ_PROP))
			settings.setMaxFrequency(Double.parseDouble(properties.getProperty(FormantSettings.MAX_FREQ_PROP)));
		if(properties.containsKey(FormantSettings.TIME_STEP_PROP))
			settings.setTimeStep(Double.parseDouble(properties.getProperty(FormantSettings.TIME_STEP_PROP)));
		if(properties.containsKey(FormantSettings.PREEPHASIS_PROP))
			settings.setPreEmphasis(Double.parseDouble(properties.getProperty(FormantSettings.PREEPHASIS_PROP)));
		if(properties.containsKey(FormantSettings.WINDOW_LENGTH_PROP))
			settings.setWindowLength(Double.parseDouble(properties.getProperty(FormantSettings.WINDOW_LENGTH_PROP)));
		if(properties.containsKey(FormantSettings.DYNAMIC_RANGE_PROP))
			settings.setDynamicRange(Double.parseDouble(properties.getProperty(FormantSettings.DYNAMIC_RANGE_PROP)));
		if(properties.containsKey(FormantSettings.DOT_SIZE_PROP))
			settings.setDotSize(Double.parseDouble(properties.getProperty(FormantSettings.DOT_SIZE_PROP)));
		if(properties.containsKey(FormantSettings.INCLUDE_NUMFORMANTS_PROP))
			settings.setIncludeNumFormants(Boolean.parseBoolean(properties.getProperty(FormantSettings.INCLUDE_NUMFORMANTS_PROP)));
		if(properties.containsKey(FormantSettings.INCLUDE_INTENSITY_PROP))
			settings.setIncludeIntensity(Boolean.parseBoolean(properties.getProperty(FormantSettings.INCLUDE_INTENSITY_PROP)));
		if(properties.containsKey(FormantSettings.INCLUDE_BANDWIDTHS_PROP))
			settings.setIncludeBandwidths(Boolean.parseBoolean(properties.getProperty(FormantSettings.INCLUDE_BANDWIDTHS_PROP)));
		setFormantSettings(settings);
	}
	
}
