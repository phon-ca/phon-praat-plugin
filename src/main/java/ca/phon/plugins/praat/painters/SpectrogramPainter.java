package ca.phon.plugins.praat.painters;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageObserver;
import java.awt.image.ReplicateScaleFilter;
import java.text.NumberFormat;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingConstants;

import ca.hedlund.jpraat.binding.fon.Spectrogram;
import ca.phon.plugins.praat.ColorMap;
import ca.phon.plugins.praat.SpectrogramSettings;

/**
 * Spectrogram painter.
 *
 */
public class SpectrogramPainter extends CachingPainter<Spectrogram> {
	
	private final static double NUMln2 = 0.6931471805599453094172321214581765680755;
	private final static double NUMln10 = 2.3025850929940456840179914546843642076011;
	
	private SpectrogramSettings settings;
	
	private ColorMap colorMap = ColorMap.getGreyscale(255);
	
	private AtomicReference<Image> scaledImgRef = new AtomicReference<Image>();
	
	private AtomicReference<Rectangle2D> boundsRef = new AtomicReference<Rectangle2D>();
	
	public SpectrogramPainter() {
		this(new SpectrogramSettings());
	}
	
	@Override
	public void setValue(Spectrogram obj) {
		super.setValue(obj);
		setScaledImage(null, null);
	}
	
	public SpectrogramPainter(SpectrogramSettings settings) {
		super();
		this.settings = settings;
	}
	
	public SpectrogramSettings getSettings() {
		return settings;
	}

	public void setSettings(SpectrogramSettings settings) {
		this.settings = settings;
	}

	public ColorMap getColorMap() {
		return colorMap;
	}

	public void setColorMap(ColorMap colorMap) {
		this.colorMap = colorMap;
	}
	
	@Override
	public BufferedImage createImage(double width, double height) {
		final Spectrogram spectrogram = getValue();
		if(spectrogram == null) return null;
		
		final int w = (int)spectrogram.getNx();
		final int h = (int)spectrogram.getNy();
		
		BufferedImage spectrogramImg = new BufferedImage(
				(int)w, (int)h,
                BufferedImage.TYPE_INT_RGB);
		
		final Graphics2D g2d = (Graphics2D)spectrogramImg.createGraphics();
		final Rectangle2D bounds = new Rectangle2D.Double(0.0, 0.0, 
				w, h);
		g2d.setColor(Color.white);
		g2d.fill(bounds);
		paintSpectrogram(g2d, bounds);

		return spectrogramImg;
	}
		
	private void paintSpectrogram(Graphics2D g2d, Rectangle2D bounds) {
//		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		
		final Spectrogram spectrogram = getValue();
		if(spectrogram == null) return;
		
		final int numFrames = (int)spectrogram.getNx();
    	final int numBins = (int)spectrogram.getNy();
    	final double[] preemphasisFactor = new double[numBins];
    	final double[] dynamicFactor = new double[numFrames];
    	
    	final double[][] dbData = new double[numFrames][];
    	
    	for(int ifreq = 0; ifreq < numBins; ifreq++) {
			preemphasisFactor[ifreq] = preEmphasis(spectrogram.getDy(), ifreq);
			for(int itime = 0; itime < numFrames; itime++) {
				if(dbData[itime] == null) {
					dbData[itime] = new double[numBins];
				}
				double value = spectrogram.getZ(itime+1, ifreq+1);
				value = dbValue(preemphasisFactor[ifreq], value);
				if(value > dynamicFactor[itime]) dynamicFactor[itime] = value;
				dbData[itime][ifreq] = value;
			}
    	}
    	
    	// autoscaling
    	double maximum = 0.0;
		for (int itime = 0; itime < numFrames; itime ++)
			if (dynamicFactor [itime] > maximum) maximum = dynamicFactor [itime];
		
		// dynamic compression
		for (int itime = 0; itime < numFrames; itime ++) {
			dynamicFactor [itime] = settings.getDynamicCompression() * (maximum - dynamicFactor [itime]);
			for (int ifreq = 0; ifreq < numBins; ifreq ++)
				dbData [itime] [ifreq] += dynamicFactor [itime];
		}
		
		double minIntensity = maximum - settings.getDynamicRange();
		
		double cellWidth = bounds.getWidth() / numFrames;
		double cellHeight = bounds.getHeight() / numBins;
		
		 double scaleFactor = (colorMap.size() / settings.getDynamicRange());
         for (int i = 0; i < numFrames; i++) {                
             for (int j = numBins-1; j >= 0; j--) {
             	double dataVal = dbData[i][j];
             	if(dataVal < minIntensity)
             		dataVal = minIntensity;
             	if(dataVal > maximum)
             		dataVal = maximum;
                 int grey = (int)Math.round( (dataVal - minIntensity) * scaleFactor);
                 if(grey >= colorMap.size())
                 	grey = colorMap.size()-1;
                 
                 int cVal = colorMap.getColor(grey);
                 
                 Color c = new Color(cVal >> 16 & 0xff, cVal >> 8 & 0xff, cVal & 0xff);
                 g2d.setColor(c);
                 
                 // TODO interpolate (i.e., calculate transparency of each cell)
                 
                 final Rectangle2D cellRect = new Rectangle2D.Double(
                		 bounds.getX() + i * cellWidth, (bounds.getY() + bounds.getHeight()) - ( (j+1) * cellHeight),
                		 cellWidth, cellHeight);
                 g2d.fill(cellRect);
             }
         }
	}
	
	private double preEmphasis(double dy, int ifreq) {
		return (settings.getPreEmphasis() / NUMln2) * Math.log(ifreq * dy / 1000.0);
	}
	
	private double dbValue(double preemphasis, double value) {
		return (10.0/NUMln10) * Math.log((value + 1e-30) / 4.0e-10) + preemphasis;  // dB
	}

	@Override
	public void paintGarnish(Graphics2D g2d, Rectangle2D bounds, int location) {
		final double startFreq = 0;
		final double endFreq = settings.getMaxFrequency();
		
		// draw start freq
		g2d.setColor(Color.black);
		final FontMetrics fm = g2d.getFontMetrics();
		
		final NumberFormat nf = NumberFormat.getNumberInstance();
		nf.setGroupingUsed(false);
		nf.setMinimumFractionDigits(1);
		nf.setMaximumFractionDigits(1);
		
		final String startFreqTxt = nf.format(startFreq) + " Hz";
		final Rectangle2D startBounds = fm.getStringBounds(startFreqTxt, g2d);
		int y = 
				(int)Math.round((bounds.getY() + bounds.getHeight())) - fm.getDescent();
		int x = 
				(int)Math.round((bounds.getX() + bounds.getWidth()) - startBounds.getWidth());
		g2d.drawString(startFreqTxt, x, y);
		
		final String endFreqTxt = nf.format(endFreq) + " Hz";
		final Rectangle2D endBounds = fm.getStringBounds(endFreqTxt, g2d);
		y = 
				(int)Math.round(bounds.getY() + endBounds.getHeight()) - fm.getDescent();
		x = 
				(int)Math.round((bounds.getX() + bounds.getWidth()) - endBounds.getWidth());
		g2d.drawString(endFreqTxt, x, y);
	}
	
	public Image getScaledImage(Image img, Rectangle2D bounds) {
		boolean create = scaledImgRef.get() == null;
		Rectangle2D lastBounds = boundsRef.get();
		
		if(lastBounds == null || lastBounds.getWidth() != bounds.getWidth()
				|| lastBounds.getHeight() != bounds.getHeight()) {
			create = true;
		}
		
		if(create) {
			final ImageFilter filter = new ReplicateScaleFilter((int)bounds.getWidth(), (int)bounds.getHeight());
			final FilteredImageSource src = new FilteredImageSource(img.getSource(), filter);
			final Image scaledImg = Toolkit.getDefaultToolkit().createImage(src);
			setScaledImage(scaledImg, bounds);
		}
		return scaledImgRef.get();
	}
	
	public void setScaledImage(Image img, Rectangle2D bounds) {
		boundsRef.set(bounds);
		scaledImgRef.set(img);
	}

	@Override
	public void paintInside(Graphics2D g2, Rectangle2D bounds) {
		final Spectrogram spectrogram = getValue();
		if(spectrogram == null) return;
		
		final Rectangle2D spectrogramBounds = 
				new Rectangle2D.Double(bounds.getX(), bounds.getY(), (double)spectrogram.getNx(), (double)spectrogram.getNy());
		final Image spectrogramImage = getImage(spectrogramBounds);
		if(spectrogramImage == null) return;
		
		final Image scaledImage = getScaledImage(spectrogramImage, bounds);
		if(scaledImage != null) {
			g2.drawImage(scaledImage, (int)bounds.getX(), (int)bounds.getY(), (ImageObserver) null);
		}
	}
	
}
