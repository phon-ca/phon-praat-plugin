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
	
	private final AtomicReference<Image> scaledImgRef = new AtomicReference<Image>();
	
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
	
	protected Image getScaledImage(Rectangle2D bounds) {
		boolean create = scaledImgRef.get() == null;
		Rectangle2D lastBounds = boundsRef.get();
		
		if(lastBounds == null || lastBounds.getWidth() != bounds.getWidth()
				|| lastBounds.getHeight() != bounds.getHeight()) {
			create = true;
		}
		
		if(create) {
			final BufferedImage img = getImage();
			if(img == null) return null;
			
			final ImageFilter filter = new ReplicateScaleFilter((int)bounds.getWidth(), (int)bounds.getHeight());
			final FilteredImageSource src = new FilteredImageSource(img.getSource(), filter);
			final Image scaledImg = Toolkit.getDefaultToolkit().createImage(src);
			setScaledImage(bounds, scaledImg);
		}
		
		return scaledImgRef.get();
	}
	
	protected void setScaledImage(Rectangle2D bounds, Image image) {
		boundsRef.set(bounds);
		scaledImgRef.set(image);
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
		}
		if(createImage) {
			img = createImage(bounds.getWidth(), bounds.getHeight());
			setImage(img);
		}
		// bail if still no image
		if(img == null) return;
		
		final Image scaledImg = getScaledImage(bounds);
		
		if(scaledImg != null) {
            g2d.drawImage(scaledImg, (int)bounds.getX(), (int)bounds.getY(), (ImageObserver) null);
        }
	}
	
}
