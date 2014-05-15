package ca.phon.plugins.praat;

/*
 * Copyright 1999-2004 Carnegie Mellon University.  
 * Portions Copyright 2002-2004 Sun Microsystems, Inc.  
 * Portions Copyright 2002-2004 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "README" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */

/*
 * Extended for use in MEAPsoft by Ron Weiss (ronw@ee.columbia.edu).
 *
 * These modifications are Copyright 2006 Columbia University.
 */

//package edu.cmu.sphinx.tools.audio;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageObserver;
import java.awt.image.ReplicateScaleFilter;
import java.util.Arrays;

import javax.swing.JPanel;
import javax.swing.SwingWorker;

import ca.hedlund.jpraat.binding.fon.Spectrogram;


/**
 * Creates a graphical representation from a matrix.  Like Matlab's
 * imagesc.
 */
public class SpectrogramPanel extends JPanel {
	
	private final static double NUMln2 = 0.6931471805599453094172321214581765680755;
	private final static double NUMln10 = 2.3025850929940456840179914546843642076011;
	
    /**
     * Where the spectrogram will live.
     */
    private BufferedImage spectrogram = null;

    /**
     * A scaled version of the spectrogram image.
     */
    private Image scaledSpectrogram = null;

    /**
     * The zooming factor.
     */
    private float zoom = 1.0f;
    private float vzoom = 1.0f;
    
    private float dynamicRange = 50.0f;

    /**
     * The data matrix to be displayed
     */
    private Spectrogram data;
    
    private SpectrogramSettings spectrogramSettings;
    
    private ColorMap cmap = ColorMap.getJet(64);

    /**
     * Creates a new SpectrogramPanel for the given data matrix
     */
    public SpectrogramPanel(Spectrogram data, SpectrogramSettings settings) {
        this.data = data;
        this.spectrogramSettings = settings;
    }

    /**
     * Actually creates the Spectrogram image.
     */
    private void computeSpectrogram() {
        try {
        	final int numFrames = getDataWidth();
        	final int numBins = getDataHeight();
        	final double[] preemphasisFactor = new double[numBins];
        	final double[] dynamicFactor = new double[numFrames];
        	
        	
        	final double[][] dbData = new double[numFrames][];
        	
        	for(int ifreq = 0; ifreq < numBins; ifreq++) {
    			preemphasisFactor[ifreq] = (spectrogramSettings.getPreEmphasis() / NUMln2) * Math.log(ifreq * data.getDy() / 1000.0);
    			for(int itime = 0; itime < numFrames; itime++) {
    				if(dbData[itime] == null) {
    					dbData[itime] = new double[numBins];
    				}
    				double value = data.getZ(itime+1, ifreq+1);
    				value = (10.0/NUMln10) * Math.log((value + 1e-30) / 4.0e-10) + preemphasisFactor[ifreq];  // dB
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
    			dynamicFactor [itime] = spectrogramSettings.getDynamicCompression() * (maximum - dynamicFactor [itime]);
    			for (int ifreq = 0; ifreq < numBins; ifreq ++)
    				dbData [itime] [ifreq] += dynamicFactor [itime];
    		}
    		
    		double minIntensity = maximum - spectrogramSettings.getDynamicRange();
    		
    		final Dimension d = new Dimension(numFrames, numBins);
            setMinimumSize(d);
            setMaximumSize(d);        
            setPreferredSize(d);

            /* Create the image for displaying the data.
             */
            spectrogram = new BufferedImage(getDataWidth(),
                                            getDataHeight(),
                                            BufferedImage.TYPE_INT_RGB);
            
            double scaleFactor = (cmap.size() / dynamicRange);
            for (int i = 0; i < numFrames; i++) {                
                for (int j = numBins-1; j >= 0; j--) {
                	double dataVal = dbData[i][j];
                	if(dataVal < minIntensity)
                		dataVal = minIntensity;
                    int grey = (int)Math.round( (dataVal - minIntensity) * scaleFactor);
                    if(grey >= cmap.size())
                    	grey = cmap.size()-1;
                    spectrogram.setRGB(i, numBins - j - 1, cmap.getColor(grey));
                }
            }

            ImageFilter scaleFilter = 
                new ReplicateScaleFilter((int) (zoom * numFrames), (int)(vzoom*numBins));
            scaledSpectrogram = 
                createImage(new FilteredImageSource(spectrogram.getSource(),
                                                    scaleFilter));
            Dimension sz = getSize();
            repaint(0, 0, 0, sz.width - 1, sz.height - 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Zoom the image in the vertical direction, preparing for new
     * display.
     */
    protected void vzoomSet(float vzoom) {
        this.vzoom = vzoom;
        zoom();
    }

    /**
     * Zoom the image in the horizontal direction, preparing for new
     * display.
     */
    protected void hzoomSet(float zoom) {
        zoomSet(zoom);
    }

    /**
     * Zoom the image in the horizontal direction, preparing for new
     * display.
     */
    protected void zoomSet(float zoom) {
        this.zoom = zoom;
        zoom();
    }
    
    public void setZoom(float hzoom, float vzoom) {
    	if(hzoom <= 0.1 || vzoom <= 0.1) return;
    	this.zoom = hzoom;
    	this.vzoom = vzoom;
    	zoom();
    }
    
    public void setData(Spectrogram data) {
    	this.data = data;
    	computeSpectrogram();
    }

    private void zoom()
    {
        if (spectrogram != null) 
        {
            int width = spectrogram.getWidth();
            int height = spectrogram.getHeight();
            
            // do the zooming
            width = (int) (zoom * width);
            height = (int)(vzoom*height);

            ImageFilter scaleFilter = 
                new ReplicateScaleFilter(width, height);
            scaledSpectrogram = 
                createImage(new FilteredImageSource(spectrogram.getSource(),
                                                    scaleFilter));

            // so ScrollPane gets notified of the new size:
            setPreferredSize(new Dimension(width, height));
            revalidate();
            
            repaint();
        }
    }

    public float getVZoom()
    {
        return vzoom;
    }

    public float getHZoom()
    {
        return zoom;
    }
    
    public float getDynamicRange() {
		return dynamicRange;
	}

	public void setDynamicRange(float dynamicRange) {
		this.dynamicRange = dynamicRange;
		computeSpectrogram();
	}
	
	public void setColorMap(ColorMap cm) {
		this.cmap = cm;
	}
	
	public ColorMap getColorMap() {
		return this.cmap;
	}

//	public SpectrogramPanel getColorBar()
//    {
//        int barWidth = 20;
//        
//        double[][] cb = new double[barWidth][cmap.size];
//
//        for(int x = 0; x < cb.length; x++)
//            for(int y = 0; y < cb[x].length; y++) 
//                cb[x][y] = y;
//
//        return new SpectrogramPanel(cb);
//    }

    public double getData(int x, int y)
    {
        return data.getValueAtXY(
        		data.getX1() + x * data.getDx(),
        		data.getY1() + y * data.getDy());
    }
    
    public Spectrogram getData() {
    	return data;
    }

    public int getDataWidth()
    {
        return (int)data.getNx();
    }

    public int getDataHeight()
    {
        return (int)data.getNy();
    }

    /** 
     * Paint the component.  This will be called by AWT/Swing.
     * 
     * @param g The <code>Graphics</code> to draw on.
     */
    public void paint(Graphics g) {
	/**
	 * Fill in the whole image with white.
	 */
	Dimension sz = getSize();

	g.setColor(Color.WHITE);
	g.fillRect(0, 0, sz.width - 1, sz.height - 1);
    
	if(spectrogram != null) {
            g.drawImage(scaledSpectrogram, 0, 0, (ImageObserver) null);
        }
    }
    
}
