package ca.phon.plugins.praat;

import ca.hedlund.jpraat.binding.fon.kSound_to_Spectrogram_windowShape;
import ca.phon.util.PrefHelper;

/**
 * Settings used for generating the Spectrogram.
 *
 */
public class SpectrogramSettings {

	public final static double DEFAULT_WINDOW_LENGTH = 0.005;
	public final static String WINDOW_LENGTH_PROP = SpectrogramSettings.class.getName() + ".windowLength";
	private double windowLength = 
			PrefHelper.getDouble(WINDOW_LENGTH_PROP, DEFAULT_WINDOW_LENGTH);
	
	public final static double DEFAULT_MAX_FREQUENCY = 5000.0;
	public final static String MAX_FREQUENCY_PROP = SpectrogramSettings.class.getName() + ".maxFreq";
	private double maxFrequency = 
			PrefHelper.getDouble(MAX_FREQUENCY_PROP, DEFAULT_MAX_FREQUENCY);
	
	public final static double DEFAULT_TIME_STEP = 0.002;
	public final static String TIME_STEP_PROP = SpectrogramSettings.class.getName() + ".timeStep";
	private double timeStep = 
			PrefHelper.getDouble(TIME_STEP_PROP, DEFAULT_TIME_STEP);
	
	public final static double DEFAULT_FREQUENCY_STEP = 20.0;
	public final static String FREQUENCY_STEP_PROP = SpectrogramSettings.class.getName() + ".freqStep";
	private double frequencyStep = 
			PrefHelper.getDouble(FREQUENCY_STEP_PROP, DEFAULT_FREQUENCY_STEP);
	
	public final static kSound_to_Spectrogram_windowShape DEFAULT_WINDOW_SHAPE = kSound_to_Spectrogram_windowShape.GAUSSIAN;
	public final static String WINDOW_SHAPE_PROP = SpectrogramSettings.class.getName() + ".windowShape";
	private kSound_to_Spectrogram_windowShape windowShape = 
			kSound_to_Spectrogram_windowShape.values()[PrefHelper.getInt(WINDOW_SHAPE_PROP, DEFAULT_WINDOW_SHAPE.ordinal())];
	
	public final static double DEFAULT_PREEMPHASIS = 6.0;
	public final static String PREEMPHASIS_PROP = SpectrogramSettings.class.getName() + ".preEmphasis";
	private double preEmphasis = 
			PrefHelper.getDouble(PREEMPHASIS_PROP, DEFAULT_PREEMPHASIS);
	
	public final static double DEFAULT_DYNAMIC_RANGE = 50.0;
	public final static String DYNAMIC_RANGE_PROP = SpectrogramSettings.class.getName() + ".dynamicRange";
	private double dynamicRange = 
			PrefHelper.getDouble(DYNAMIC_RANGE_PROP, DEFAULT_DYNAMIC_RANGE);
	
	public final static boolean DEFAULT_USE_COLOR = Boolean.FALSE;
	public final static String USE_COLOR_PROP = SpectrogramSettings.class.getName() + ".useColor";
	private boolean useColor = 
			PrefHelper.getBoolean(USE_COLOR_PROP, DEFAULT_USE_COLOR);
	
	public final static double DEFAULT_DYNAMIC_COMPRESSION = 0.0;
	public final static String DYNAMIC_COMPRESSION_PROP = SpectrogramSettings.class.getName() + ".dynamicCompression";
	private double dynamicCompression =
			PrefHelper.getDouble(DYNAMIC_COMPRESSION_PROP, DEFAULT_DYNAMIC_COMPRESSION);
	
	public double getDynamicCompression() {
		return dynamicCompression;
	}
	public void setDynamicCompression(double dynamicCompression) {
		this.dynamicCompression = dynamicCompression;
	}
	public double getWindowLength() {
		return windowLength;
	}
	public void setWindowLength(double windowLength) {
		this.windowLength = windowLength;
	}
	public double getMaxFrequency() {
		return maxFrequency;
	}
	public void setMaxFrequency(double maxFrequency) {
		this.maxFrequency = maxFrequency;
	}
	public double getTimeStep() {
		return timeStep;
	}
	public void setTimeStep(double timeStep) {
		this.timeStep = timeStep;
	}
	public double getFrequencyStep() {
		return frequencyStep;
	}
	public void setFrequencyStep(double frequencyStep) {
		this.frequencyStep = frequencyStep;
	}
	public kSound_to_Spectrogram_windowShape getWindowShape() {
		return windowShape;
	}
	public void setWindowShape(kSound_to_Spectrogram_windowShape windowShape) {
		this.windowShape = windowShape;
	}
	public double getPreEmphasis() {
		return preEmphasis;
	}
	public void setPreEmphasis(double preEmphasis) {
		this.preEmphasis = preEmphasis;
	}
	public double getDynamicRange() {
		return dynamicRange;
	}
	public void setDynamicRange(double dynamicRange) {
		this.dynamicRange = dynamicRange;
	}
	public boolean isUseColor() {
		return useColor;
	}
	public void setUseColor(boolean useColor) {
		this.useColor = useColor;
	}
	
}
