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
