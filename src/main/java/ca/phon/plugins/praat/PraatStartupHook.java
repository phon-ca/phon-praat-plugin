package ca.phon.plugins.praat;

import java.util.logging.Logger;

import com.sun.jna.Native;

import ca.hedlund.jpraat.binding.Praat;
import ca.phon.app.hooks.PhonStartupHook;
import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;
import ca.phon.plugin.PluginException;

/**
 * Init Praat library on startup.
 *
 */
public class PraatStartupHook implements PhonStartupHook, IPluginExtensionPoint<PhonStartupHook> {

	private static final Logger LOGGER = Logger
			.getLogger(PraatStartupHook.class.getName());
	
	@Override
	public void startup() throws PluginException {
		LOGGER.info("Initializing Praat library");
		
		// try to prevent JVM crashing due to uncaught C++ exceptions
		Native.setProtected(true);
		
		Praat.INSTANCE.praat_lib_init();
	}

	@Override
	public Class<?> getExtensionType() {
		return PhonStartupHook.class;
	}

	@Override
	public IPluginExtensionFactory<PhonStartupHook> getFactory() {
		return factory;
	}
	
	private final IPluginExtensionFactory<PhonStartupHook> factory = new IPluginExtensionFactory<PhonStartupHook>() {
		
		@Override
		public PhonStartupHook createObject(Object... args) {
			return PraatStartupHook.this;
		}
		
	};

}
