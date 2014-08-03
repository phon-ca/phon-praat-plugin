package ca.phon.plugins.praat.painters;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

import ca.hedlund.jpraat.binding.fon.Formant;
import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.Pitch;
import ca.hedlund.jpraat.binding.fon.kPitch_unit;
import ca.phon.plugins.praat.PitchSettings;

public class PitchSpecklePainter extends CachingPainter<Pitch> {
	
	private PitchSettings settings = new PitchSettings();
	
	public PitchSpecklePainter() {
		super();
	}
	
	public void setSettings(PitchSettings settings) {
		this.settings = settings;
		setImage(null);
	}
	
	public PitchSettings getSettings() {
		return this.settings;
	}

	@Override
	protected BufferedImage createImage(double width, double height) {
		final Pitch pitch = getValue();
		if(pitch == null) return null;
		
		BufferedImage pitchImg = new BufferedImage((int)width, (int)height,
                BufferedImage.TYPE_INT_ARGB);
		
		final Graphics2D g2d = (Graphics2D)pitchImg.createGraphics();
		final Rectangle2D bounds = new Rectangle2D.Double(0.0, 0.0, 
				(double)width, (double)height);
		g2d.setBackground(new Color(0, 0, 0, 0));
		g2d.clearRect((int)bounds.getX(), 
				(int)bounds.getY(), (int)bounds.getWidth(), (int)bounds.getHeight());
		paintPitch(g2d, bounds);
		
		return pitchImg;
	}
	
	private void paintPitch(Graphics2D g2d, Rectangle2D bounds) {
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		
		final Pitch pitch = getValue();
		if(pitch == null) return;
		
		final double tmin = pitch.getXMin();
		final double tmax = pitch.getXMax();
		
		final double len = (tmax - tmin);
		if(len <= 0.0) return;
		
		double fmin = settings.getRangeStart();
		if(settings.getUnits().ordinal() != kPitch_unit.HERTZ.ordinal()) {
			fmin = pitch.convertStandardToSpecialUnit(fmin, Pitch.LEVEL_FREQUENCY,
					settings.getUnits().ordinal());
		}
		
		double fmax = settings.getRangeEnd();
		if(settings.getUnits().ordinal() > kPitch_unit.HERTZ.ordinal()) {
			fmax = pitch.convertStandardToSpecialUnit(fmax, Pitch.LEVEL_FREQUENCY,
					settings.getUnits().ordinal());
		}
		double range = Math.abs(fmax - fmin);
		
		final double unitsPerPixel = range / bounds.getHeight();
		final double pixelPerSec = bounds.getWidth() / len;
		
		final Pointer pimin =  new Memory(Native.getNativeSize(Long.TYPE));
		final Pointer pimax =  new Memory(Native.getNativeSize(Long.TYPE));
		
		pitch.getWindowSamples(tmin, tmax, pimin, pimax);
		
		final int firstFrame = (int)pimin.getLong(0);
		final int lastFrame = (int)pimax.getLong(0);
		
		final double radius =
				Math.ceil(0.5 * settings.getDotSize() * Toolkit.getDefaultToolkit().getScreenResolution() / 25.4);
		
		for(int i = firstFrame; i <= lastFrame; i++) {
			double time = pitch.indexToX(i);
			double v = pitch.getValueAtSample(i, Pitch.LEVEL_FREQUENCY, settings.getUnits().ordinal());
			if(Double.isInfinite(v) || Double.isNaN(v) ) continue;
			if(!pitch.isUnitLogarithmic(Pitch.LEVEL_FREQUENCY, settings.getUnits().ordinal()))
				v = pitch.convertToNonlogarithmic(v, Pitch.LEVEL_FREQUENCY, settings.getUnits().ordinal());
			
			double x = bounds.getX() + ((time - tmin) * pixelPerSec);
			double y = (bounds.getY() + bounds.getHeight()) - ((v - fmin) / unitsPerPixel);
			
			final Ellipse2D circle = new Ellipse2D.Double();
			circle.setFrameFromCenter(x, y, x + radius, y + radius);
			
			g2d.setColor(Color.cyan);
			g2d.fill(circle);

			circle.setFrameFromCenter(x, y, x + (radius - 1), y + (radius - 1));
			g2d.setColor(Color.blue);
			g2d.fill(circle);
		}
	}

	@Override
	public void paintGarnish(Graphics2D g2d, Rectangle2D bounds, int location) {
		final Pitch pitch = getValue();
		final WString unitText = pitch.getUnitText(Pitch.LEVEL_FREQUENCY, 
				settings.getUnits(), Function.UNIT_TEXT_SHORT);
		final double startValue = 
				pitch.convertStandardToSpecialUnit(settings.getRangeStart(), Pitch.LEVEL_FREQUENCY, 
						settings.getUnits().ordinal());
		final double endValue =
				pitch.convertStandardToSpecialUnit(settings.getRangeEnd(), Pitch.LEVEL_FREQUENCY,
						settings.getUnits().ordinal());
		
		final NumberFormat nf = NumberFormat.getNumberInstance();
		nf.setMinimumFractionDigits(1);
		nf.setMaximumFractionDigits(1);
		nf.setGroupingUsed(false);
		
		final FontMetrics fm = g2d.getFontMetrics();
		
		final String startTxt = nf.format(startValue) + " " + unitText.toString();
		int y = 
				(int)Math.round(
						(bounds.getY() + bounds.getHeight()) - 1);
		int x = (int)Math.round(bounds.getX());
		g2d.setColor(Color.blue);
		g2d.drawString(startTxt, x, y);
		
		final String endTxt = nf.format(endValue) + " " + unitText.toString();
		final Rectangle2D endBounds = fm.getStringBounds(endTxt, g2d);
		y = 
				(int)Math.round(
						(bounds.getY()) + endBounds.getHeight());
		g2d.drawString(endTxt, x, y);
	}
	
}
