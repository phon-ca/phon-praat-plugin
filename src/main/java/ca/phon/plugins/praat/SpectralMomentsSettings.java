/*
 * Copyright (C) 2012-2018 Gregory Hedlund
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *    http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.phon.plugins.praat;

import ca.hedlund.jpraat.binding.fon.kSound_windowShape;
import ca.phon.util.PrefHelper;

import java.util.prefs.Preferences;

public class SpectralMomentsSettings {
	
	private final kSound_windowShape DEFAULT_WINDOW_SHAPE = kSound_windowShape.KAISER_2;
	private final static String WINDOW_SHAPE_PROP = SpectralMomentsSettings.class.getName() + ".windowShape";
	private kSound_windowShape windowShape = getDefaultWindowShape();
	
	public kSound_windowShape getDefaultWindowShape() {
		return kSound_windowShape.valueOf(
				PrefHelper.get(WINDOW_SHAPE_PROP, DEFAULT_WINDOW_SHAPE.toString()));
	}
	
	public kSound_windowShape getWindowShape() {
		return this.windowShape;
	}
	
	public void setWindowShape(kSound_windowShape windowShape) {
		this.windowShape = windowShape;
	}
	
	private final static double DEFAULT_FILTER_START = 500;
	private final static String FILTER_START_PROP = SpectralMomentsSettings.class.getName() + ".filterStart";
	private double filterStart = getDefaultFilterStart();
	
	public double getDefaultFilterStart() {
		return PrefHelper.getDouble(FILTER_START_PROP, DEFAULT_FILTER_START);
	}
	
	public double getFilterStart() {
		return this.filterStart;
	}
	
	public void setFilterStart(double filterStart) {
		this.filterStart = filterStart;
	}
	
	private final static double DEFAULT_FILTER_END = 15000;
	private final static String FILTER_END_PROP = SpectralMomentsSettings.class.getName() + ".filterEnd";
	private double filterEnd = getDefaultFilterEnd();
	
	public double getDefaultFilterEnd() {
		return PrefHelper.getDouble(FILTER_END_PROP, DEFAULT_FILTER_END);
	}
	
	public double getFilterEnd() {
		return this.filterEnd;
	}
	
	public void setFilterEnd(double filterEnd) {
		this.filterEnd = filterEnd;
	}
	
	private final static double DEFAULT_FILTER_SMOOTHING = 100;
	private final static String FILTER_SMOOTHING_PROP = SpectralMomentsSettings.class.getName() + ".filterSmoothing";
	private double filterSmoothing = getDefaultFilterSmoothing();
	
	public double getDefaultFilterSmoothing() {
		return PrefHelper.getDouble(FILTER_SMOOTHING_PROP, DEFAULT_FILTER_SMOOTHING);
	}
	
	public double getFilterSmoothing() {
		return this.filterSmoothing;
	}
	
	public void setFilterSmoothing(double filterSmoothing) {
		this.filterSmoothing = filterSmoothing;
	}
	
	private final boolean DEFAULT_USE_PREEMPHASIS = true;
	private final static String USE_PREEMPHASIS_PROP = SpectralMomentsSettings.class.getName() + ".usePreemphasis";
	private boolean usePreemphasis = getDefaultUsePreemphasis();
	
	public boolean getDefaultUsePreemphasis() {
		return PrefHelper.getBoolean(USE_PREEMPHASIS_PROP, DEFAULT_USE_PREEMPHASIS);
	}
	
	public boolean isUsePreemphasis() {
		return this.usePreemphasis;
	}
	
	public void setUsePreemphasis(boolean usePreemphasis) {
		this.usePreemphasis = usePreemphasis;
	}
	
	private final static double DEFAULT_PREEMP_FROM = 2000;
	private final static String PREEMP_FROM_PROP = SpectralMomentsSettings.class.getName() + ".preempFrom";
	private double preempFrom = getDefaultPreempFrom();
	
	public double getDefaultPreempFrom() {
		return PrefHelper.getDouble(PREEMP_FROM_PROP, DEFAULT_PREEMP_FROM);
	}
	
	public double getPreempFrom() {
		return this.preempFrom;
	}
	
	public void setPreempFrom(double preempFrom) {
		this.preempFrom = preempFrom;
	}
	
	public void saveAsDefaults() {
		final Preferences prefs = PrefHelper.getUserPreferences();
		prefs.put(WINDOW_SHAPE_PROP, getWindowShape().toString());
		prefs.putDouble(FILTER_START_PROP, getFilterStart());
		prefs.putDouble(FILTER_END_PROP, getFilterEnd());
		prefs.putDouble(FILTER_SMOOTHING_PROP, getFilterSmoothing());
		prefs.putBoolean(USE_PREEMPHASIS_PROP, isUsePreemphasis());
		prefs.putDouble(PREEMP_FROM_PROP, getPreempFrom());
	}
	
	public void loadDefaults() {
		setWindowShape(getDefaultWindowShape());
		setFilterStart(getDefaultFilterStart());
		setFilterEnd(getDefaultFilterEnd());
		setFilterSmoothing(getDefaultFilterSmoothing());
		setUsePreemphasis(getDefaultUsePreemphasis());
		setPreempFrom(getDefaultPreempFrom());
	}
	
	public void loadStandards() {
		setWindowShape(DEFAULT_WINDOW_SHAPE);
		setFilterStart(DEFAULT_FILTER_START);
		setFilterEnd(DEFAULT_FILTER_END);
		setFilterSmoothing(DEFAULT_FILTER_SMOOTHING);
		setUsePreemphasis(DEFAULT_USE_PREEMPHASIS);
		setPreempFrom(DEFAULT_PREEMP_FROM);
	}

}
