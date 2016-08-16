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
import java.net.URL;

import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;
import ca.phon.query.db.ScriptLibrary;
import ca.phon.query.script.QueryName;
import ca.phon.query.script.QueryScript;
import ca.phon.query.script.QueryScriptHandler;
import ca.phon.util.resources.ClassLoaderHandler;

public class PraatQueryScriptHandler extends ClassLoaderHandler<QueryScript> implements QueryScriptHandler,
	IPluginExtensionPoint<QueryScriptHandler> {
	
	private final static String RESOURCE_FILE = "ca/phon/query/script/praat_query.list";
	
	public PraatQueryScriptHandler() {
		super();
		
		loadResourceFile(RESOURCE_FILE);
	}

	@Override
	public QueryScript loadFromURL(URL url) throws IOException {
		final QueryScript retVal = new QueryScript(url);
		final QueryName qn = retVal.getExtension(QueryName.class);
		if(qn != null) {
			qn.setCategory("Praat");
			qn.setScriptLibrary(ScriptLibrary.PLUGINS);
		}
		return retVal;
	}

	@Override
	public Class<?> getExtensionType() {
		return QueryScriptHandler.class;
	}

	@Override
	public IPluginExtensionFactory<QueryScriptHandler> getFactory() {
		return factory;
	}
	
	private final static IPluginExtensionFactory<QueryScriptHandler> factory 
		= new IPluginExtensionFactory<QueryScriptHandler>() {
		
		@Override
		public QueryScriptHandler createObject(Object... args) {
			return new PraatQueryScriptHandler();
		}
		
	};

}
