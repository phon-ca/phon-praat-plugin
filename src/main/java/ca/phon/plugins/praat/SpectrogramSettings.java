/*
 * phon-textgrid-plugin
 * Copyright (C) 2015, Gregory Hedlund <ghedlund@mun.ca>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ca.phon.plugins.praat;

import java.util.prefs.Preferences;

import ca.hedlund.jpraat.binding.fon.kSound_to_Spectrogram_windowShape;
import ca.phon.util.PrefHelper;

/**
 * Settings used for generating the Spectrogram.
 *
 */
public class SpectrogramSettings {

	public final static double DEFAULT_WINDOW_LENGTH = 0.005;
	public final static String WINDOW_LENGTH_PROP = SpectrogramSettings.class.getName() + ".windowLength";
	private double windowLength = getDefaultWindowLength();
	
	public final static double DEFAULT_MAX_FREQUENCY = 5000.0;
	public final static String MAX_FREQUENCY_PROP = SpectrogramSettings.class.getName() + ".maxFreq";
	private double maxFrequency = getDefaultMaxFrequency();
			
	
	public final static double DEFAULT_TIME_STEP = 0.002;
	public final static String TIME_STEP_PROP = SpectrogramSettings.class.getName() + ".timeStep";
	private double timeStep = getDefaultTimeStep();
			
	
	public final static double DEFAULT_FREQUENCY_STEP = 20.0;
	public final static String FREQUENCY_STEP_PROP = SpectrogramSettings.class.getName() + ".freqStep";
	private double frequencyStep = getDefaultFrequencyStep();
			
	
	public final static kSound_to_Spectrogram_windowShape DEFAULT_WINDOW_SHAPE = kSound_to_Spectrogram_windowShape.GAUSSIAN;
	public final static String WINDOW_SHAPE_PROP = SpectrogramSettings.class.getName() + ".windowShape";
	private kSound_to_Spectrogram_windowShape windowShape = getDefaultWindowShape();
			
	
	public final static double DEFAULT_PREEMPHASIS = 6.0;
	public final static String PREEMPHASIS_PROP = SpectrogramSettings.class.getName() + ".preEmphasis";
	private double preEmphasis = getDefaultPreEmphasis();
			
	
	public final static double DEFAULT_DYNAMIC_RANGE = 70.0;
	public final static String DYNAMIC_RANGE_PROP = SpectrogramSettings.class.getName() + ".dynamicRange";
	private double dynamicRange = getDefaultDynamicRange();
	
	public final static double DEFAULT_DYNAMIC_COMPRESSION = 0.0;
	public final static String DYNAMIC_COMPRESSION_PROP = SpectrogramSettings.class.getName() + ".dynamicCompression";
	private double dynamicCompression = getDefaultDynamicCompression();
	
	public static double getDefaultDynamicCompression() {
		return PrefHelper.getDouble(DYNAMIC_COMPRESSION_PROP, DEFAULT_DYNAMIC_COMPRESSION);
	}
	public double getDynamicCompression() {
		return dynamicCompression;
	}
	public void setDynamicCompression(double dynamicCompression) {
		this.dynamicCompression = dynamicCompression;
	}
	
	public static double getDefaultWindowLength() {
		return PrefHelper.getDouble(WINDOW_LENGTH_PROP, DEFAULT_WINDOW_LENGTH);
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
	
	public static double getDefaultMaxFrequency() {
		return PrefHelper.getDouble(MAX_FREQUENCY_PROP, DEFAULT_MAX_FREQUENCY);
	}
	public void setMaxFrequency(double maxFrequency) {
		this.maxFrequency = maxFrequency;
	}
	
	public static double getDefaultTimeStep() {
		return PrefHelper.getDouble(TIME_STEP_PROP, DEFAULT_TIME_STEP);
	}
	public double getTimeStep() {
		return timeStep;
	}
	public void setTimeStep(double timeStep) {
		this.timeStep = timeStep;
	}
	
	public static double getDefaultFrequencyStep() {
		return PrefHelper.getDouble(FREQUENCY_STEP_PROP, DEFAULT_FREQUENCY_STEP);
	}
	public double getFrequencyStep() {
		return frequencyStep;
	}
	public void setFrequencyStep(double frequencyStep) {
		this.frequencyStep = frequencyStep;
	}
	
	public static kSound_to_Spectrogram_windowShape getDefaultWindowShape() {
		return kSound_to_Spectrogram_windowShape.values()[PrefHelper.getInt(WINDOW_SHAPE_PROP, DEFAULT_WINDOW_SHAPE.ordinal())];
	}
	public kSound_to_Spectrogram_windowShape getWindowShape() {
		return windowShape;
	}
	public void setWindowShape(kSound_to_Spectrogram_windowShape windowShape) {
		this.windowShape = windowShape;
	}
	
	public static double getDefaultPreEmphasis() {
		return PrefHelper.getDouble(PREEMPHASIS_PROP, DEFAULT_PREEMPHASIS);
	}
	public double getPreEmphasis() {
		return preEmphasis;
	}
	public void setPreEmphasis(double preEmphasis) {
		this.preEmphasis = preEmphasis;
	}
	
	public static double getDefaultDynamicRange() {
		return PrefHelper.getDouble(DYNAMIC_RANGE_PROP, DEFAULT_DYNAMIC_RANGE);
	}
	public double getDynamicRange() {
		return dynamicRange;
	}
	public void setDynamicRange(double dynamicRange) {
		this.dynamicRange = dynamicRange;
	}
	
	/**
	 * Save these settings as custom defaults.
	 * 
	 * 
	 */
	public void saveAsDefaults() {
		final Preferences prefs = PrefHelper.getUserPreferences();
		prefs.putDouble(DYNAMIC_COMPRESSION_PROP, getDynamicCompression());
		prefs.putDouble(DYNAMIC_RANGE_PROP, getDynamicRange());
		prefs.putDouble(FREQUENCY_STEP_PROP, getFrequencyStep());
		prefs.putDouble(MAX_FREQUENCY_PROP, getMaxFrequency());
		prefs.putDouble(PREEMPHASIS_PROP, getPreEmphasis());
		prefs.putDouble(TIME_STEP_PROP, getTimeStep());
		prefs.putDouble(WINDOW_LENGTH_PROP, getWindowLength());
		prefs.putInt(WINDOW_SHAPE_PROP, getWindowShape().ordinal());
	}
	
	public void loadStandards() {
		setDynamicCompression(DEFAULT_DYNAMIC_COMPRESSION);
		setDynamicRange(DEFAULT_DYNAMIC_RANGE);
		setFrequencyStep(DEFAULT_FREQUENCY_STEP);
		setMaxFrequency(DEFAULT_MAX_FREQUENCY);
		setPreEmphasis(DEFAULT_PREEMPHASIS);
		setTimeStep(DEFAULT_TIME_STEP);
		setWindowLength(DEFAULT_WINDOW_LENGTH);
		setWindowShape(DEFAULT_WINDOW_SHAPE);
	}
	
	public void loadDefaults() {
		setDynamicCompression(getDefaultDynamicCompression());
		setDynamicRange(getDefaultDynamicRange());
		setFrequencyStep(getDefaultFrequencyStep());
		setMaxFrequency(getDefaultMaxFrequency());
		setPreEmphasis(getDefaultPreEmphasis());
		setTimeStep(getDefaultTimeStep());
		setWindowLength(getDefaultWindowLength());
		setWindowShape(getDefaultWindowShape());
	}
	
}
