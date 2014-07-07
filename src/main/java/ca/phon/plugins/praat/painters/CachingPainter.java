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

public abstract class CachingPainter<T> implements Painter<T> {
	
	private final AtomicReference<T> objRef = new AtomicReference<T>();
	
	private final AtomicReference<BufferedImage> imgRef = new AtomicReference<BufferedImage>();
	
	private AtomicReference<Rectangle2D> boundsRef = new AtomicReference<Rectangle2D>();
	
	@Override
	public void setValue(T obj) {
		objRef.getAndSet(obj);
		setImage(null);
	}

	@Override
	public T getValue() {
		return objRef.get();
	}
	
	protected BufferedImage getImage() {
		return imgRef.get();
	}

	protected void setImage(BufferedImage img) {
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
		BufferedImage img = getImage();
		boolean createImage = false;
		if(img == null) {
			createImage = true;
		} else {
			final Rectangle2D lastBounds = boundsRef.get();
			if(lastBounds.getWidth() != bounds.getWidth()
					|| lastBounds.getHeight() != bounds.getHeight()) {
				createImage = true;
			}
		}
		if(createImage) {
			img = createImage(bounds.getWidth(), bounds.getHeight());
			setImage(img);
			boundsRef.set(bounds);
		}
		// bail if still no image
		if(img == null) return;
		
		final ImageFilter filter = new ReplicateScaleFilter((int)bounds.getWidth(), (int)bounds.getHeight());
		final FilteredImageSource src = new FilteredImageSource(img.getSource(), filter);
		final Image scaledImg = Toolkit.getDefaultToolkit().createImage(src);
		
		if(scaledImg != null) {
            g2d.drawImage(scaledImg, (int)bounds.getX(), (int)bounds.getY(), (ImageObserver) null);
        }
	}
	
}
