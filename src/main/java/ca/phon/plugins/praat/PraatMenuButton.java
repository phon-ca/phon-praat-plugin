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
package ca.phon.plugins.praat;

import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;

import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisEditorView;
import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisTier;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.util.icons.IconManager;
import ca.phon.util.icons.IconSize;

public class PraatMenuButton extends JButton {

	private static final long serialVersionUID = 1347432616845351664L;
	
	private final SpeechAnalysisEditorView parent;
	
	public PraatMenuButton(SpeechAnalysisEditorView parent) {
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
		
		for(SpeechAnalysisTier tier:parent.getPluginTiers()) {
			tier.addMenuItems(menu);
		}
		
		final JMenu praatMenu = (JMenu)menu.getItem(1);
		
		return praatMenu.getPopupMenu();
	}

}
