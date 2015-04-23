/*
 * phon-textgrid-plugin
 * Copyright (C) 2015, Gregory Hedlund <ghedlund@mun.ca>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ca.phon.plugins.praat.script;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;
import java.util.UUID;

import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;


/**
 * Helper class for loading script templates
 * and filling in values.
 *
 */
public class PraatScript {
	
	private String scriptTemplate;
	
	private String scriptText;

	/**
	 * Constructor
	 */
	public PraatScript() {
		super();
		scriptTemplate = new String();
	}
	
	public PraatScript(String scriptText) {
		super();
		this.scriptText = scriptText;
	}
	
	public PraatScript(String scriptText, String scriptTemplate) {
		super();
		this.scriptTemplate = scriptTemplate;
		this.scriptText = scriptText;
	}
	
	public String getScriptTemplate() {
		return this.scriptTemplate;
	}
	
	public void setScriptTemplate(String script) {
		this.scriptTemplate = script;
	}
	
	public String getScriptText() {
		return scriptText;
	}

	public void setScriptText(String scriptText) {
		this.scriptText = scriptText;
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
		final Properties p = new Properties();
		p.setProperty(RuntimeConstants.RESOURCE_LOADER, "string");
		p.setProperty("string.resource.loader.class", "org.apache.velocity.runtime.resource.loader.StringResourceLoader");
	
		final VelocityEngine ve = new VelocityEngine();
		ve.init(p);
		
		if(scriptTemplate == null || scriptTemplate.length() == 0) {
			// install string and set template name
			scriptTemplate = UUID.randomUUID().toString();
			StringResourceRepository repository = StringResourceLoader.getRepository();
			repository.putStringResource(scriptTemplate, scriptText);
		}
		
		// load template
		final Template t = ve.getTemplate(getScriptTemplate());
		final StringWriter sw = new StringWriter();
		
		t.merge(ctx.velocityContext(), sw);
		
		return sw.toString();
	}
	
}
