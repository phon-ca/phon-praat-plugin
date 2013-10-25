package ca.phon.plugins.praat;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

import javax.swing.JComponent;
import javax.swing.event.MouseInputAdapter;

import ca.phon.gui.PhonGuiConstants;
import ca.phon.gui.action.PhonUIAction;
import ca.phon.gui.recordeditor.SegmentPanelCalculator;
import ca.phon.textgrid.TextGridInterval;
import ca.phon.textgrid.TextGridTier;

/**
 * Display a text grid tier.
 *
 */
public class TextGridTierComponent extends JComponent {

	private static final long serialVersionUID = -6771204663446541476L;

	private TextGridTier tier;
	
	private SegmentPanelCalculator calculator;
	
	/**
	 * Constructor
	 */
	public TextGridTierComponent(TextGridTier tier, SegmentPanelCalculator calculator) {
		super();
		this.tier = tier;
		this.calculator = calculator;
		
		setFocusable(false);
		addMouseListener(selectionListener);
	}
	
	/**
	 * Convert a time value (in seconds) to an x-value.
	 * 
	 * @param time
	 * @return x-value for the given time
	 */
	private int locationForTime(float time) {
		final Rectangle2D contentRect = calculator.getSegmentRect();
		final float length = (getMaxTime() - getMinTime());
		final float sPerPixel = length / (float)contentRect.getWidth();
		
		return (int)Math.round(contentRect.getX()) + (int)Math.round((time - getMinTime()) / sPerPixel);
	}
	
	/**
	 * Get the interval at the give x-location 
	 * 
	 * @param x
	 * 
	 * @return interval
	 */
	public TextGridInterval intervalForLocation(int x) {
		for(int i = 0; i < tier.getNumberOfIntervals(); i++) {
			final TextGridInterval interval = tier.getIntervalAt(i);
			
			final int start = locationForTime(interval.getStart());
			final int end = locationForTime(interval.getEnd());
			
			if(x >= start && x < end) {
				return interval;
			}
		}
		return null;
	}
	
	private float getMinTime() {
		return tier.getIntervalAt(0).getStart();
	}
	
	private float getMaxTime() {
		return tier.getIntervalAt(tier.getNumberOfIntervals()-1).getEnd();
	}
	
	@Override
	public Dimension getPreferredSize() {
		final Dimension retVal= super.getPreferredSize();
		retVal.height = 50;
		return retVal;
	}
	
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		// set rendering hints
		final Graphics2D g2 = (Graphics2D)g;
		g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		final Rectangle2D contentRect = calculator.getSegmentRect();
		
		// draw text grid
		final FontMetrics fm = getFontMetrics(getFont());
		int height = (int)getHeight();
		int width = (int) getWidth();
		int currentX = (int)contentRect.getX();
		
		g2.setColor(Color.white);
		g2.fillRect(0, 0, width, height);
		
		for(int i = 0; i < tier.getNumberOfIntervals(); i++) {
			final TextGridInterval interval = tier.getIntervalAt(i);
			int intervalEnd = locationForTime(interval.getEnd());
			
			final Rectangle intervalRect = 
					new Rectangle(currentX, 0, (intervalEnd - currentX), height);
			
			// draw bg
			if(selectionPainted && selectedInterval == interval) {
				g2.setColor(PhonGuiConstants.PHON_SELECTED);
			} else {
				g2.setColor(Color.white);
			}
			g2.fillRect(intervalRect.x, intervalRect.y, intervalRect.width, intervalRect.height);
			
			// draw text centered (if possible) inside interval rect
			g2.setFont(getFont());
			g2.setColor(getForeground());
			
			final String txt = interval.getLabel();
			final Rectangle2D txtRect = fm.getStringBounds(txt, g2);
			
			int fontX = 0;
			if(txtRect.getWidth() > intervalRect.width) {
				fontX = currentX;
			} else {
				fontX = intervalRect.x + (int)Math.round(intervalRect.width / 2) - (int)Math.round(txtRect.getCenterX());
			}
			int fontY = intervalRect.y + ((intervalRect.height / 2) - (int)Math.round(txtRect.getCenterY()));
			
			if(txt.equalsIgnoreCase("#")) {
				g2.setColor(Color.lightGray);
			}
			g2.drawString(txt, fontX, fontY);
			
			// draw end boundary
			g2.setColor(Color.lightGray);
			if(i < tier.getNumberOfIntervals()-1)
				g2.drawLine(intervalEnd, 0, intervalEnd, height);
			currentX = intervalEnd+1;
		}
		
		// draw edges
		final Rectangle2D leftInsetRect = calculator.getLeftInsetRect();
		leftInsetRect.setRect(
				leftInsetRect.getX(), 0.0, leftInsetRect.getWidth(), height);
		final Rectangle2D rightInsetRect = calculator.getRightInsetRect();
		rightInsetRect.setRect(
				rightInsetRect.getX(), 0.0, rightInsetRect.getWidth(), height);

		g2.setColor(new Color(200, 200, 200, 100));
		g2.fill(leftInsetRect);
		g2.fill(rightInsetRect);
		
		if(super.hasFocus()) {
			g2.setStroke(new BasicStroke(2.0f));
			final Rectangle2D focusRect = new Rectangle2D.Double(
					leftInsetRect.getX(), 0.0, 
					leftInsetRect.getWidth() + contentRect.getWidth() + rightInsetRect.getWidth(), height);
			g2.draw(focusRect);
		}
	}
	
	/*
	 * Selection
	 */
	private TextGridInterval selectedInterval = null;
	
	private boolean selectionPainted = false;
	
	public final static String SELECTED_INTERVAL_PROP = "selected_interval";
	
	public boolean isSelectionPainted() {
		return this.selectionPainted;
	}
	
	public void setSelectionPainted(boolean selectionPainted) {
		this.selectionPainted = selectionPainted;
		repaint();
	}
	
	public TextGridInterval getSelectedInterval() {
		return this.selectedInterval;
	}
	
	/**
	 * Selection listener
	 */
	private final MouseAdapter selectionListener = new MouseInputAdapter() {

		@Override
		public void mousePressed(MouseEvent e) {
			final Point p = e.getPoint();
			final TextGridInterval interval = intervalForLocation(p.x);
			if(interval != null) {
				final TextGridInterval oldInterval = selectedInterval;
				selectedInterval = interval;
				firePropertyChange(SELECTED_INTERVAL_PROP, null, selectedInterval);
			}
		}
		
	};
}
