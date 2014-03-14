package ca.phon.plugins.praat.script;

import org.apache.velocity.VelocityContext;

/**
 * <p>This class is responsible for the following:
 *  <ul>
 *  <li>Providing the VelocityContext used to render script</li>
 *  <li>Setting up TCP listener (if necessary)</li>
 *  </ul>
 * </p>
 *  
 */
public class PraatScriptContext {

	/* context for velocity renderer */
	private final VelocityContext velocityContext;
	
	public PraatScriptContext() {
		this(new VelocityContext());
	}
	
	public PraatScriptContext(VelocityContext ctx) {
		super();
		this.velocityContext = ctx;
	}

	/*
	 * Delegates for velocity context
	 * 
	 */
	public boolean containsKey(Object key) {
		return velocityContext.containsKey(key);
	}

	public Object get(String key) {
		return velocityContext.get(key);
	}

	public Object[] getKeys() {
		return velocityContext.getKeys();
	}

	public Object put(String key, Object value) {
		return velocityContext.put(key, value);
	}
	
	VelocityContext velocityContext() {
		return this.velocityContext;
	}
}
