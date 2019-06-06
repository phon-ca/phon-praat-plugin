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
import ca.hedlund.jpraat.binding.sys.Interpreter;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.opgraph.OpNodeInfo;
import ca.phon.opgraph.app.GraphDocument;
import ca.phon.opgraph.app.extensions.NodeSettings;
import ca.phon.plugins.praat.*;
import ca.phon.query.db.*;
import ca.phon.query.report.datasource.DefaultTableDataSource;
import ca.phon.session.*;

@OpNodeInfo(
	name="Spectral Moments",
	category="Praat",
	description="List ...",
	showInLibrary=true
)
public class SpectralMomentsNode extends PraatNode implements NodeSettings {
	
	private final static Logger LOGGER = Logger.getLogger(SpectralMomentsNode.class.getName());
	
	private JPanel settingsPanel;
	
	private SpectralMomentsSettingsPanel spectralMomentsSettingsPanel;
	private SpectralMomentsSettings spectralMomentsSettings = new SpectralMomentsSettings();
	
	public SpectralMomentsNode() {
		super();
		
		putExtension(NodeSettings.class, this);
	}
	
	@Override
	public void addRowToTable(LongSound longSound, TextGrid textGrid, TextInterval textInterval,
			Session session, SessionPath sessionPath,
			MediaSegment segment, Result result, ResultValue rv, Object value, DefaultTableDataSource table) {
		final SpectralMomentsSettings settings = getSpectrumSettings();
		final double xmin = segment.getStartValue()/1000.0;
		final double xmax = segment.getEndValue()/1000.0;

		try (final Sound recordSound = longSound.extractPart(xmin, xmax, true)) {
			try (final Sound shapedSound = recordSound.extractPart(textInterval.getXmin(), textInterval.getXmax(),
					settings.getWindowShape(), 2, true)) {
				try (final Spectrum spectrum = shapedSound.to_Spectrum(true)) {
					spectrum.passHannBand(settings.getFilterStart(), settings.getFilterEnd(), settings.getFilterSmoothing());
					
					if(settings.isUsePreemphasis()) {
						final String formula = 
								String.format("if x >= %d then self*x else self fi", (Double.valueOf(settings.getPreempFrom()).intValue()));
						spectrum.formula(formula, Interpreter.create(), null);
					}
					
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
					
					rowData[colIdx++] = spectrum.getCentreOfGravity(2);
					rowData[colIdx++] = spectrum.getStandardDeviation(2);
					rowData[colIdx++] = spectrum.getKurtosis(2);
					rowData[colIdx++] = spectrum.getSkewness(2);
					
					table.addRow(rowData);
				}
			}
		} catch (Exception e) {
			addToWarningsTable(sessionPath, result, e.getLocalizedMessage());
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	public SpectralMomentsSettings getSpectrumSettings() {
		return (spectralMomentsSettingsPanel != null ? spectralMomentsSettingsPanel.getSettings() : spectralMomentsSettings);
	}
	
	public void setSpectrumSettings(SpectralMomentsSettings spectralMomentsSettings) {
		this.spectralMomentsSettings = spectralMomentsSettings;
		if(spectralMomentsSettingsPanel != null)
			spectralMomentsSettingsPanel.loadSettings(spectralMomentsSettings);
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
		
		colNames.add("Start Time(s)");
		colNames.add("End Time(s)");
		
		colNames.add("Center of Gravity");
		colNames.add("Standard Deviation");
		colNames.add("Kurtosis");
		colNames.add("Skewness");
		
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
			settingsPanel.add(new JXTitledSeparator("Spectrum Settings"), gbc);
			spectralMomentsSettingsPanel = new SpectralMomentsSettingsPanel(spectralMomentsSettings);
			
			++gbc.gridy;
			gbc.weightx = 1.0;
			gbc.weighty = 1.0;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.insets = new Insets(2, 2, 2, 2);
			
			settingsPanel.add(spectralMomentsSettingsPanel, gbc);
		}
		
		return settingsPanel;
	}

	@Override
	public Properties getSettings() {
		final Properties retVal = super.getSettings();
		
		final SpectralMomentsSettings settings = getSpectrumSettings();
		retVal.put("windowShape", settings.getWindowShape().toString());
		retVal.put("filterStart", Double.toString(settings.getFilterStart()));
		retVal.put("filterEnd", Double.toString(settings.getFilterEnd()));
		retVal.put("filterSmoothing", Double.toString(settings.getFilterSmoothing()));
		retVal.put("usePreemphasis", Boolean.toString(settings.isUsePreemphasis()));
		retVal.put("preempFrom", Double.toString(settings.getPreempFrom()));
		
		return retVal;
	}

	@Override
	public void loadSettings(Properties properties) {
		super.loadSettings(properties);
		
		final SpectralMomentsSettings spectralMomentsSettings = new SpectralMomentsSettings();
		
		final kSound_windowShape windowShape = 
				kSound_windowShape.valueOf(properties.getProperty("windowShape", spectralMomentsSettings.getDefaultWindowShape().toString()));
		spectralMomentsSettings.setWindowShape(windowShape);
		
		final double filterStart = 
				Double.parseDouble(properties.getProperty("filterStart", Double.toString(spectralMomentsSettings.getDefaultFilterStart())));
		spectralMomentsSettings.setFilterStart(filterStart);
		
		final double filterEnd = 
				Double.parseDouble(properties.getProperty("filterEnd", Double.toString(spectralMomentsSettings.getDefaultFilterEnd())));
		spectralMomentsSettings.setFilterEnd(filterEnd);
		
		final double filterSmoothing = 
				Double.parseDouble(properties.getProperty("filterSmoothing", Double.toString(spectralMomentsSettings.getFilterSmoothing())));
		spectralMomentsSettings.setFilterSmoothing(filterSmoothing);
		
		final boolean usePreemphasis = 
				Boolean.parseBoolean(properties.getProperty("usePreemphasis", spectralMomentsSettings.getDefaultUsePreemphasis()+""));
		spectralMomentsSettings.setUsePreemphasis(usePreemphasis);
		
		final double preempFrom =
			Double.parseDouble(properties.getProperty("preempFrom", Double.toString(spectralMomentsSettings.getPreempFrom())));
		spectralMomentsSettings.setPreempFrom(preempFrom);
		
		setSpectrumSettings(spectralMomentsSettings);
	}
	
}
