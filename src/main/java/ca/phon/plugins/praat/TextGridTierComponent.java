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
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JComponent;
import javax.swing.event.MouseInputAdapter;

import ca.hedlund.jpraat.binding.fon.IntervalTier;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.phon.media.sampled.PCMSegmentView;
import ca.phon.media.wavdisplay.WavDisplay;
import ca.phon.ui.PhonGuiConstants;

/**
 * Display a text grid tier.
 *
 */
public class TextGridTierComponent extends JComponent {

	private static final long serialVersionUID = -6771204663446541476L;

	private IntervalTier tier;
	
	private final PCMSegmentView segmentView;
	
	/**
	 * Constructor
	 */
	public TextGridTierComponent(IntervalTier tier, PCMSegmentView segmentView) {
		super();
		this.tier = tier;
		this.segmentView = segmentView;
		
		setFocusable(false);
		addMouseListener(selectionListener);
	}
	
	/**
	 * Get the interval at the give x-location 
	 * 
	 * @param x
	 * 
	 * @return interval
	 */
	public TextInterval intervalForLocation(int x) {
		double time = segmentView.viewToModel(x);
		long i = tier.timeToLowIndex(time);
//		for(int i = 1; i <= tier.numberOfIntervals(); i++) {
//			final TextGridInterval interval = tier.getIntervalAt(i);
//			
//			final double start = segmentView.modelToView(interval.getStart());
//			final double end = segmentView.modelToView(interval.getEnd());
//			
//			if(x >= start && x < end) {
//				return interval;
//			}
//		}
		return (i > 0 && i <= tier.numberOfIntervals() ? tier.interval(i) : null);
	}
	
	public IntervalTier getTier() {
		return this.tier;
	}
	
	private double getMinTime() {
		return tier.getXmin();
	}
	
	private double getMaxTime() {
		return tier.getXmax();
	}
	
	@Override
	public Dimension getPreferredSize() {
		final Dimension retVal= super.getPreferredSize();
		retVal.height = 50;
		return retVal;
	}
	
	@Override
	public void paintComponent(Graphics g) {
		g.setColor(Color.white);
		g.fillRect(0, 0, getWidth(), getHeight());
		g.setColor(segmentView.getExcludedColor());
		g.fillRect(0, 0, getWidth(), getHeight());
		
		// set rendering hints
		final Graphics2D g2 = (Graphics2D)g;
		g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		final int height = getHeight();
		double width = getWidth();
		
		final double segX1 = segmentView.modelToView(segmentView.getSegmentStart());
		final double segX2 = 
				segmentView.modelToView(segmentView.getSegmentStart()+segmentView.getSegmentLength());
		final Rectangle2D contentRect = new Rectangle2D.Double(
				segX1, 0, segX2-segX1, height);
		
		if((int)contentRect.getWidth() <= 0
				|| (int)contentRect.getHeight() <= 0) {
			return;
		}
		
		// draw text grid
		final FontMetrics fm = getFontMetrics(getFont());
		double currentX = contentRect.getX();
		
		for(long i = 1; i <= tier.numberOfIntervals(); i++) {
			final TextInterval interval = tier.interval(i);
			double intervalEnd = segmentView.modelToView((float)interval.getXmax());
			
			final Rectangle2D intervalRect = 
					new Rectangle2D.Double(currentX, 0.0, (intervalEnd - currentX), height);
			g2.setColor(Color.white);
			g2.fill(intervalRect);
			
			// draw text centered (if possible) inside interval rect
			g2.setFont(getFont());
			g2.setColor(getForeground());
			
			final String txt = interval.getText();
			final Rectangle2D txtRect = fm.getStringBounds(txt, g2);
			
			double fontX = 0;
			if(txtRect.getWidth() > intervalRect.getWidth()) {
				fontX = currentX;
			} else {
				fontX = (int)intervalRect.getX() + (int)Math.round(intervalRect.getWidth() / 2) - (int)Math.round(txtRect.getCenterX());
			}
			int fontY = (int)intervalRect.getY() + (int)((intervalRect.getHeight() / 2) - (int)Math.round(txtRect.getCenterY()));
			
			if(txt.equalsIgnoreCase("#")) {
				g2.setColor(Color.lightGray);
			}
			g2.drawString(txt, (int)fontX, (int)fontY);
			
			// draw end boundary
			g2.setColor(Color.lightGray);
			if(i < tier.numberOfIntervals()) {
				final Line2D line = new Line2D.Double(intervalEnd, 0.0, intervalEnd, height);
				g2.draw(line);
			}
			currentX = intervalEnd+1;
		}
		
		if(segmentView.hasSelection()) {
			double selX1 = segmentView.modelToView(segmentView.getSelectionStart());
			double selX2 = segmentView.modelToView(segmentView.getSelectionStart()+segmentView.getSelectionLength());
			final Rectangle2D selectionRect = new Rectangle2D.Double(selX1, contentRect.getY(),
					selX2-selX1, contentRect.getHeight());
			selectionRect.setRect(selectionRect.getX(), selectionRect.getY(), selectionRect.getWidth(), getHeight());
			g2.setColor(segmentView.getSelectionColor());
			g2.fill(selectionRect);
		}
		
		if(!isEnabled()) {
			final Color overlay = new Color(1.0f, 1.0f, 1.0f, 0.75f);
			g2.setColor(overlay);
			g2.fillRect(0, 0, (int)width, (int)height);
		}
	}
	
	/*
	 * Selection
	 */
	private TextInterval selectedInterval = null;
	
	private boolean selectionPainted = false;
	
	public final static String SELECTED_INTERVAL_PROP = "selected_interval";
	
	public boolean isSelectionPainted() {
		return this.selectionPainted;
	}
	
	public void setSelectionPainted(boolean selectionPainted) {
		this.selectionPainted = selectionPainted;
		repaint();
	}
	
	public TextInterval getSelectedInterval() {
		return this.selectedInterval;
	}
	
	/**
	 * Selection listener
	 */
	private final MouseAdapter selectionListener = new MouseInputAdapter() {

		@Override
		public void mousePressed(MouseEvent e) {
			final Point p = e.getPoint();
			final TextInterval interval = intervalForLocation(p.x);
			if(interval != null) {
				final TextInterval oldInterval = selectedInterval;
				selectedInterval = interval;
				firePropertyChange(SELECTED_INTERVAL_PROP, oldInterval, selectedInterval);
			}
		}
		
	};
}
