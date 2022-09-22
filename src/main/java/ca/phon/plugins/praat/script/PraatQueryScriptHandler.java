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

import ca.phon.plugin.*;
import ca.phon.query.db.ScriptLibrary;
import ca.phon.query.script.*;
import ca.phon.util.resources.ClassLoaderHandler;

import java.io.IOException;
import java.net.URL;

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
