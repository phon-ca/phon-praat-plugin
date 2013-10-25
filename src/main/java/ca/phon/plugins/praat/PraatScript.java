package ca.phon.plugins.praat;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

/**
 * Helper class for loading script templates
 * and filling in values.
 *
 */
public class PraatScript {

	/**
	 * Velocity engine
	 */
	private final VelocityEngine ve;
	
	/**
	 * Constructor
	 */
	public PraatScript() {
		super();
		
		ve = new VelocityEngine();
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
	}
	
	/**
	 * Generate a script from the specified template
	 * using the given object map.
	 * 
	 * @param template
	 * @param objMap
	 * 
	 * @return script
	 * 
	 * @throws IOException
	 */
	public String generateScript(String template, Map<String, Object> objMap) 
		throws IOException {
		// setup context
		final VelocityContext ctx = new VelocityContext();
		for(String key:objMap.keySet()) {
			ctx.put(key, objMap.get(key));
		}
		
		// load template
		final Template t = ve.getTemplate(template);
		final StringWriter sw = new StringWriter();
		
		t.merge(ctx, sw);
		
		return sw.toString();
	}
	
}
