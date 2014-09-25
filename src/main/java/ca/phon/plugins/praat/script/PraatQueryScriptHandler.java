package ca.phon.plugins.praat.script;

import java.io.IOException;
import java.net.URL;

import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;
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
