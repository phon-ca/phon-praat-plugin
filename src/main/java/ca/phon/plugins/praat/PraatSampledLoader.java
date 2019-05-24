package ca.phon.plugins.praat;

import java.io.File;
import java.io.IOException;

import ca.hedlund.jpraat.binding.fon.Sound;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.media.sampled.Sampled;
import ca.phon.media.sampled.SampledLoader;
import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;
import ca.phon.plugin.PhonPlugin;
import ca.phon.plugin.Rank;

/**
 * Use praat API to load sampled objects.  Setting rank to 1 makes
 * this the default sampled loader for Phon.
 */
@PhonPlugin(name="phon-praat-plugin")
@Rank(1)
public class PraatSampledLoader implements SampledLoader, IPluginExtensionPoint<SampledLoader> {

	@Override
	public boolean canLoadFile(File file) {
		return true;
	}

	@Override
	public Sampled loadSampledFromFile(File file) throws IOException {
		try {
			return new PraatSampledBridge(Sound.readFromPath(file.getAbsolutePath()));
		} catch (PraatException e) {
			throw new IOException(e);
		}
	}

	@Override
	public Class<?> getExtensionType() {
		return SampledLoader.class;
	}

	@Override
	public IPluginExtensionFactory<SampledLoader> getFactory() {
		return (args) -> this;
	}

}
