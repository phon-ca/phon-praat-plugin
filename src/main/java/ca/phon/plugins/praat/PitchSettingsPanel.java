package ca.phon.plugins.praat;

import java.awt.BorderLayout;
import java.text.NumberFormat;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.jdesktop.swingx.VerticalLayout;

import ca.hedlund.jpraat.binding.fon.kPitch_unit;
import ca.phon.ui.action.PhonUIAction;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

public class PitchSettingsPanel extends JPanel {

	private static final long serialVersionUID = -8954511673170535288L;

	private JFormattedTextField timeStepField;
	
	private JFormattedTextField rangeStartField;
	
	private JFormattedTextField rangeEndField;
	
	private JRadioButton autoCorrelateButton;
	
	private JRadioButton crossCorrelateButton;
	
	private JComboBox unitsBox;
	
	private JCheckBox veryAccurateBox;
	
	private JFormattedTextField maxCandidatesField;
	
	private JFormattedTextField silenceThresholdField;
	
	private JFormattedTextField voicingThresholdField;
	
	private JFormattedTextField ocatveCostField;
	
	private JFormattedTextField octaveJumpCostField;
	
	private JFormattedTextField voicedUnvoicedCostField;
	
	private JFormattedTextField dotSizeField;
	
	private JButton saveDefaultsButton;
	
	private JButton loadDefaultsButton;
	
	private JButton loadStandardsButton;
	
	public PitchSettingsPanel() {
		this(new PitchSettings());
	}
	
	public PitchSettingsPanel(PitchSettings settings) {
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
		
		timeStepField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Time step"), cc.xy(1, rowIdx));
		builder.add(timeStepField, cc.xy(3, rowIdx));
		builder.add(new JLabel("(s)"), cc.xy(4, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		rangeStartField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Range start"), cc.xy(1, rowIdx));
		builder.add(rangeStartField, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		rangeEndField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Range end"), cc.xy(1, rowIdx));
		builder.add(rangeEndField, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		unitsBox = new JComboBox(kPitch_unit.values());
		builder.appendRow("pref");
		builder.add(new JLabel("Units"), cc.xy(1, rowIdx));
		builder.add(unitsBox, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		final ButtonGroup btnGrp = new ButtonGroup();
		autoCorrelateButton = new JRadioButton("Auto-correlate");
		autoCorrelateButton.setSelected(true);
		crossCorrelateButton = new JRadioButton("Cross-correlate");
		btnGrp.add(autoCorrelateButton);
		btnGrp.add(crossCorrelateButton);
		builder.appendRow("pref");
		builder.add(new JLabel("Correlation"), cc.xy(1, rowIdx));
		builder.add(autoCorrelateButton, cc.xy(3, rowIdx));
		builder.appendRow("pref"); ++rowIdx;
		builder.add(crossCorrelateButton, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		veryAccurateBox = new JCheckBox("Very accurate");
		builder.appendRow("pref");
		builder.add(veryAccurateBox, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		maxCandidatesField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Max. candidates"), cc.xy(1, rowIdx));
		builder.add(maxCandidatesField, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		silenceThresholdField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Silence threshold"), cc.xy(1, rowIdx));
		builder.add(silenceThresholdField, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		voicingThresholdField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Voicing threshold"), cc.xy(1, rowIdx));
		builder.add(voicingThresholdField, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		ocatveCostField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Ocatave cost"), cc.xy(1, rowIdx));
		builder.add(ocatveCostField, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		octaveJumpCostField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Ocatave-jump cost"), cc.xy(1, rowIdx));
		builder.add(octaveJumpCostField, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		voicedUnvoicedCostField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Voiced/unvoiced cost"), cc.xy(1, rowIdx));
		builder.add(voicedUnvoicedCostField, cc.xy(3, rowIdx));
		builder.appendRow("3dlu");
		rowIdx += 2;
		
		dotSizeField = new JFormattedTextField(numberFormat);
		builder.appendRow("pref");
		builder.add(new JLabel("Dot size"), cc.xy(1, rowIdx));
		builder.add(dotSizeField, cc.xy(3, rowIdx));
		builder.add(new JLabel("(mm)"), cc.xy(4, rowIdx));
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
	
	public void loadSettings(PitchSettings settings) {
		timeStepField.setValue(settings.getTimeStep());
		autoCorrelateButton.setSelected(settings.isAutoCorrelate());
		crossCorrelateButton.setSelected(!settings.isAutoCorrelate());
		rangeStartField.setValue(settings.getRangeStart());
		rangeEndField.setValue(settings.getRangeEnd());
		dotSizeField.setValue(settings.getDotSize());
		unitsBox.setSelectedItem(settings.getUnits());
		veryAccurateBox.setSelected(settings.isVeryAccurate());
		maxCandidatesField.setValue(settings.getMaxCandidates());
		silenceThresholdField.setValue(settings.getSilenceThreshold());
		voicingThresholdField.setValue(settings.getVoicingThreshold());
		ocatveCostField.setValue(settings.getOctaveCost());
		octaveJumpCostField.setValue(settings.getOctaveJumpCost());
		voicedUnvoicedCostField.setValue(settings.getVoicedUnvoicedCost());
	}
	
	public PitchSettings getSettings() {
		final PitchSettings retVal = new PitchSettings();
		
		final double timeStep = ((Number)timeStepField.getValue()).doubleValue();
		retVal.setTimeStep(timeStep);
		
		retVal.setAutoCorrelate(autoCorrelateButton.isSelected());

		final double dotSize = ((Number)dotSizeField.getValue()).doubleValue();
		retVal.setDotSize(dotSize);
		
		final double rangeStart = ((Number)rangeStartField.getValue()).doubleValue();
		retVal.setRangeStart(rangeStart);
		
		final double rangeEnd = ((Number)rangeEndField.getValue()).doubleValue();
		retVal.setRangeEnd(rangeEnd);
		
		final kPitch_unit units = (kPitch_unit)unitsBox.getSelectedItem();
		retVal.setUnits(units);
		
		retVal.setVeryAccurate(veryAccurateBox.isSelected());
		
		final int maxCandidates = ((Number)maxCandidatesField.getValue()).intValue();
		retVal.setMaxCandidates(maxCandidates);
		
		final double silenceThreshold = ((Number)silenceThresholdField.getValue()).doubleValue();
		retVal.setSilenceThreshold(silenceThreshold);
		
		final double voicingThreshold = ((Number)voicingThresholdField.getValue()).doubleValue();
		retVal.setVoicingThreshold(voicingThreshold);
		
		final double octaveCost = ((Number)ocatveCostField.getValue()).doubleValue();
		retVal.setOctaveCost(octaveCost);
		
		final double octaveJumpCost = ((Number)octaveJumpCostField.getValue()).doubleValue();
		retVal.setOctaveJumpCost(octaveJumpCost);
		
		final double voicedUnvoicedCost = ((Number)voicedUnvoicedCostField.getValue()).doubleValue();
		retVal.setVoicedUnvoicedCost(voicedUnvoicedCost);
		
		return retVal;
	}
	
	public void saveSettingsAsDefaults() {
		getSettings().saveAsDefaults();
	}
	
	public void loadStandards() {
		final PitchSettings settings = new PitchSettings();
		settings.loadStandards();
		loadSettings(settings);
	}
	
	public void loadDefaults() {
		loadSettings(new PitchSettings());
	}
	
}
