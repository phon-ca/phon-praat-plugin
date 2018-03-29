package ca.phon.plugins.praat.opgraph;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;

import javax.swing.*;

import org.jdesktop.swingx.JXTitledSeparator;

import ca.gedge.opgraph.OpNodeInfo;
import ca.gedge.opgraph.app.GraphDocument;
import ca.gedge.opgraph.app.extensions.NodeSettings;
import ca.hedlund.jpraat.binding.fon.*;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.app.log.LogUtil;
import ca.phon.query.db.*;
import ca.phon.query.report.datasource.DefaultTableDataSource;
import ca.phon.session.*;
import ca.phon.ui.text.PromptedTextField;

/**
 * Calculate voice onset time (VoT) for each sampled interval.  VoT is calculated
 * using a secondary tier.  The secondary tier may be a point tier or an interval tier
 * (though point is assumed.)
 * 
 */
@OpNodeInfo(name="Voice Onset Time (VOT)", category="Praat", description="Calculate voice onset time", showInLibrary=true)
public class VOTNode extends PraatNode implements NodeSettings {
	
	private final static String DEFAULT_VOT_TIER = "Release";
	private String votTier = DEFAULT_VOT_TIER;
	
	private final static float DEFAULT_THRESHOLD = 0.5f;
	private float threshold = DEFAULT_THRESHOLD;
	
	private JPanel settingsPanel;
	private PromptedTextField votTierNameField;
	
	private final Collection<TextPoint> processedPoints = new ArrayList<>();

	public VOTNode() {
		super();

		putExtension(NodeSettings.class, this);
	}
	
	@Override
	public void addRowToTable(LongSound longSound, TextGrid textGrid, TextInterval textInterval, SessionPath sessionPath,
			MediaSegment segment, Result result, ResultValue rv, Object value, DefaultTableDataSource table) {
		// check for vot tier
		final Optional<TextTier> votTier = findVoTTier(textGrid);
		if(!votTier.isPresent()) return;
		
		// find vot point for given interval
		final Optional<TextPoint> votPoint = findVoTPoint(votTier.get(), textInterval);
		if(!votPoint.isPresent()) return;
		
		final double vot = votPoint.get().getNumber() - textInterval.getXmax();
		
		// add row
		int col = 0;
		final Object rowData[] = new Object[getColumnNames().size()];
		rowData[col++] = sessionPath;
		rowData[col++] = result.getRecordIndex()+1;
		rowData[col++] = result;

		if(isUseRecordInterval()) {
			// add nothing
		} else if(isUseTextGridInterval()) {
			rowData[col++] = textInterval.getText();
		} else {
			rowData[col++] = rv.getTierName();
			rowData[col++] = rv.getGroupIndex()+1;
			rowData[col++] = value;
		}

		rowData[col++] = textInterval.getXmin();
		rowData[col++] = textInterval.getXmax();
		rowData[col++] = votPoint.get().getNumber();
		rowData[col++] = (textInterval.getXmax() - textInterval.getXmin()) + vot;
		rowData[col++] = vot;
		
		table.addRow(rowData);
	}

	@Override
	public List<String> getColumnNames() {
		final List<String> colNames = new ArrayList<>();
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
		
		colNames.add("Start Time(s)");
		colNames.add("End Time(s)");
		colNames.add("Release(s)");
		colNames.add("Dur + VOT(s)");
		colNames.add("VOT(s)");
		
		return colNames;
	}
	
	private Optional<TextPoint> findVoTPoint(TextTier tier, TextInterval interval) {
		for(long i = 1; i <= tier.numberOfPoints(); i++) {
			TextPoint tp = tier.point(i);
			if(tp.getNumber() < interval.getXmin() || processedPoints.contains(tp)) 
				continue;
			else if(tp.getNumber() > interval.getXmax() + 0.5)
				break;
			
			if(tp.getText().equals(interval.getText())) {
				processedPoints.add(tp);
				return Optional.of(tp);
			}
		}
		return Optional.empty();
	}
	
	private Optional<TextTier> findVoTTier(TextGrid textGrid) {
		final String tierName = getVoTTier();
		
		for(long i = 1; i <= textGrid.numberOfTiers(); i++) {
			Function tier = textGrid.tier(i);
			if(tier.getName().equals(tierName)) {
				try {
					return Optional.of(textGrid.checkSpecifiedTierIsPointTier(i));
				} catch (PraatException e1) {
					LogUtil.log(Level.WARNING, e1.getLocalizedMessage(), e1);
					return Optional.empty();
				}
			}
		}
		
		return Optional.empty();
	}
	
	public float getThreshold() {
		return this.threshold;
	}
	
	public void setThreshold(float threshold) {
		this.threshold = threshold;
	}
	
	public String getVoTTier() {
		return (this.votTierNameField != null ? votTierNameField.getText() : votTier);
	}
	
	public void setVoTTier(String votTier) {
		this.votTier = votTier;
		if(this.votTierNameField != null) {
			this.votTierNameField.setText(votTier);
		}
	}
	
	@Override
	public Properties getSettings() {
		final Properties retVal = super.getSettings();
		
		retVal.setProperty("votTier", getVoTTier());
		retVal.setProperty("threshold", Float.toString(getThreshold()));
		
		return retVal;
	}
	
	@Override
	public void loadSettings(Properties properties) {
		super.loadSettings(properties);
		
		setVoTTier(properties.getProperty("votTier", DEFAULT_VOT_TIER));
		setThreshold(Float.parseFloat(properties.getProperty("threshold", Float.toString(DEFAULT_THRESHOLD))));
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
			settingsPanel.add(new JXTitledSeparator("VoT Settings"), gbc);
			
			++gbc.gridy;
			
			final JPanel votPanel = new JPanel(new BorderLayout());
			
			votTierNameField = new PromptedTextField("Enter name of Point tier which contains onset release times");
			votTierNameField.setText(this.votTier);
			votPanel.add(new JLabel("VOT Release Tier:"), BorderLayout.WEST);
			votPanel.add(votTierNameField, BorderLayout.CENTER);
			
			settingsPanel.add(votPanel, gbc);
			
			++gbc.gridy;
			gbc.weighty = 1.0;
			gbc.fill = GridBagConstraints.BOTH;
			
			settingsPanel.add(Box.createVerticalGlue(), gbc);
		}
		return settingsPanel;
	}
	
}
