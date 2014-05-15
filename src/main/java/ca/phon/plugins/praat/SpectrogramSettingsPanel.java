package ca.phon.plugins.praat;

import java.text.NumberFormat;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import ca.hedlund.jpraat.binding.fon.kSound_to_Spectrogram_windowShape;
import ca.phon.syllabifier.opgraph.extensions.SyllabifierSettings;

import com.jgoodies.forms.builder.ButtonBarBuilder;
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
	
	private JFormattedTextField dynamicCompressionField;

	public SpectrogramSettingsPanel() {
		super();
		
		init();
	}
	
	private void init() {
		final FormLayout layout = new FormLayout(
				"right:pref, fill:pref:grow, pref",
				"pref, pref, pref, pref, pref, pref, pref, pref");
		setLayout(layout);
		final CellConstraints cc = new CellConstraints();
		
		final NumberFormat numberFormat = NumberFormat.getNumberInstance();
		
		windowLengthField = new JFormattedTextField(numberFormat);
		add(new JLabel("Window length:"), cc.xy(1, 1));
		add(windowLengthField, cc.xy(2, 1));
		add(new JLabel("(s)"), cc.xy(3, 1));
		
		maxFreqField = new JFormattedTextField(numberFormat);
		add(new JLabel("Max frequency:"), cc.xy(1, 2));
		add(maxFreqField, cc.xy(2, 2));
		add(new JLabel("(Hz)"), cc.xy(3, 2));
		
		timeStepField = new JFormattedTextField(numberFormat);
		add(new JLabel("Time step:"), cc.xy(1, 3));
		add(timeStepField, cc.xy(2, 3));
		add(new JLabel("(s)"), cc.xy(3, 3));
		
		freqStepField = new JFormattedTextField(numberFormat);
		add(new JLabel("Frequency step:"), cc.xy(1, 4));
		add(freqStepField, cc.xy(2, 4));
		add(new JLabel("(Hz)"), cc.xy(3, 4));
		
		windowShapeBox = new JComboBox(kSound_to_Spectrogram_windowShape.values());
		add(new JLabel("Window shape:"), cc.xy(1, 5));
		add(windowShapeBox, cc.xy(2, 5));
		
		preEmphasisField = new JFormattedTextField(numberFormat);
		add(new JLabel("Pre-emphasis:"), cc.xy(1, 6));
		add(preEmphasisField, cc.xy(2, 6));
		add(new JLabel("(dB/Hz)"), cc.xy(3, 6));
		
		dynamicRangeField = new JFormattedTextField(numberFormat);
		add(new JLabel("Dynamic range:"), cc.xy(1, 7));
		add(dynamicRangeField, cc.xy(2, 7));
		add(new JLabel("(Hz)"), cc.xy(3, 7));
		
		dynamicCompressionField = new JFormattedTextField(numberFormat);
		add(new JLabel("Dynamic compression:"), cc.xy(1,8));
		add(dynamicCompressionField, cc.xy(2, 8));
		add(new JLabel("(dB)"), cc.xy(3, 8));
		
		loadSettings(new SpectrogramSettings());
	}
	
	public void loadSettings(SpectrogramSettings settings) {
		windowLengthField.setValue(settings.getWindowLength());
		maxFreqField.setValue(settings.getMaxFrequency());
		timeStepField.setValue(settings.getTimeStep());
		freqStepField.setValue(settings.getFrequencyStep());
		windowShapeBox.setSelectedItem(settings.getWindowShape());
		preEmphasisField.setValue(settings.getPreEmphasis());
		dynamicRangeField.setValue(settings.getDynamicRange());
		dynamicCompressionField.setValue(settings.getDynamicCompression());
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
		
		final double dynamicCompression = ((Number)dynamicCompressionField.getValue()).doubleValue();
		retVal.setDynamicCompression(dynamicCompression);
		
		retVal.setUseColor(false);
		
		return retVal;
	}
	
}
