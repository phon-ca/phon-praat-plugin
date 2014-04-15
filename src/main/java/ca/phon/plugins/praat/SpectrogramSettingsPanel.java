package ca.phon.plugins.praat;

import java.text.NumberFormat;

import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;

import ca.hedlund.jpraat.binding.fon.kSound_to_Spectrogram_windowShape;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

/**
 * Panel for modifying spectrogram settings.
 *
 */
public class SpectrogramSettingsPanel extends JPanel {

	private static final long serialVersionUID = 5101268187463658850L;

	private JFormattedTextField windowLengthField;
	
	private JFormattedTextField maxFreqField;
	
	private JFormattedTextField timeStepField;
	
	private JFormattedTextField freqStepField;
	
	private JComboBox windowShapeBox;
	
	private JFormattedTextField preEmphasisField;
	
	private JFormattedTextField dynamicRangeField;

	public SpectrogramSettingsPanel() {
		super();
		
		init();
	}
	
	private void init() {
		final FormLayout layout = new FormLayout(
				"right:pref, fill:pref:grow, pref",
				"pref, pref, pref, pref, pref, pref, pref");
		setLayout(layout);
		final CellConstraints cc = new CellConstraints();
		
		final NumberFormat numberFormat = NumberFormat.getNumberInstance();
		
		windowLengthField = new JFormattedTextField(numberFormat);
		windowLengthField.setValue(SpectrogramSettings.DEFAULT_WINDOW_LENGTH);
		add(new JLabel("Window length:"), cc.xy(1, 1));
		add(windowLengthField, cc.xy(2, 1));
		add(new JLabel("(s)"), cc.xy(3, 1));
		
		maxFreqField = new JFormattedTextField(numberFormat);
		maxFreqField.setValue(SpectrogramSettings.DEFAULT_MAX_FREQUENCY);
		add(new JLabel("Max frequency:"), cc.xy(1, 2));
		add(maxFreqField, cc.xy(2, 2));
		add(new JLabel("(Hz)"), cc.xy(3, 2));
		
		timeStepField = new JFormattedTextField(numberFormat);
		timeStepField.setValue(SpectrogramSettings.DEFAULT_TIME_STEP);
		add(new JLabel("Time step:"), cc.xy(1, 3));
		add(timeStepField, cc.xy(2, 3));
		add(new JLabel("(s)"), cc.xy(3, 3));
		
		freqStepField = new JFormattedTextField(numberFormat);
		freqStepField.setValue(SpectrogramSettings.DEFAULT_FREQUENCY_STEP);
		add(new JLabel("Frequency step:"), cc.xy(1, 4));
		add(freqStepField, cc.xy(2, 4));
		add(new JLabel("(Hz)"), cc.xy(3, 4));
		
		windowShapeBox = new JComboBox(kSound_to_Spectrogram_windowShape.values());
		windowShapeBox.setSelectedItem(SpectrogramSettings.DEFAULT_WINDOW_SHAPE);
		add(new JLabel("Window shape:"), cc.xy(1, 5));
		add(windowShapeBox, cc.xy(2, 5));
		
		preEmphasisField = new JFormattedTextField(numberFormat);
		preEmphasisField.setValue(SpectrogramSettings.DEFAULT_PREEMPHASIS);
		add(new JLabel("Pre-emphasis:"), cc.xy(1, 6));
		add(preEmphasisField, cc.xy(2, 6));
		add(new JLabel("(dB/Hz)"), cc.xy(3, 6));
		
		dynamicRangeField = new JFormattedTextField(numberFormat);
		dynamicRangeField.setValue(SpectrogramSettings.DEFAULT_DYNAMIC_RANGE);
		add(new JLabel("Dynamic range:"), cc.xy(1, 7));
		add(dynamicRangeField, cc.xy(2, 7));
		add(new JLabel("(Hz)"), cc.xy(3, 7));
	}
	
	public SpectrogramSettings getSettings() {
		final SpectrogramSettings retVal = new SpectrogramSettings();
		
		final double windowLength = ((Number)windowLengthField.getValue()).doubleValue();
		retVal.setWindowLength(windowLength);
		
		final double maxFreq = ((Number)maxFreqField.getValue()).doubleValue();
		retVal.setMaxFrequency(maxFreq);
		
		final double timeStep = ((Number)timeStepField.getValue()).doubleValue();
		retVal.setTimeStep(timeStep);
		
		final double freqStep = ((Number)freqStepField.getValue()).doubleValue();
		retVal.setFrequencyStep(freqStep);
		
		final kSound_to_Spectrogram_windowShape windowShape = 
				(kSound_to_Spectrogram_windowShape)windowShapeBox.getSelectedItem();
		retVal.setWindowShape(windowShape);
		
		final double preEmphasis = ((Number)preEmphasisField.getValue()).doubleValue();
		retVal.setPreEmphasis(preEmphasis);
		
		final double dynamicRange = ((Number)dynamicRangeField.getValue()).doubleValue();
		retVal.setDynamicRange(dynamicRange);
		
		return retVal;
	}
	
}
