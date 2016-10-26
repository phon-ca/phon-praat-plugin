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
package ca.phon.plugins.praat.painters;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.atomic.AtomicReference;

import ca.hedlund.jpraat.binding.fon.Formant;
import ca.phon.plugins.praat.FormantSettings;
import ca.phon.ui.painter.BufferedPainter;

public class FormantPainter extends BufferedPainter<Formant> implements PraatPainter<Formant> {
	
	private FormantSettings settings = new FormantSettings();
	
	private double maxFrequency = settings.getMaxFrequency();

	public FormantPainter() {
		this(new FormantSettings());
	}
	
	public FormantPainter(FormantSettings settings) {
		super();
		super.setResizeMode(ResizeMode.REPAINT_ON_RESIZE);
		this.settings = settings;
	}
	
	public FormantSettings getSettings() {
		return this.settings;
	}
	
	public void setSettings(FormantSettings settings) {
		this.settings = settings;
	}
	
	@Override
	protected void paintBuffer(Formant formants, Graphics2D g2d, Rectangle2D bounds) {
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		
		if(formants == null) return;
		
		final double tmin = formants.getXMin();
		final double tmax = formants.getXMax();
		
		final double len = (tmax - tmin);
		if(len <= 0.0) return;
		
		final double pixelPerSec = bounds.getWidth() / len;
		final double pixelPerFreq = bounds.getHeight() / getMaxFrequency();
	
		final AtomicReference<Long> iminRef = new AtomicReference<Long>();
		final AtomicReference<Long> imaxRef = new AtomicReference<Long>();
		
		formants.getWindowSamples(tmin, tmax, iminRef, imaxRef);
		
		final int firstFrame = iminRef.get().intValue();
		final int lastFrame = imaxRef.get().intValue();
		
		double minIntensity = 0.0;
		double maxIntensity = minIntensity;
		for(int i = firstFrame; i <= lastFrame; i++) {
			double intensity = formants.getIntensityAtSample(i);
			maxIntensity = Math.max(maxIntensity, intensity);
		}
		if(maxIntensity == 0.0 || settings.getDynamicRange() <= 0.0) {
			minIntensity = 0.0;
		} else {
			minIntensity = maxIntensity / Math.pow(10.0, settings.getDynamicRange() / 10.0);
		}
		
		final double radius =
				((0.5 * settings.getDotSize()) * Toolkit.getDefaultToolkit().getScreenResolution()) / 25.4;
		
		g2d.setColor(Color.red);
		for(int i = firstFrame; i <= lastFrame; i++) {
			double time = formants.indexToX(i);
			double intensity = formants.getIntensityAtSample(i);
			
			if(intensity < minIntensity) continue;
			
			for(int iformant = 1; iformant <= settings.getNumFormants(); iformant++) {
				long which = (iformant << 1);
				double freq = formants.getValueAtSample(i, which, 0);
				if(freq > getMaxFrequency()) continue;
				if(!Double.isInfinite(freq) && !Double.isNaN(freq)) {
					double x = bounds.getX() + ((time - tmin) * pixelPerSec);
					double y = (bounds.getY() + bounds.getHeight()) - (freq * pixelPerFreq);
					
					final Ellipse2D circle = new Ellipse2D.Double();
					circle.setFrameFromCenter(x, y, x + radius, y + radius);
					
					g2d.fill(circle);
				}
			}
		}
	}

	public double getMaxFrequency() {
		return maxFrequency;
	}

	public void setMaxFrequency(double maxFrequency) {
		this.maxFrequency = maxFrequency;
	}

	@Override
	public void paintGarnish(Formant formants, Graphics2D g2d, Rectangle2D bounds, int location) {
	}
	
}
