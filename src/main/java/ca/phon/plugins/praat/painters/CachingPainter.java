package ca.phon.plugins.praat.painters;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageObserver;
import java.awt.image.ReplicateScaleFilter;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.groovy.tools.shell.commands.SetCommand;

public abstract class CachingPainter<T> implements Painter<T> {
	
	private final AtomicReference<T> objRef = new AtomicReference<T>();
	
	private final AtomicReference<Image> imgRef = new AtomicReference<Image>();
	
	private AtomicReference<Rectangle2D> boundsRef = new AtomicReference<Rectangle2D>();
	
	@Override
	public void setValue(T obj) {
		objRef.getAndSet(obj);
		setImage(null, null);
	}

	@Override
	public T getValue() {
		return objRef.get();
	}
	
	protected Image getImage(Rectangle2D bounds) {
		boolean create = imgRef.get() == null;
		Rectangle2D lastBounds = boundsRef.get();
		
		if(lastBounds == null || lastBounds.getWidth() != bounds.getWidth()
				|| lastBounds.getHeight() != bounds.getHeight()) {
			create = true;
		}
		
		if(create) {
			final BufferedImage img = createImage(bounds.getWidth(), bounds.getHeight());
			setImage(bounds, img);
		}
		
		return imgRef.get();
	}

	protected void setImage(Rectangle2D bounds, Image img) {
		boundsRef.set(bounds);
		imgRef.getAndSet(img);
	}
	
	/**
	 * Create the buffered image.
	 * 
	 * @return image
	 */
	protected abstract BufferedImage createImage(double width, double height);

	@Override
	public void paintInside(Graphics2D g2d, Rectangle2D bounds) {
		Image img = getImage(bounds);
		
		if(img != null) {
            g2d.drawImage(img, (int)bounds.getX(), (int)bounds.getY(), (ImageObserver) null);
        }
	}
	
}
