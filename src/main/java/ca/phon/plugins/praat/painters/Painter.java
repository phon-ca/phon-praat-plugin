package ca.phon.plugins.praat.painters;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

public interface Painter<T> {

	public void setValue(T obj);
	
	public T getValue();
	
	/**
	 * Paint the given object inside the bounds using the provided java 2D
	 * context.
	 * 
	 * @param g2d
	 * @param bounds
	 */
	public void paintInside(Graphics2D g2d, Rectangle2D bounds);
	
}
