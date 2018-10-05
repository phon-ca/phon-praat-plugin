/*
 * Copyright (C) 2012-2018 Gregory Hedlund
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *    http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
