package ca.phon.plugins.praat.painters;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.ImageIcon;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import ca.hedlund.jpraat.binding.fon.Formant;
import ca.hedlund.jpraat.binding.stat.Table;
import ca.phon.plugins.praat.FormantSettings;
import ca.phon.util.icons.IconManager;
import ca.phon.util.icons.IconSize;

public class FormantPainter extends CachingPainter<Formant> {
	
	private FormantSettings settings = new FormantSettings();
	
	private double maxFrequency = settings.getMaxFrequency();

	@Override
	protected BufferedImage createImage(double width, double height) {
		final Formant formants = getValue();
		if(formants == null) return null;
		
		BufferedImage formantImg = new BufferedImage((int)width, (int)height,
                BufferedImage.TYPE_INT_ARGB);
		
		final Graphics2D g2d = (Graphics2D)formantImg.createGraphics();
		final Rectangle2D bounds = new Rectangle2D.Double(0.0, 0.0, 
				(double)width, (double)height);
		g2d.setBackground(new Color(0, 0, 0, 0));
		g2d.clearRect((int)bounds.getX(), 
				(int)bounds.getY(), (int)bounds.getWidth(), (int)bounds.getHeight());
		paintFormants(g2d, bounds);
		
		return formantImg;
	}
	
	public FormantSettings getSettings() {
		return this.settings;
	}
	
	public void setSettings(FormantSettings settings) {
		this.settings = settings;
		setImage(null, null);
	}
	
	private void paintFormants(Graphics2D g2d, Rectangle2D bounds) {
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		
		final Formant formants = getValue();
		if(formants == null) return;
		
		final double tmin = formants.getXMin();
		final double tmax = formants.getXMax();
		
		final double len = (tmax - tmin);
		if(len <= 0.0) return;
		
		final double pixelPerSec = bounds.getWidth() / len;
		final double freqPerPixel = getMaxFrequency() / bounds.getHeight();
	
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
				Math.ceil(0.5 * settings.getDotSize() * Toolkit.getDefaultToolkit().getScreenResolution() / 25.4);
		
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
					double y = (bounds.getY() + bounds.getHeight()) - (freq / freqPerPixel);
					
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
	public void paintGarnish(Graphics2D g2d, Rectangle2D bounds, int location) {
		// don't paint formant garnish
	}
	
}
