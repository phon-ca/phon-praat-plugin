package ca.phon.plugins.praat.opgraph;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JPanel;

import org.jdesktop.swingx.JXTitledSeparator;

import ca.gedge.opgraph.OpNodeInfo;
import ca.gedge.opgraph.app.GraphDocument;
import ca.gedge.opgraph.app.extensions.NodeSettings;
import ca.hedlund.jpraat.binding.fon.Intensity;
import ca.hedlund.jpraat.binding.fon.LongSound;
import ca.hedlund.jpraat.binding.fon.Pitch;
import ca.hedlund.jpraat.binding.fon.Sound;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.plugins.praat.IntensitySettings;
import ca.phon.plugins.praat.IntensitySettingsPanel;
import ca.phon.plugins.praat.PitchSettings;
import ca.phon.query.db.Result;
import ca.phon.query.db.ResultValue;
import ca.phon.query.report.datasource.DefaultTableDataSource;
import ca.phon.session.MediaSegment;
import ca.phon.session.SessionPath;

@OpNodeInfo(
		name="Intensity",
		description="Intensity listing for results",
		category="Praat",
		showInLibrary=true)
public class IntensityNode extends PraatNode implements NodeSettings {
	
	private final static Logger LOGGER = Logger.getLogger(IntensityNode.class.getName());

	private IntensitySettings intensitySettings = new IntensitySettings();
	
	private JPanel settingsPanel;
	private IntensitySettingsPanel intensitySettingsPanel;

	@Override
	public void addRowToTable(LongSound longSound, TextInterval textInterval, SessionPath sessionPath,
			MediaSegment segment, Result result,
			ResultValue rv, Object value, DefaultTableDataSource table) {
		final IntensitySettings intensitySettings = getIntensitySettings();
		try {
			final double xmin = segment.getStartValue()/1000.0;
			final double xmax = segment.getEndValue()/1000.0;
			
			final Sound sound = longSound.extractPart(xmin, xmax, 1);
			final Intensity intensity = 
					sound.to_Intensity(intensitySettings.getViewRangeMin(), 0.0, 
							(intensitySettings.getSubtractMean() ? 1 : 0));
			
			// columns
			int cols = getColumnNames().size();
			
			Object[] rowData = new Object[cols];
			int colIdx = 0;
			rowData[colIdx++] = sessionPath;
			rowData[colIdx++] = result.getRecordIndex()+1;
			
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
				double v =
						intensity.getValueAtX(textInterval.getXmin() + (i * timeStep), 1, 1);
				v = intensity.convertSpecialToStandardUnit(v, 1, Intensity.UNITS_DB);
				rowData[colIdx++] = v;
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
		colNames.add("Record #");
		
		if(isUseRecordInterval()) {
			// no extra tiers
		} else if (isUseTextGridInterval()) {
			colNames.add("Text");
		} else {
			colNames.add("Tier");
			colNames.add("Group");
			colNames.add(getColumn());
		}
		
		colNames.add("Start Time");
		colNames.add("End Time");
		
		for(int i = 10; i < 100; i+=10) {
			colNames.add("I" + i + "(dB)");
		}
		
		return colNames;
	}

	@Override
	public Component getComponent(GraphDocument document) {
		if(settingsPanel == null) {
			settingsPanel = (JPanel)super.getComponent(document);
			intensitySettingsPanel = new IntensitySettingsPanel();
			intensitySettingsPanel.loadSettings(intensitySettings);
			
			final GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;
			gbc.gridy = 7;
			gbc.weightx = 1.0;
			gbc.weighty = 0.0;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(5, 2, 2, 2);
			settingsPanel.add(new JXTitledSeparator("Intensity Settings"), gbc);
			
			++gbc.gridy;
			gbc.weightx = 1.0;
			gbc.weighty = 1.0;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.insets = new Insets(2, 2, 2, 2);
			settingsPanel.add(intensitySettingsPanel, gbc);
		}
		return settingsPanel;
	}
	
	public IntensitySettings getIntensitySettings() {
		return (this.intensitySettingsPanel != null ? this.intensitySettingsPanel.getSettings() : this.intensitySettings);
	}
	
	public void setIntensitySettings(IntensitySettings settings) {
		this.intensitySettings = settings;
		if(this.intensitySettingsPanel != null)
			this.intensitySettingsPanel.loadSettings(settings);
	}

	@Override
	public Properties getSettings() {
		final Properties retVal = super.getSettings();
		
		final IntensitySettings settings = getIntensitySettings();
		retVal.put(IntensitySettings.AVERAGING_PROP, 
				Integer.toString(settings.getAveraging()));
		retVal.put(IntensitySettings.SUBTRACT_MEAN_PROP,
				Boolean.toString(settings.getSubtractMean()));
		retVal.put(IntensitySettings.VIEW_RANGE_MAX_PROP,
				Double.toString(settings.getViewRangeMax()));
		retVal.put(IntensitySettings.DEFAULT_VIEW_RANGE_MIN,
				Double.toString(settings.getViewRangeMin()));
				
		return retVal;
	}

	@Override
	public void loadSettings(Properties properties) {
		super.loadSettings(properties);
		
		final IntensitySettings settings = new IntensitySettings();
		if(properties.containsKey(IntensitySettings.AVERAGING_PROP))
			settings.setAveraging(Integer.parseInt(properties.getProperty(IntensitySettings.AVERAGING_PROP)));
		if(properties.containsKey(IntensitySettings.SUBTRACT_MEAN_PROP))
			settings.setSubtractMean(Boolean.parseBoolean(properties.getProperty(IntensitySettings.SUBTRACT_MEAN_PROP)));
		if(properties.containsKey(IntensitySettings.VIEW_RANGE_MAX_PROP))
			settings.setViewRangeMax(Double.parseDouble(properties.getProperty(IntensitySettings.VIEW_RANGE_MAX_PROP)));
		if(properties.containsKey(IntensitySettings.VIEW_RANGE_MIN_PROP))
			settings.setViewRangeMin(Double.parseDouble(properties.getProperty(IntensitySettings.VIEW_RANGE_MIN_PROP)));
		setIntensitySettings(settings);
	}
	
	
	
}
