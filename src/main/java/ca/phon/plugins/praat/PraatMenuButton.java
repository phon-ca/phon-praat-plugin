package ca.phon.plugins.praat;

import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;

import ca.phon.app.session.editor.view.waveform.WaveformEditorView;
import ca.phon.app.session.editor.view.waveform.WaveformTier;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.util.icons.IconSize;
import ca.phon.util.icons.IconManager;

public class PraatMenuButton extends JButton {

	private static final long serialVersionUID = 1347432616845351664L;
	
	private final WaveformEditorView parent;
	
	public PraatMenuButton(WaveformEditorView parent) {
		super();
		
		this.parent = parent;
		
		final PhonUIAction act = new PhonUIAction(this, "showMenu");
		act.putValue(PhonUIAction.NAME, "Praat");
		act.putValue(PhonUIAction.SMALL_ICON, IconManager.getInstance().getIcon("apps/praat", IconSize.SMALL));
		setAction(act);
	}
	
	public void showMenu() {
		final JPopupMenu menu = getMenu();
		menu.show(this, 0, getHeight());
	}
	
	public JPopupMenu getMenu() {
		final JMenu menu = new JMenu();
		
		for(WaveformTier tier:parent.getPluginTiers()) {
			tier.addMenuItems(menu);
		}
		
		final JMenu praatMenu = (JMenu)menu.getItem(1);
		
		return praatMenu.getPopupMenu();
	}

}
