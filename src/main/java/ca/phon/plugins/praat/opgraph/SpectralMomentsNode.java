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
import ca.hedlund.jpraat.binding.fon.LongSound;
import ca.hedlund.jpraat.binding.fon.Sound;
import ca.hedlund.jpraat.binding.fon.Spectrum;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.binding.fon.kSound_windowShape;
import ca.hedlund.jpraat.binding.sys.Interpreter;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.plugins.praat.SpectrumSettings;
import ca.phon.plugins.praat.SpectrumSettingsPanel;
import ca.phon.query.db.Result;
import ca.phon.query.db.ResultValue;
import ca.phon.query.report.datasource.DefaultTableDataSource;
import ca.phon.session.MediaSegment;
import ca.phon.session.SessionPath;

@OpNodeInfo(
	name="Spectral Moments",
	category="Praat",
	description="List ...",
	showInLibrary=true
)
public class SpectralMomentsNode extends PraatNode implements NodeSettings {
	
	private final static Logger LOGGER = Logger.getLogger(SpectralMomentsNode.class.getName());
	
	private JPanel settingsPanel;
	
	private SpectrumSettingsPanel spectrumSettingsPanel;
	private SpectrumSettings spectrumSettings = new SpectrumSettings();
	
	public SpectralMomentsNode() {
		super();
		
		putExtension(NodeSettings.class, this);
	}
	
	@Override
	public void addRowToTable(LongSound longSound, TextInterval textInterval, SessionPath sessionPath,
			MediaSegment segment, Result result, ResultValue rv, Object value, DefaultTableDataSource table) {
		final SpectrumSettings settings = getSpectrumSettings();
		try {
			final double xmin = segment.getStartValue()/1000.0;
			final double xmax = segment.getEndValue()/1000.0;
			
			final Sound recordSound = longSound.extractPart(xmin, xmax, 1);
			final Sound shapedSound = recordSound.extractPart(textInterval.getXmin(), textInterval.getXmax(),
					settings.getWindowShape(), 2, true);
			
			final Spectrum spectrum = shapedSound.to_Spectrum(1);
			spectrum.passHannBand(settings.getFilterStart(), settings.getFilterEnd(), settings.getFilterSmoothing());
			
			if(settings.isUsePreemphasis()) {
				final String formula = 
						String.format("if x >= %d then self*x else self fi", (new Double(settings.getPreempFrom())).intValue());
				spectrum.formula(formula, Interpreter.create(), null);
			}
			
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
			
			rowData[colIdx++] = spectrum.getCentreOfGravity(2);
			rowData[colIdx++] = spectrum.getStandardDeviation(2);
			rowData[colIdx++] = spectrum.getKurtosis(2);
			rowData[colIdx++] = spectrum.getSkewness(2);
			
			table.addRow(rowData);
		} catch (PraatException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	public SpectrumSettings getSpectrumSettings() {
		return (spectrumSettingsPanel != null ? spectrumSettingsPanel.getSettings() : spectrumSettings);
	}
	
	public void setSpectrumSettings(SpectrumSettings spectrumSettings) {
		this.spectrumSettings = spectrumSettings;
		if(spectrumSettingsPanel != null)
			spectrumSettingsPanel.loadSettings(spectrumSettings);
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
			spectrumSettingsPanel = new SpectrumSettingsPanel(spectrumSettings);
			
			++gbc.gridy;
			gbc.weightx = 1.0;
			gbc.weighty = 1.0;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.insets = new Insets(2, 2, 2, 2);
			
			settingsPanel.add(spectrumSettingsPanel, gbc);
		}
		
		return settingsPanel;
	}

	@Override
	public Properties getSettings() {
		final Properties retVal = super.getSettings();
		
		final SpectrumSettings settings = getSpectrumSettings();
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
		
		final SpectrumSettings spectrumSettings = new SpectrumSettings();
		
		final kSound_windowShape windowShape = 
				kSound_windowShape.valueOf(properties.getProperty("windowShape", spectrumSettings.getDefaultWindowShape().toString()));
		spectrumSettings.setWindowShape(windowShape);
		
		final double filterStart = 
				Double.parseDouble(properties.getProperty("filterStart", Double.toString(spectrumSettings.getDefaultFilterStart())));
		spectrumSettings.setFilterStart(filterStart);
		
		final double filterEnd = 
				Double.parseDouble(properties.getProperty("filterEnd", Double.toString(spectrumSettings.getDefaultFilterEnd())));
		spectrumSettings.setFilterEnd(filterEnd);
		
		final double filterSmoothing = 
				Double.parseDouble(properties.getProperty("filterSmoothing", Double.toString(spectrumSettings.getFilterSmoothing())));
		spectrumSettings.setFilterSmoothing(filterSmoothing);
		
		final boolean usePreemphasis = 
				Boolean.parseBoolean(properties.getProperty("usePreemphasis", spectrumSettings.getDefaultUsePreemphasis()+""));
		spectrumSettings.setUsePreemphasis(usePreemphasis);
		
		final double preempFrom =
			Double.parseDouble(properties.getProperty("preempFrom", Double.toString(spectrumSettings.getPreempFrom())));
		spectrumSettings.setPreempFrom(preempFrom);
		
		setSpectrumSettings(spectrumSettings);
	}
	
}
