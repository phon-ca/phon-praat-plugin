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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;

import org.jdesktop.swingx.JXTreeTable;

import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.phon.app.session.editor.DelegateEditorAction;
import ca.phon.app.session.editor.EditorAction;
import ca.phon.app.session.editor.EditorEvent;
import ca.phon.app.session.editor.EditorEventType;
import ca.phon.app.session.editor.EditorView;
import ca.phon.app.session.editor.SessionEditor;
import ca.phon.media.util.MediaLocator;
import ca.phon.plugins.praat.export.ExportEntryCheckboxTree;
import ca.phon.plugins.praat.export.TextGridExportEntry;
import ca.phon.plugins.praat.export.TextGridExportWizard;
import ca.phon.plugins.praat.export.TextGridExporter;
import ca.phon.plugins.praat.script.PraatScriptContext;
import ca.phon.plugins.praat.script.PraatScriptTcpHandler;
import ca.phon.plugins.praat.script.PraatScriptTcpServer;
import ca.phon.session.MediaSegment;
import ca.phon.session.Tier;
import ca.phon.ui.CommonModuleFrame;
import ca.phon.ui.action.PhonActionEvent;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.ui.decorations.DialogHeader;

/**
 * Display a table representing a textgrid for a record
 * and allow for Praat operations on the data.
 */
public class TextGridPanel extends EditorView {
	
	private static final Logger LOGGER = Logger
			.getLogger(TextGridPanel.class.getName());
	
	private static final long serialVersionUID = 2535987323765660243L;

	private final String VIEW_TITLE = "Text Grid";

	/** The table */
	private JXTreeTable tgTable;
	
	/* UI */
	private JToolBar toolbar;
	
	private JButton generateButton;
	
	private JButton openTgButton;
	
	private TextGridManager tgManager;

	/**
	 * Velocity templates
	 */
	private final static String OPEN_TEXTGRID_TEMPLATE = "ca/phon/plugins/praat/OpenTextGrid.vm";

	/**
	 * Constructor
	 */
	public TextGridPanel(SessionEditor editor) {
		super(editor);
		
		tgManager = new TextGridManager(editor.getProject());
		
		init();
		registerEvents();
	}

	private void init() {
		setLayout(new BorderLayout());

		toolbar = new JToolBar();
		
//		final PhonUIAction exportSettingsAct = 
//				new PhonUIAction(this, "onExportSettings");
//		exportSettingsAct.putValue(PhonUIAction.NAME, "Settings");
//		exportSettingsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Setup TextGrid defaults");
//		exportSettingsBtn = new JButton(exportSettingsAct);
//		toolbar.add(exportSettingsBtn);
//		
		openTgButton = new JButton("Open TextGrid");
		final PhonUIAction openTgAct = new PhonUIAction("Open TextGrid", this, "openTextGridAction");
		openTgButton.setAction(openTgAct);
		toolbar.add(openTgButton);
//
//		refreshBtn = new JButton("Refresh");
//		refreshAct = new PhonUIAction("Refresh", this, "refreshAction");
//		refreshBtn.setAction(refreshAct);
//		toolbar.add(refreshBtn);
		
		final PhonUIAction generateAct = new PhonUIAction(this, "generateTextGrids");
		generateAct.putValue(PhonUIAction.NAME, "Generate TextGrids");
		generateAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Generate TextGrids for session...");
		generateButton = new JButton(generateAct);
		toolbar.add(generateButton);

		add(toolbar, BorderLayout.NORTH);
		tgTable = new JXTreeTable();
		tgTable.setColumnControlVisible(true);
		tgTable.setRootVisible(false);
		tgTable.setSortable(false);
		JScrollPane scroller = new JScrollPane(tgTable);

		add(scroller, BorderLayout.CENTER);

	}
	
	private void updatePanel() {
		TextGrid tg = getTextGrid();
		if(tg == null) return;

		TextGridTreeTableModel tgModel =
				new TextGridTreeTableModel(tg);
		tgTable.setTreeTableModel(tgModel);
		tgTable.packAll();
//		tgViewer.setTextGrid(tg);
	}

	/*
	 * Get the TextGrid for the current record. Generate if not found.
	 */
	public TextGrid getTextGrid() {
		TextGrid retVal = readTextGrid();
		return retVal;
	}

	private TextGrid generateTextGrid() {
		final TextGridExporter tgExporter = new TextGridExporter();
		final TextGrid retVal = tgExporter.createEmptyTextGrid(getEditor().currentRecord());

		final SessionEditor model = getEditor();
		// create some default tiers
		tgExporter.setupTextGrid(model.getProject(), model.currentRecord(), retVal);
		
		// save text grid to file
		saveTextGrid(retVal);
		
		return retVal;
	}

	/**
	 * Look for the text grid in project resources.
	 */
	private TextGrid readTextGrid() {
		final SessionEditor model = getEditor();
		return tgManager.loadTextGrid(model.currentRecord().getUuid().toString());
	}

	private void saveTextGrid(TextGrid tg) {
		final SessionEditor model = getEditor();
		tgManager.saveTextGrid(tg, model.currentRecord().getUuid().toString());
	}

	/**
	 * Get the location of the audio file.
	 * 
	 */
	public File getAudioFile() {
		final SessionEditor model = getEditor();
		File selectedMedia = 
				MediaLocator.findMediaFile(model.getProject(), model.getSession());
		if(selectedMedia == null) return null;
		File audioFile = null;
		
		int lastDot = selectedMedia.getName().lastIndexOf('.');
		String mediaName = selectedMedia.getName();
		if(lastDot >= 0) {
			mediaName = mediaName.substring(0, lastDot);
		}
		if(!selectedMedia.isAbsolute()) selectedMedia = 
			MediaLocator.findMediaFile(model.getSession().getMediaLocation(), model.getProject(), model.getSession().getCorpus());
		
		if(selectedMedia != null) {
			File parentFile = selectedMedia.getParentFile();
			audioFile = new File(parentFile, mediaName + ".wav");
			
			if(!audioFile.exists()) {
				audioFile = null;
			}
		}
		return audioFile;
	}

	/** UI Actions */
	public void openTextGridAction() {
		File mediaFile =
				getAudioFile();
		if(mediaFile == null) return;

		final SessionEditor model = getEditor();
		final Tier<MediaSegment> segmentTier = model.currentRecord().getSegment();
		if(segmentTier.numberOfGroups() == 0) return;
		final MediaSegment media = segmentTier.getGroup(0);

		String tgPath = tgManager.textGridPath(model.currentRecord().getUuid().toString());

		final PraatScriptContext map = new PraatScriptContext();
		final PraatScriptTcpServer server = new PraatScriptTcpServer();
		server.setHandler(new PraatScriptTcpHandler() {
			
			@Override
			public void praatScriptFinished(String data) {
				System.out.println(data);
			}
			
		});
		map.put("replyToPhon", Boolean.TRUE);
		map.put("socket", server.getPort());
		map.put("soundFile", mediaFile.getAbsolutePath());
		map.put("tgFile", tgPath);
		map.put("interval", media);
		
//		final PraatScript ps = new PraatScript();
//		String script;
//		try {
//			script = ps.generateScript(map);
//			
//			String errVal = SendPraat.sendPraat(script);
//			if(errVal != null) {
//				// try to open praat
//				SendPraat.openPraat();
//				// wait
//				try {
//					Thread.sleep(1000);
//				} catch (InterruptedException e) {}
//				
//				// try again
//				SendPraat.sendPraat(script);
//			}
//		} catch (IOException e) {
//			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
//		}
	}

	public void refreshAction(PhonActionEvent pae) {
		updatePanel();
	}
	
	public void generateTextGrids() {
		final SessionEditor model = getEditor();
		final TextGridExportWizard wizard = new TextGridExportWizard(model.getProject(), model.getSession());
		wizard.pack();
		wizard.setLocationRelativeTo(this);
		wizard.setVisible(true);
	}
	
	public void onExportSettings() {
		final JDialog dialog = new JDialog(CommonModuleFrame.getCurrentFrame());
		dialog.setModal(true);
		
		dialog.setLayout(new BorderLayout());
		
		final DialogHeader header = new DialogHeader("TextGrid Settings", "Setup defaults for the TextGrid plug-in");
		dialog.add(header, BorderLayout.NORTH);
		
		final SessionEditor model = getEditor();
		final ExportEntryCheckboxTree checkboxTree = new ExportEntryCheckboxTree(model.getSession());
		final TextGridExporter tgExporter = new TextGridExporter();
		final List<TextGridExportEntry> exports = tgExporter.getExports(model.getProject());
		checkboxTree.setChecked(exports);
		final JScrollPane treeScroller = new JScrollPane(checkboxTree);
		dialog.add(treeScroller, BorderLayout.CENTER);
		
		final JButton okBtn = new JButton("Ok");
		okBtn.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dialog.setVisible(false);
			}
		});
		
		final JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		btnBar.add(okBtn);
		dialog.add(btnBar, BorderLayout.SOUTH);
		
		// show dialog...
		dialog.pack();
		dialog.setLocationRelativeTo(this);
		dialog.getRootPane().setDefaultButton(okBtn);
		dialog.setVisible(true);
		
		// ... get checked and save
		final List<TextGridExportEntry> selectedExports = checkboxTree.getSelectedExports();
		tgExporter.saveExports(selectedExports, model.getProject());
	}

	/**
	 * Request notifications of editor events
	 */
	private void registerEvents() {
		final EditorAction recordChangedAct = new DelegateEditorAction(this, "onRecordChanged");
		getEditor().getEventManager().registerActionForEvent(EditorEventType.RECORD_CHANGED_EVT, recordChangedAct);
	}

	public void onRecordChanged(EditorEvent evt) {
		// update on any change to the record
		updatePanel();
	}

	@Override
	public String getName() {
		return VIEW_TITLE;
	}

	@Override
	public ImageIcon getIcon() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public JMenu getMenu() {
		return null;
	}
	
}
