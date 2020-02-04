package ca.phon.plugins.praat;

import java.io.File;

import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.media.LongSound;
import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;
import ca.phon.plugin.PhonPlugin;
import ca.phon.plugin.Rank;

@PhonPlugin(name = "PraatLongSound")
@Rank(20)
public class PraatLongSoundExtPt implements IPluginExtensionPoint<LongSound> {

	@Override
	public Class<?> getExtensionType() {
		return LongSound.class;
	}

	@Override
	public IPluginExtensionFactory<LongSound> getFactory() {
		return (args) -> {
			try {
				return new PraatLongSound((File)args[0]);
			} catch (PraatException e) {
				throw new IllegalArgumentException(e);
			}
		};
	}
	
}
