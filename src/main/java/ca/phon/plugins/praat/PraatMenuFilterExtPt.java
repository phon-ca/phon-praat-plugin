package ca.phon.plugins.praat;
import java.awt.Window;
import java.io.ByteArrayOutputStream;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import ca.phon.system.logger.PhonLogger;
import ca.phon.system.plugin.IPluginExtensionFactory;
import ca.phon.system.plugin.IPluginExtensionPoint;
import ca.phon.system.plugin.IPluginMenuFilter;
import ca.phon.system.plugin.PhonPlugin;
import ca.phon.system.plugin.PluginAction;


/**
 * Implementation of a menu filter which adds the commands
 * 'Run Praat script...' and 'Open TextGrid in Praat...'
 * 
 * to the 'Plugins' menu of the RecordEditor windows.
 */
@PhonPlugin(name="phon-textgrid-plugin",version="0.1")
public class PraatMenuFilterExtPt implements IPluginExtensionPoint<IPluginMenuFilter> {

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
				PhonLogger.warning("[PraatMenuFilter] No plugins menu found, aborting.");
				return;
			}
			
			// add menu entries to menu
			if(pluginsMenu.getMenuComponentCount() > 0) {
				pluginsMenu.addSeparator();
			}
			
			PluginAction runScriptAct = new PluginAction("SendPraat");
			runScriptAct.putArg("script", "Create Sound from formula... sine Mono 0 1 44100 sin (2*pi*100*x)");
			runScriptAct.putArg("batchMode", false);
			runScriptAct.putArg("output", new ByteArrayOutputStream());
			runScriptAct.putValue(PluginAction.NAME, "Run Praat script...");
			runScriptAct.putValue(PluginAction.SHORT_DESCRIPTION, "Execute a script in praat");
			JMenuItem runScriptItem = new JMenuItem(runScriptAct);
			pluginsMenu.add(runScriptItem);
			
			
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
