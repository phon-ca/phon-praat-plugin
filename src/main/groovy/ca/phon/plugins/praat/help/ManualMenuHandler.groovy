package ca.phon.plugins.praat.help

import java.awt.Window;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import ca.phon.app.hooks.PhonStartupHook;
import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;
import ca.phon.plugin.IPluginMenuFilter;
import ca.phon.plugin.PhonPlugin;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.util.OpenFileLauncher;

/**
 * Adds menu entries for opening Phon pdf manuals.
 *
 */
class ManualMenuHandler implements IPluginMenuFilter {

	private final static APP_MANUAL = "META-INF/doc/pdf/plugin-doc.pdf";
	
	public void filterWindowMenu(Window owner, JMenuBar menuBar) {
		JMenu menu = null;
		for(int i = 0; i < menuBar.getMenuCount(); i++) {
			if(menuBar.getMenu(i).getText().equals("Help")) {
				menu = menuBar.getMenu(i);
				break;
			}
		}
		assert menu != null;
		
		PhonUIAction action = new PhonUIAction(this, "onShowManual", APP_MANUAL);
		action.putValue(PhonUIAction.NAME, "User Manual");
		JMenuItem manualItem = new JMenuItem(action);
		
		menu.add(manualItem, 0);
	}
	
	public void onShowAPI(String url) {
		OpenFileLauncher.openURL(new java.net.URL(url));
	}
	
	public void onShowManual(String manual) {
		File tempFile = extractTextFile(manual);
		assert tempFile != null;
		showPDF(tempFile);
	}

	private File extractTextFile(String source) 
		throws IOException {
		final ClassLoader cl = this.class.classLoader;
		
		final InputStream is = cl.getResourceAsStream(source);
		assert is != null;
		
		final File retVal = File.createTempFile("manual", ".pdf");
		retVal.mkdirs();
		
		final FileOutputStream fos = new FileOutputStream(retVal);
		final byte[] buffer = new byte[1024];
		int read = -1;
		while((read = is.read(buffer)) >= 0 ) {
			fos.write(buffer, 0, read);
		}
		fos.flush();
		fos.close();
		is.close();
	
		return retVal;
	}
		
	private void showPDF(File pdfFile) {
		OpenFileLauncher.openURL(pdfFile.toURI().toURL());
	}
}

/**
 * Phon extension point
 *
 */
@PhonPlugin
class ManualMenuHandlerExtPt implements IPluginExtensionPoint<IPluginMenuFilter> {
	
	def factory = new IPluginExtensionFactory<IPluginMenuFilter>() {
		@Override
		public IPluginMenuFilter createObject(Object... args) {
			return new ManualMenuHandler();
		}
	};

	public Class<?> getExtensionType() {
		return IPluginMenuFilter.class;
	}

	public IPluginExtensionFactory<IPluginMenuFilter> getFactory() {
		return factory;
	}
	
}
