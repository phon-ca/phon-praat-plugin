package ca.phon.plugins.praat;

import java.util.concurrent.atomic.AtomicReference;

import ca.phon.media.sampled.Sampled;

public class PraatSampledBridge implements Sampled {
	
	private ca.hedlund.jpraat.binding.fon.Sound sound;

	PraatSampledBridge(ca.hedlund.jpraat.binding.fon.Sound sound) {
		super();
		this.sound = sound;
	}

	@Override
	public int getNumberOfChannels() {
		return (int)sound.getNy();
	}

	@Override
	public long getNumberOfSamples() {
		return sound.getNx();
	}

	@Override
	public float getSampleRate() {
		return (float)(1.0 / sound.getDx());
	}

	@Override
	public int getSampleSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isSigned() {
		return true;
	}

	@Override
	public double valueForSample(int channel, long sample) {
		return sound.getValueAtXY(sample, channel);
	}

	@Override
	public long sampleForTime(float time) {
		return sound.xToNearestIndex(time);
	}

	@Override
	public double valueForTime(int channel, float time) {
		return valueForSample(channel, sampleForTime(time));
	}

	@Override
	public float getStartTime() {
		return (float)sound.getX1();
	}

	@Override
	public float getLength() {
		return (float)(sound.getDx() * sound.getNx());
	}

	@Override
	public double maximumValue(int channel, long firstSample, long lastSample) {
		return getWindowExtrema(channel, firstSample, lastSample)[1];
	}

	@Override
	public double maximumValue(int channel, float startTime, float endTime) {
		return getWindowExtrema(channel, startTime, endTime)[1];
	}

	@Override
	public double minimumValue(int channel, long firstSample, long lastSample) {
		return getWindowExtrema(channel, firstSample, lastSample)[0];
	}

	@Override
	public double[] getWindowExtrema(int channel, long firstSample, long lastSample) {
		double[] retVal = new double[2];
		getWindowExtrema(channel, firstSample, lastSample, retVal);
		return retVal;
	}

	@Override
	public void getWindowExtrema(int channel, long firstSample, long lastSample, double[] extrema) {
		AtomicReference<Double> minRef = new AtomicReference<Double>(0.0);
		AtomicReference<Double> maxRef = new AtomicReference<Double>(0.0);
		sound.getWindowExtrema(firstSample, lastSample, channel, channel, minRef, maxRef);
	
		extrema[0] = minRef.get();
		extrema[1] = maxRef.get();
	}

	@Override
	public double[] getWindowExtrema(int channel, float startTime, float endTime) {
		double[] retVal = new double[2];
		getWindowExtrema(channel, startTime, endTime, retVal);
		return retVal;
	}

	@Override
	public void getWindowExtrema(int channel, float startTime, float endTime, double[] extrema) {
		getWindowExtrema(channel, sampleForTime(startTime), sampleForTime(endTime), extrema);
	}

	@Override
	public byte[] getBytes(float startTime, float endTime) {
		// TODO Auto-generated method stub
		return null;
	}

}
