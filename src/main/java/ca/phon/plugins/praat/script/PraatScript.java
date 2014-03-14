package ca.phon.plugins.praat.script;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import ca.phon.script.PhonScriptContext;

/**
 * Helper class for loading script templates
 * and filling in values.
 *
 */
public class PraatScript {
	
	private String scriptTemplate;

	/**
	 * Constructor
	 */
	public PraatScript() {
		super();
		scriptTemplate = new String();
	}
	
	public PraatScript(String script) {
		super();
		this.scriptTemplate = script;
	}
	
	public String getScriptTemplate() {
		return this.scriptTemplate;
	}
	
	public void setScriptTemplate(String script) {
		this.scriptTemplate = script;
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
	public String generateScript(PraatScriptContext ctx) 
		throws IOException {
		final VelocityEngine ve = new VelocityEngine();
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		
		// load template
		final Template t = ve.getTemplate(getScriptTemplate());
		final StringWriter sw = new StringWriter();
		
		t.merge(ctx.velocityContext(), sw);
		
		final String retVal = sw.toString();
		
		System.out.println(PraatScriptParams.extractParamSection(retVal));
		
		return sw.toString();
	}
	
}
