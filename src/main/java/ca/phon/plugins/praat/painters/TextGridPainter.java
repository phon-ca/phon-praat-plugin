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
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.IntervalTier;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.binding.fon.TextTier;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.ui.painter.BufferedPainter;

public class TextGridPainter extends BufferedPainter<TextGrid> {
	
	private final static Logger LOGGER = Logger.getLogger(TextGridPainter.class.getName());
	
	private List<String> hiddenTiers = new ArrayList<>();

	private boolean paintTierLabels = false;
	
	private double startTime = 0.0;
	
	private double endTime = 0.0;
	
	public TextGridPainter() {
		super();
		setResizeMode(ResizeMode.REPAINT_ON_RESIZE);
	}
	
	public boolean isHidden(String tierName) {
		return hiddenTiers.contains(tierName);
	}
	
	public void setHidden(String tierName) {
		hiddenTiers.add(tierName);
	}
	
	public boolean isPaintTierLabels() {
		return paintTierLabels;
	}

	public void setPaintTierLabels(boolean paintTierLabels) {
		this.paintTierLabels = paintTierLabels;
	}

	@Override
	protected void paintBuffer(TextGrid obj, Graphics2D g2d, Rectangle2D bounds) {
		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		double contentHeight = bounds.getHeight();
		double tierHeight = contentHeight / obj.numberOfTiers();
		
		g2d.setColor(Color.WHITE);
		g2d.fill(bounds);
		
		for(long tIdx = 1; tIdx <= obj.numberOfTiers(); tIdx++) {
			// tier rect
			final Rectangle2D tierRect = new Rectangle2D.Double(
					bounds.getX(), bounds.getY() + ((tIdx-1)*tierHeight),
					bounds.getWidth(), tierHeight);
			try {
				final IntervalTier intervalTier = obj.checkSpecifiedTierIsIntervalTier(tIdx);
				if(!isHidden(intervalTier.getName().toString())) 
					paintIntervalTier(intervalTier, g2d, tierRect);
			} catch (PraatException pe) {
				try {
					final TextTier pointTier = obj.checkSpecifiedTierIsPointTier(tIdx);
					if(!isHidden(pointTier.getName().toString()))
						paintPointTier(pointTier, g2d, tierRect);
				} catch (PraatException pe1) {
					LOGGER.log(Level.SEVERE, pe1.getLocalizedMessage(), pe1);
				}
			}
		}
	}
	
	public void paintTierLabel(Function tier, Graphics2D g2d, Rectangle2D bounds) {
		final String name = (tier.getName() != null ? tier.getName().toString() : "");
		if(name.length() > 0) {
			// get bounding rectangle of tier name
			Rectangle2D tierNameBounds = g2d.getFontMetrics().getStringBounds(name, g2d);
			
			// create a rounded rectangle
			Rectangle2D labelRect = new Rectangle2D.Double(bounds.getX(), bounds.getY(), tierNameBounds.getWidth() + 20, 
					tierNameBounds.getHeight());
			final Color tierBgColor = new Color(255, 255, 0, 120);
			g2d.setColor(tierBgColor);
			g2d.fill(labelRect);
			g2d.setColor(Color.darkGray);
			g2d.draw(labelRect);
			
			g2d.setColor(Color.black);
			g2d.drawString(name, (float)bounds.getX() + 10,
					(float)(bounds.getY() + (float)tierNameBounds.getHeight() - g2d.getFontMetrics().getDescent()) );
		}
	}
	
	public void paintIntervalTier(IntervalTier intervalTier, Graphics2D g2d, Rectangle2D bounds) {
		double contentWidth = bounds.getWidth();
		double tgLen = intervalTier.getXmax() - intervalTier.getXmin();
		double pxPerSec = contentWidth / tgLen;
		double xoffset = intervalTier.getXmin();
		
		for(long i = 1; i <= intervalTier.numberOfIntervals(); i++) {
			final TextInterval interval = intervalTier.interval(i);
			
			double startX = (interval.getXmin() - xoffset) * pxPerSec;
			double endX = (interval.getXmax() - xoffset) * pxPerSec;
			
			g2d.setColor(Color.DARK_GRAY);
			final Line2D startLine = new Line2D.Double(startX, bounds.getY(), startX, 
					bounds.getY() + bounds.getHeight());
			g2d.draw(startLine);
			
			final Line2D endLine = new Line2D.Double(endX, bounds.getY(),
					endX, bounds.getY() + bounds.getHeight());
			g2d.draw(endLine);
			
			final Rectangle2D labelRect = new Rectangle2D.Double(
					startX, bounds.getY(), endX - startX, bounds.getHeight());
			final String labelText = interval.getText();
			final Rectangle2D textBounds = 
					g2d.getFontMetrics().getStringBounds(labelText, g2d);
			
			g2d.setColor(Color.black);
			if(textBounds.getWidth() <= labelRect.getWidth()) {
				// center text
				double textX = labelRect.getCenterX() - textBounds.getCenterX();
				double textY = labelRect.getCenterY() - textBounds.getCenterY();
				g2d.drawString(labelText, (float)textX, (float)textY);
			}
		}
		
		if(paintTierLabels)
			paintTierLabel(intervalTier, g2d, bounds);
	}
	
	public void paintPointTier(TextTier textTier, Graphics2D g2d, Rectangle2D bounds) {
		
	}

}
