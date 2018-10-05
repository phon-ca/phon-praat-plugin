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
