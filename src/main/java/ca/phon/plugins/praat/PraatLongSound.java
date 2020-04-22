package ca.phon.plugins.praat;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import ca.hedlund.jpraat.binding.sys.MelderFile;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.app.log.LogUtil;
import ca.phon.media.LongSound;
import ca.phon.media.Sound;
import ca.phon.media.sampled.Channel;

public class PraatLongSound extends LongSound  {
	
	private ca.hedlund.jpraat.binding.fon.LongSound internalLongSound;
	
	private int channelCount = -1;

	protected PraatLongSound(File file) throws PraatException {
		super(file);
		
		internalLongSound = 
				ca.hedlund.jpraat.binding.fon.LongSound.open(MelderFile.fromPath(file.getAbsolutePath()));
	}

	@Override
	public int numberOfChannels() {
		if(channelCount < 0) {
			try {
				if(internalLongSound.haveWindow(0.0, 1.0)) {
					var part = internalLongSound.extractPart(0.0, 1.0, true);
					channelCount = (int)part.getNy();
				}
			} catch (PraatException e) {
				LogUtil.severe(e);
			}
		}
		return channelCount;
	}

	@Override
	public float length() {
		return (float)internalLongSound.getXMax();
	}

	@Override
	public Sound extractPart(float startTime, float endTime) {
		try {
			var snd = internalLongSound.extractPart(startTime, endTime, true);
			return new PraatSound(snd);
		} catch (PraatException e) {
			LogUtil.severe(e);
		}
		return null;
	}
	
	private class PraatSound implements Sound {
		
		private ca.hedlund.jpraat.binding.fon.Sound internalSound;
		
		PraatSound(ca.hedlund.jpraat.binding.fon.Sound snd) {
			this.internalSound = snd;
		}

		@Override
		public int numberOfChannels() {
			return (int)internalSound.getNy();
		}

		@Override
		public float startTime() {
			return (float)internalSound.getXMin();
		}

		@Override
		public float endTime() {
			return (float)internalSound.getXMax();
		}

		@Override
		public float length() {
			return endTime() - startTime();
		}

		@Override
		public double[] getWindowExtrema(Channel channel, float startTime, float endTime) {
			double[] retVal = new double[2];
			
			AtomicReference<Long> iminRef = new AtomicReference<Long>();
			AtomicReference<Long> imaxRef = new AtomicReference<Long>();
			internalSound.getWindowSamples(startTime, endTime, iminRef, imaxRef);
			
			AtomicReference<Double> minRef = new AtomicReference<Double>();
			AtomicReference<Double> maxRef = new AtomicReference<Double>();
			internalSound.getWindowExtrema(iminRef.get(), imaxRef.get(), channel.channelNumber(), channel.channelNumber(), minRef, maxRef);
		
			retVal[0] = minRef.get();
			retVal[1] = maxRef.get();
			
			return retVal;
		}
		
	}
	
}
