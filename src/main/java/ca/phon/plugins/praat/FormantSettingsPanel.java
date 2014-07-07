package ca.phon.plugins.praat;

import java.awt.BorderLayout;
import java.text.NumberFormat;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jdesktop.swingx.VerticalLayout;

import ca.phon.ui.action.PhonUIAction;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

public class FormantSettingsPanel extends JPanel {

	private static final long serialVersionUID = 1527299258021483921L;
	
	private JFormattedTextField numFormantsField;
	
	private JFormattedTextField maxFreqField;
	
	private JFormattedTextField timeStepField;
	
	private JFormattedTextField preEmphasisField;
	
	private JFormattedTextField windowLengthField;
	
	private JFormattedTextField dynamicRangeField;
	
	private JFormattedTextField dotSizeField;
	
	private JCheckBox includeNumFormantsBox;
	
	private JCheckBox includeIntensityBox;
	
	private JCheckBox includeBandwidthsBox;
	
	private JButton saveDefaultsButton;
	
	private JButton loadDefaultsButton;
	
	private JButton loadStandardsButton;
	
	public FormantSettingsPanel() {
		this(new FormantSettings());
	}
	
	public FormantSettingsPanel(FormantSettings settings) {
		super();
		init();
		loadSettings(settings);
	}
	
	private void init() {
		setLayout(new BorderLayout());
	
		final FormLayout formLayout = new FormLayout(
				"right:pref, 3dlu, fill:pref:grow, pref", "");
		final CellConstraints cc = new CellConstraints();
		final PanelBuilder builder = new PanelBuilder(formLayout);
		
		final NumberFormat numberFormat = NumberFormat.getNumberInstance();
		int rowIdx = 1;
		
		numFormantsField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Num formants"), cc.xy(1, rowIdx));
		builder.add(numFormantsField, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		windowLengthField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Window length"), cc.xy(1, rowIdx));
		builder.add(windowLengthField, cc.xy(3, rowIdx));
		builder.add(new JLabel("(s)"), cc.xy(4, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		maxFreqField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Max frequency"), cc.xy(1, rowIdx));
		builder.add(maxFreqField, cc.xy(3, rowIdx));
		builder.add(new JLabel("(Hz)"), cc.xy(4, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		timeStepField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Time step"), cc.xy(1, rowIdx));
		builder.add(timeStepField, cc.xy(3, rowIdx));
		builder.add(new JLabel("(s)"), cc.xy(4, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		preEmphasisField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Preemphasis"), cc.xy(1, rowIdx));
		builder.add(preEmphasisField, cc.xy(3, rowIdx));
		builder.add(new JLabel("(Hz)"), cc.xy(4, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		dynamicRangeField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Dynamic range"), cc.xy(1, rowIdx));
		builder.add(dynamicRangeField, cc.xy(3, rowIdx));
		builder.add(new JLabel("(dB)"), cc.xy(4, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		dotSizeField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Dot size"), cc.xy(1, rowIdx));
		builder.add(dotSizeField, cc.xy(3, rowIdx));
		builder.add(new JLabel("(mm)"), cc.xy(4, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		includeNumFormantsBox = new JCheckBox("Include num formants");
		builder.appendRow("pref");
		builder.add(includeNumFormantsBox, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		includeIntensityBox = new JCheckBox("Include intensity");
		builder.appendRow("pref");
		builder.add(includeIntensityBox, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		includeBandwidthsBox = new JCheckBox("Include bandwidths");
		builder.appendRow("pref");
		builder.add(includeBandwidthsBox, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		add(builder.getPanel(), BorderLayout.CENTER);
		
		final JPanel btnPanel = new JPanel(new VerticalLayout());
		
		final PhonUIAction loadDefaultsAct = new PhonUIAction(this, "loadDefaults");
		loadDefaultsAct.putValue(PhonUIAction.NAME, "Load defaults");
		loadDefaultsButton = new JButton(loadDefaultsAct);
		btnPanel.add(loadDefaultsButton);
		
		final PhonUIAction loadStandardsAct = new PhonUIAction(this, "loadStandards");
		loadStandardsAct.putValue(PhonUIAction.NAME, "Load standards");
		loadStandardsButton = new JButton(loadStandardsAct);
		btnPanel.add(loadStandardsButton);
		
		final PhonUIAction saveDefaultsAct = new PhonUIAction(this, "saveSettingsAsDefaults");
		saveDefaultsAct.putValue(PhonUIAction.NAME, "Save as defaults");
		saveDefaultsButton = new JButton(saveDefaultsAct);
		btnPanel.add(saveDefaultsButton);
		
		add(btnPanel, BorderLayout.EAST);
	}
	
	public void loadSettings(FormantSettings settings) {
		numFormantsField.setValue(settings.getNumFormants());
		maxFreqField.setValue(settings.getMaxFrequency());
		timeStepField.setValue(settings.getTimeStep());
		preEmphasisField.setValue(settings.getPreEmphasis());
		windowLengthField.setValue(settings.getWindowLength());
		dynamicRangeField.setValue(settings.getDynamicRange());
		dotSizeField.setValue(settings.getDotSize());
		includeNumFormantsBox.setSelected(settings.isIncludeNumFormants());
		includeIntensityBox.setSelected(settings.isIncludeIntensity());
		includeBandwidthsBox.setSelected(settings.isIncludeBandwidths());
	}
	
	public FormantSettings getSettings() {
		final FormantSettings retVal = new FormantSettings();
	
		final int numFormants = ((Number)numFormantsField.getValue()).intValue();
		retVal.setNumFormants(numFormants);
		
		final double maxFreq = ((Number)maxFreqField.getValue()).doubleValue();
		retVal.setMaxFrequency(maxFreq);
		
		final double timeStep = ((Number)timeStepField.getValue()).doubleValue();
		retVal.setTimeStep(timeStep);
		
		final double preEmphasis = ((Number)preEmphasisField.getValue()).doubleValue();
		retVal.setPreEmphasis(preEmphasis);
		
		final double windowLength = ((Number)windowLengthField.getValue()).doubleValue();
		retVal.setWindowLength(windowLength);
		
		final double dynamicRange = ((Number)dynamicRangeField.getValue()).doubleValue();
		retVal.setDynamicRange(dynamicRange);
		
		final double dotSize = ((Number)dotSizeField.getValue()).doubleValue();
		retVal.setDotSize(dotSize);
		
		final boolean includeNumFormants = includeNumFormantsBox.isSelected();
		retVal.setIncludeNumFormants(includeNumFormants);
		
		final boolean includeIntensity = includeIntensityBox.isSelected();
		retVal.setIncludeIntensity(includeIntensity);
		
		final boolean includeBandwidths = includeBandwidthsBox.isSelected();
		retVal.setIncludeBandwidths(includeBandwidths);
		
		return retVal;
	}

	public void saveSettingsAsDefaults() {
		getSettings().saveAsDefaults();
	}
	
	public void loadStandards() {
		final FormantSettings settings = new FormantSettings();
		settings.loadStandards();
		loadSettings(settings);
	}
	
	public void loadDefaults() {
		loadSettings(new FormantSettings());
	}
	
}