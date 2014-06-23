package ca.phon.plugins.praat;
import java.awt.Window;
import java.io.ByteArrayOutputStream;
import java.util.logging.Logger;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import ca.phon.app.modules.EntryPointArgs;
import ca.phon.app.project.ProjectWindow;
import ca.phon.app.session.editor.SessionEditor;
import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;
import ca.phon.plugin.IPluginMenuFilter;
import ca.phon.plugin.PhonPlugin;
import ca.phon.plugin.PluginAction;


/**
 * Implementation of a menu filter which adds the commands
 * 'Run Praat script...' and 'Open TextGrid in Praat...'
 * 
 * to the 'Plugins' menu of the RecordEditor windows.
 */
@PhonPlugin(name="phon-textgrid-plugin",version="0.1")
public class PraatMenuFilterExtPt implements IPluginExtensionPoint<IPluginMenuFilter> {
	
	private static final Logger LOGGER = Logger
			.getLogger(PraatMenuFilterExtPt.class.getName());

	@Override
	public Class<IPluginMenuFilter> getExtensionType() {
		return IPluginMenuFilter.class;
	}

	@Override
	public IPluginExtensionFactory<IPluginMenuFilter> getFactory() {
		return new PraatMenuFilterExtPtFactory();
	}
	
	/**
	 * The filter
	 * 
	 */
	private class PraatMenuFilter implements IPluginMenuFilter {

		@Override
		public void filterWindowMenu(Window owner, JMenuBar menu) {
			// get the 'Plugins' menu
			JMenu pluginsMenu = null;
			for(int i = 0; i < menu.getMenuCount(); i++) {
				JMenu m = menu.getMenu(i);
				if(m != null && m.getText() != null && m.getText().equals("Plugins")) {
					pluginsMenu = m;
					break;
				}
			}
			
			if(pluginsMenu == null) {
				LOGGER.warning("[PraatMenuFilter] No plugins menu found, aborting.");
				return;
			}
			
			// add menu entries to menu
			if(pluginsMenu.getMenuComponentCount() > 0) {
				pluginsMenu.addSeparator();
			}
			
			if(owner instanceof SessionEditor) {
				final SessionEditor editor = (SessionEditor)owner;
				
				pluginsMenu.add("-- TextGrid --").setEnabled(false);
				
				final EntryPointArgs args = new EntryPointArgs();
				args.put("editor", editor);
				PluginAction genTgAct = new PluginAction("GenerateTextGrids");
				genTgAct.putArgs(args);
				genTgAct.putValue(PluginAction.NAME, "Generate TextGrids...");
				genTgAct.putValue(PluginAction.SHORT_DESCRIPTION, "Generate/Export TextGrids for Session");
				JMenuItem genTgItem = new JMenuItem(genTgAct);
				pluginsMenu.add(genTgItem);
			} else if (owner instanceof ProjectWindow) {
				final ProjectWindow pw = (ProjectWindow)owner;
				
				pluginsMenu.add("-- TextGrid --").setEnabled(false);
				
				PluginAction importTgAct = new PluginAction("ImportTextGrids");
				importTgAct.putArg("projectWindow", pw);
				importTgAct.putValue(PluginAction.NAME, "Import TextGrids...");
				importTgAct.putValue(PluginAction.SHORT_DESCRIPTION, "Import TextGrids into a Session");
				JMenuItem importTgItem = new JMenuItem(importTgAct);
				pluginsMenu.add(importTgItem);
				
				final EntryPointArgs exportArgs = new EntryPointArgs();
				exportArgs.put("projectWindow", pw);
				PluginAction exportTgAct = new PluginAction("GenerateTextGrids");
				exportTgAct.putArgs(exportArgs);
				exportTgAct.putValue(PluginAction.NAME, "Generate TextGrids...");
				exportTgAct.putValue(PluginAction.SHORT_DESCRIPTION, "Export TextGrids for a Session");
				JMenuItem exportTgItem = new JMenuItem(exportTgAct);
				pluginsMenu.add(exportTgItem);
			}
		}
	}
	
	/**
	 * The factory
	 */
	private class PraatMenuFilterExtPtFactory implements IPluginExtensionFactory<IPluginMenuFilter> {

		@Override
		public IPluginMenuFilter createObject(Object... args) {
			return new PraatMenuFilter();
		}
		
	}
}
