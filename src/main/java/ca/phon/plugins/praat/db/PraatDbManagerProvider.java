package ca.phon.plugins.praat.db;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;

import ca.phon.extensions.Extension;
import ca.phon.extensions.ExtensionProvider;
import ca.phon.extensions.IExtendable;
import ca.phon.plugin.PhonPlugin;
import ca.phon.project.Project;

@PhonPlugin()
@Extension(Project.class)
public class PraatDbManagerProvider implements ExtensionProvider {

	@Override
	public void installExtension(IExtendable obj) {
		final Project project = (Project)obj;
		final PraatDbManager dbManager = new PraatDbManager(project);
		
		// ensure database exists
		if(!dbManager.databaseExists()) {
			final ODatabaseDocumentTx docDb = dbManager.createDatabase();
			docDb.close();
		}
		
		project.putExtension(PraatDbManager.class, dbManager);
	}

}
