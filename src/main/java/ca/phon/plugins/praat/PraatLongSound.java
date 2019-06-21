package ca.phon.plugins.praat;

import java.io.File;

import ca.phon.media.LongSound;
import ca.phon.media.Sound;
import ca.phon.media.sampled.Channel;

public class PraatLongSound extends LongSound {
	
	private ca.hedlund.jpraat.binding.fon.LongSound longSound;

	public PraatLongSound(File file) {
		super(file);
	}
	
	@Override
	public int numberOfChannels() {
		return this.longSound.
	}

	@Override
	public float length() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Sound extractPart(float startTime, float endTime) {
		// TODO Auto-generated method stub
		return null;
	}
	
	private class PraatSound implements Sound {

		@Override
		public int numberOfChannels() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public float startTime() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public float endTime() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public float length() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public double[] getWindowExtrema(Channel channel, float startTime, float endTime) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}

}
