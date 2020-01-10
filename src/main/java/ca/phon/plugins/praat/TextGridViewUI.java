package ca.phon.plugins.praat;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;

import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;

import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.IntervalTier;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.binding.fon.TextPoint;
import ca.hedlund.jpraat.binding.fon.TextTier;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.app.log.LogUtil;
import ca.phon.app.media.TimeComponentUI;
import ca.phon.app.media.TimeUIModel.Interval;
import ca.phon.session.SystemTierType;
import ca.phon.session.TierDescription;
import ca.phon.ui.fonts.FontPreferences;
import ca.phon.util.Tuple;

public class TextGridViewUI extends TimeComponentUI {
		
	private final static Insets labelInsets = new Insets(5, 3, 5, 3);

	private TextGridView tgView;
	
	private JLabel label;
	
	// tuple is tier num and interval index
	private RTree<Tuple<Long, Long>, com.github.davidmoten.rtree.geometry.Rectangle> intervalTree;
	
	private RTree<String, com.github.davidmoten.rtree.geometry.Rectangle> messageTree;
	
	private RTree<Action, com.github.davidmoten.rtree.geometry.Rectangle> actionsTree;
	
	public TextGridViewUI() {
		super();
	}
		
	@Override
	public void installUI(JComponent c) {
		if(!(c instanceof TextGridView))
			throw new IllegalArgumentException("Invalid component");
		
		super.installUI(c);
		tgView = (TextGridView)super.getTimeComponent();
		
		tgView.addMouseListener(mouseListener);
	}

	@Override
	public void uninstallUI(JComponent c) {
		super.uninstallUI(c);
		
		tgView.removeMouseListener(mouseListener);
	}
	
	private JLabel getLabel() {
		if(label == null) {
			label = new JLabel();
			label.setDoubleBuffered(false);
		}
		if(tgView != null)
			label.setFont(tgView.getFont());
		return label;
	}
	
	public int getTierHeight() {
		JLabel lbl = getLabel();
		String oldTxt = lbl.getText();
		lbl.setText("WWWW");
		int txtHeight = lbl.getPreferredSize().height;
		lbl.setText(oldTxt);
		
		return labelInsets.top + labelInsets.bottom + txtHeight;
	}
	
	public int getTierLabelHeight() {
		JLabel lbl = getLabel();
		String oldTxt = lbl.getText();
		lbl.setText("WWWW");
		int retVal = lbl.getPreferredSize().height;
		lbl.setText(oldTxt);
		
		return retVal;
	}
	
	@Override
	public Dimension getPreferredSize(JComponent c) {
		int numTiers = tgView.getVisibleTierCount();
		int prefWidth = getTimeComponent().getTimeModel().getPreferredWidth();
		int prefHeight = numTiers * getTierLabelHeight() + numTiers * getTierHeight();
		
		return new Dimension(prefWidth, prefHeight);
	}

	@Override
	public void paint(Graphics g, JComponent c) {
		intervalTree = RTree.create();
		messageTree = RTree.create();
		
		Graphics2D g2d = (Graphics2D)g;
		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		if(tgView.isOpaque()) {
			g2d.setColor(tgView.getBackground());
			g2d.fillRect(0, 0, tgView.getWidth(), tgView.getHeight());
		}
		
		int tierHeight = getTierHeight();
		int tierLabelHeight = getTierLabelHeight();
		
		TextGrid tg = tgView.getTextGrid();
		if(tg != null) {
			int visibleTierIdx = 0;
			for(long tIdx = 1; tIdx <= tg.numberOfTiers(); tIdx++) {
				final Function tier = tg.tier(tIdx);
				if(!tgView.isTierVisible(tier.getName())) continue;
				
				int tierLabelY = visibleTierIdx * tierHeight + visibleTierIdx * tierLabelHeight;
				int tierY = tierLabelY + tierLabelHeight;
				// tier rect
				final Rectangle2D tierRect = new Rectangle2D.Double(
						0, tierY, tgView.getWidth(), tierHeight);
				visibleTierIdx++;
				
				try {
					final IntervalTier intervalTier = tg.checkSpecifiedTierIsIntervalTier(tIdx);
					paintTierLabel(intervalTier, g2d, tierLabelY);
					paintIntervalTier(tIdx, intervalTier, g2d, tierRect);
				} catch (PraatException pe) {
					try {
						final TextTier pointTier = tg.checkSpecifiedTierIsPointTier(tIdx);
						paintTierLabel(pointTier, g2d, tierLabelY);
						paintPointTier(pointTier, g2d, tierRect);
					} catch (PraatException pe1) {
						LogUtil.severe(pe1.getLocalizedMessage(), pe1);
					}
				}
			}
		}
		
		for(var interval:tgView.getTimeModel().getIntervals()) {
			paintInterval(g2d, interval, true);
		}
		
		for(var marker:tgView.getTimeModel().getMarkers()) {
			paintMarker(g2d, marker);
		}
	}

	public void paintTierLabel(Function tier, Graphics2D g2d, int y) {
		final String name = (tier.getName() != null ? tier.getName().toString() : "");
		int x = tgView.getVisibleRect().x;
		if(name.length() > 0) {
			// get bounding rectangle of tier name
			Rectangle2D tierNameBounds = g2d.getFontMetrics().getStringBounds(name, g2d);
			
			// create a rounded rectangle
			Rectangle2D labelRect = new Rectangle2D.Double(x, y, tierNameBounds.getWidth() + 20, 
					tierNameBounds.getHeight());
			final Color tierBgColor = new Color(255, 255, 0, 120);
			g2d.setColor(tierBgColor);
			g2d.fill(labelRect);
			g2d.setColor(Color.darkGray);
			g2d.draw(labelRect);
			
			g2d.setColor(Color.black);
			g2d.drawString(name, x+10,
					(float)(y + (float)tierNameBounds.getHeight() - g2d.getFontMetrics().getDescent()) );
		}
	}
	
	public void paintIntervalTier(long tierIndex, IntervalTier intervalTier, Graphics2D g2d, Rectangle2D bounds) {
		for(long i = 1; i <= intervalTier.numberOfIntervals(); i++) {
			final TextInterval interval = intervalTier.interval(i);
			
			double startX = tgView.xForTime((float)interval.getXmin());
			double endX = tgView.xForTime((float)interval.getXmax());
			
			Rectangle2D intervalRect = new Rectangle2D.Double(
					startX, bounds.getY(), endX - startX, bounds.getHeight());
			if(!intervalRect.intersects(tgView.getVisibleRect())) {
				if(startX < tgView.getVisibleRect().getMaxX()) {
					continue;
				} else {
					break;
				}
			}
			
			g2d.setColor(Color.DARK_GRAY);
			final Line2D startLine = new Line2D.Double(startX, bounds.getY(), startX, 
					bounds.getY() + bounds.getHeight());
			g2d.draw(startLine);
			
			final Line2D endLine = new Line2D.Double(endX, bounds.getY(),
					endX, bounds.getY() + bounds.getHeight());
			g2d.draw(endLine);
			
			final String labelText = interval.getText();
			JLabel lbl = getLabel();
			lbl.setText(labelText);
			
			lbl.setHorizontalAlignment(SwingConstants.CENTER);
			lbl.setHorizontalTextPosition(SwingConstants.CENTER);
			lbl.setVerticalTextPosition(SwingConstants.CENTER);
			
			if(startX + labelInsets.left < (endX - labelInsets.right)) {
				final Rectangle2D labelRect = new Rectangle2D.Double(
						startX + labelInsets.left, bounds.getY(), (endX - labelInsets.right) - startX, bounds.getHeight());
				SwingUtilities.paintComponent(g2d, lbl, tgView, labelRect.getBounds());
				
				if(labelRect.getWidth() < lbl.getPreferredSize().width) {
					messageTree = messageTree.add(labelText, Geometries.rectangle(labelRect.getX(), labelRect.getY(), labelRect.getMaxX(), labelRect.getMaxY()));
				}
				
				Tuple<Long, Long> intervalIndex = new Tuple<>(tierIndex, i);
				intervalTree = intervalTree.add(intervalIndex, Geometries.rectangle(intervalRect.getX(), intervalRect.getY(), intervalRect.getMaxX(), intervalRect.getMaxY()));
			} // otherwise too small an area
		}		
	}
	
	public void paintPointTier(TextTier textTier, Graphics2D g2d, Rectangle2D bounds) {
		double contentWidth = bounds.getWidth();
		double tgLen = textTier.getXmax() - textTier.getXmin();
		double pxPerSec = contentWidth / tgLen;
		double xoffset = textTier.getXmin();
		
		for(long i = 1; i <= textTier.numberOfPoints(); i++) {
			final TextPoint tp = textTier.point(i);
			
			double lineX = (tp.getNumber() - xoffset) * pxPerSec;
			
			g2d.setColor(Color.DARK_GRAY);
			final Line2D pointLine = new Line2D.Double(lineX, bounds.getY(), lineX,
					bounds.getY() + bounds.getHeight());
			g2d.draw(pointLine);
			
			final Rectangle2D textBounds = 
					g2d.getFontMetrics().getStringBounds(tp.getText(), g2d);
			
			float x = (float)(lineX - textBounds.getCenterX());
			float y = (float)((bounds.getY() + (bounds.getHeight() / 2.0)) - (textBounds.getHeight()/2.0));

			g2d.setColor(Color.white);
			textBounds.setRect(x, y, textBounds.getWidth(), textBounds.getHeight());
			g2d.fill(textBounds);
			
			g2d.setColor(Color.black);
			g2d.drawString(tp.getText(), x, (float)(textBounds.getY() + textBounds.getHeight() - g2d.getFontMetrics().getDescent()));
		}
	}
	
	private <T> Optional<T> hitTest(RTree<T, com.github.davidmoten.rtree.geometry.Rectangle> tree, com.github.davidmoten.rtree.geometry.Point p) {
		var entries = tree.search(p);
		List<Tuple<com.github.davidmoten.rtree.geometry.Rectangle, T>> tupleList = new ArrayList<>();
		entries
			.map( entry -> new Tuple<com.github.davidmoten.rtree.geometry.Rectangle, T>(entry.geometry(), entry.value()))
			.subscribe(tupleList::add);
		
		if(tupleList.size() > 0) {
			double dist = Double.MAX_VALUE;
			Tuple<com.github.davidmoten.rtree.geometry.Rectangle, T> currentTuple = null;
			for(var tuple:tupleList) {
				double d = p.distance(tuple.getObj1());
				
				if(d < dist) {
					dist = d;
					currentTuple = tuple;
				}
			}
			return Optional.of(currentTuple.getObj2());
		} else {
			return Optional.empty();
		}
	}
	
	private Optional<Tuple<Long, Long>> intervalHitTest(com.github.davidmoten.rtree.geometry.Point p) {
		return hitTest(intervalTree, p);
	}
	
	private final MouseInputAdapter mouseListener = new MouseInputAdapter() {

		@Override
		public void mouseClicked(MouseEvent e) {
			// TODO Auto-generated method stub
			super.mouseClicked(e);
		}

		@Override
		public void mousePressed(MouseEvent e) {
			if(e.getButton() == MouseEvent.BUTTON1
				 && tgView.getUI().getCurrentlyDraggedMarker() == null) {
				Optional<Tuple<Long, Long>> optionalInterval = intervalHitTest(Geometries.point(e.getX(), e.getY()));
				if(optionalInterval.isPresent()) {
					tgView.fireIntervalSelected(optionalInterval.get());
				}
			}
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			// TODO Auto-generated method stub
			super.mouseReleased(e);
		}
	
		
		
	};
}