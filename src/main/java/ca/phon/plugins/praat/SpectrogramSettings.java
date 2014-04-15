package ca.phon.plugins.praat;

import ca.hedlund.jpraat.binding.fon.kSound_to_Spectrogram_windowShape;

/**
 * Settings used for generating the Spectrogram.
 *
 */
public class SpectrogramSettings {

	public final static double DEFAULT_WINDOW_LENGTH = 0.005;
	private double windowLength = DEFAULT_WINDOW_LENGTH;
	
	public final static double DEFAULT_MAX_FREQUENCY = 5000.0;
	private double maxFrequency = DEFAULT_MAX_FREQUENCY;
	
	public final static double DEFAULT_TIME_STEP = 0.002;
	private double timeStep = DEFAULT_TIME_STEP;
	
	public final static double DEFAULT_FREQUENCY_STEP = 20.0;
	private double frequencyStep = DEFAULT_FREQUENCY_STEP;
	
	public final static kSound_to_Spectrogram_windowShape DEFAULT_WINDOW_SHAPE = kSound_to_Spectrogram_windowShape.GAUSSIAN;
	private kSound_to_Spectrogram_windowShape windowShape = DEFAULT_WINDOW_SHAPE;
	
	public final static double DEFAULT_PREEMPHASIS = 6.0;
	private double preEmphasis = DEFAULT_PREEMPHASIS;
	
	public final static double DEFAULT_DYNAMIC_RANGE = 50.0;
	private double dynamicRange = DEFAULT_DYNAMIC_RANGE;
	
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
	
}
