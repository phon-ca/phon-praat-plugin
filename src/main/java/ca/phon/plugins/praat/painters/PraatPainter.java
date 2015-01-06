package ca.phon.plugins.praat.painters;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import ca.phon.ui.painter.Painter;

public interface PraatPainter<T> extends Painter<T> {

	public void paintGarnish(T obj, Graphics2D g2d, Rectangle2D bounds, int location);
	
}
