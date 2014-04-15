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


/**
 * Creates a graphical representation from a matrix.  Like Matlab's
 * imagesc.
 */
public class SpectrogramPanel extends JPanel {
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
    private double[][] data;

    private int width;
    private int height;

    private ColorMap cmap = ColorMap.getGreyscale(64);
    //private static float minZoom = .1f;
    
    private double maxVal; 

    /**
     * Creates a new SpectrogramPanel for the given data matrix
     */
    public SpectrogramPanel(double[][] dat) {
        data = dat;
        width = dat.length;
        height = dat[0].length;
        computeSpectrogram();
    }

    /**
     * Actually creates the Spectrogram image.
     */
    private void computeSpectrogram() {
        try {
            // prepare the data:
            maxVal = 0;
            for(int x = 0; x < width; x++)
            {
                for(int y = 0; y < height; y++)
                {
                	double val = data[x][y];
                	
                    if (val > maxVal)
                        maxVal = val;
                }
            }
            double minIntensity = maxVal - dynamicRange;
            
            int maxYIndex = height - 1;
            Dimension d = new Dimension((int)(width * getHZoom()), (int)( height * getVZoom()));
        
            setMinimumSize(d);
            setMaximumSize(d);        
            setPreferredSize(d);

            /* Create the image for displaying the data.
             */
            spectrogram = new BufferedImage(width,
                                            height,
                                            BufferedImage.TYPE_INT_RGB);
            
            double scaleFactor = (cmap.size() / dynamicRange);
            for (int i = 0; i < width; i++) {                
                for (int j = maxYIndex; j >= 0; j--) {
                	double dataVal = data[i][j];
                	if(dataVal < minIntensity)
                		dataVal = minIntensity;
                    int grey = (int)Math.round( (dataVal - minIntensity) * scaleFactor);
                    
                    spectrogram.setRGB(i, maxYIndex - j, cmap.getColor(grey));
                }
            }

            ImageFilter scaleFilter = 
                new ReplicateScaleFilter((int) (zoom * width), (int)(vzoom*height));
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
    
    public void setData(double[][] data) {
    	this.data = data;
    	width = data.length;
    	height = data[0].length;
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
    
    public SpectrogramPanel getColorBar()
    {
        int barWidth = 20;
        
        double[][] cb = new double[barWidth][cmap.size];

        for(int x = 0; x < cb.length; x++)
            for(int y = 0; y < cb[x].length; y++) 
                cb[x][y] = y;

        return new SpectrogramPanel(cb);
    }

    public double getData(int x, int y)
    {
        return data[x][y];
    }
    
    public double[][] getData() {
    	return data;
    }

    public int getDataWidth()
    {
        return width;
    }

    public int getDataHeight()
    {
        return height;
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
