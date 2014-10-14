package ca.phon.plugins.praat.painters;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.groovy.tools.shell.commands.SetCommand;

import ca.hedlund.jpraat.binding.fon.Intensity;
import ca.phon.plugins.praat.IntensitySettings;

public class IntensityPainter extends CachingPainter<Intensity> {
	
	private final Color intensityColor = Color.YELLOW;
	
	private IntensitySettings settings = new IntensitySettings();

	public IntensityPainter() {
		super();
	}
	
	public void setSettings(IntensitySettings settings) {
		this.settings = settings;
		setImage(null, null);
	}
	
	public IntensitySettings getSettings() {
		return this.settings;
	}
	
	@Override
	public void paintGarnish(Graphics2D g2d, Rectangle2D bounds, int location) {
	}

	@Override
	protected BufferedImage createImage(double width, double height) {
		final Intensity intensity = getValue();
		if(intensity == null) return null;
		
		BufferedImage intensityImg = new BufferedImage((int)width, (int)height,
				BufferedImage.TYPE_INT_ARGB);
		
		final Graphics2D g2d = (Graphics2D)intensityImg.createGraphics();
		final Rectangle2D bounds = new Rectangle2D.Double(0.0, 0.0, 
				(double)width, (double)height);
		g2d.setBackground(new Color(0, 0, 0, 0));
		g2d.clearRect((int)bounds.getX(), 
				(int)bounds.getY(), (int)bounds.getWidth(), (int)bounds.getHeight());
		paintIntensity(g2d, bounds);
		
		return intensityImg;
	}
	
	private void paintIntensity(Graphics2D g2d, Rectangle2D bounds) {
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		
		final Intensity intensity = getValue();
		if(intensity == null) return;
		
		double tmin = intensity.getXMin();
		double tmax = intensity.getXMax();
		
		final double len = (tmax - tmin);
		if(len <= 0.0) return;
		
		double dbMin = settings.getViewRangeMin();
		double dbMax = settings.getViewRangeMax();
		double range = Math.abs(dbMax - dbMin);
		
		final double unitsPerPixel = range / bounds.getHeight();
		final double pixelPerSec = bounds.getWidth() / len;
		
		final AtomicReference<Long> iminRef = new AtomicReference<Long>();
		final AtomicReference<Long> imaxRef = new AtomicReference<Long>();
		
		intensity.getWindowSamples(tmin, tmax, iminRef, imaxRef);
		
		final int firstFrame = iminRef.get().intValue();
		final int lastFrame = imaxRef.get().intValue();
		
		g2d.setColor(intensityColor);
		
		double lastY = Double.POSITIVE_INFINITY;
		double lastX = Double.POSITIVE_INFINITY;
		for(int i = firstFrame; i <= lastFrame; i++) {
			double time = intensity.indexToX(i);
			double v = intensity.getValueAtSample(i, Intensity.AVERAGING_MEDIAN, Intensity.UNITS_DB);
			
			double x = bounds.getX()  + ((time - tmin) * pixelPerSec);
			double y = (bounds.getY() + bounds.getHeight()) - ((v-dbMin) / unitsPerPixel);
			
			if(lastX != Double.POSITIVE_INFINITY && lastY != Double.POSITIVE_INFINITY) {
				final Line2D line = new Line2D.Double(lastX, lastY, x, y);
				g2d.draw(line);
			}
			lastX = x;
			lastY = y;
		}
	}

}
