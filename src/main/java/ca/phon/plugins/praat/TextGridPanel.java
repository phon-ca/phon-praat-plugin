/*
 * Phon - An open source tool for research in phonology.
 * Copyright (C) 2008 The Phon Project, Memorial University <http://phon.ling.mun.ca>
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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.jdesktop.swingx.JXTreeTable;

import ca.phon.application.transcript.Form;
import ca.phon.application.transcript.IMedia;
import ca.phon.application.transcript.IPhoneticRep;
import ca.phon.application.transcript.IWord;
import ca.phon.application.transcript.MediaUnit;
import ca.phon.exceptions.ParserException;
import ca.phon.gui.CommonModuleFrame;
import ca.phon.gui.DialogHeader;
import ca.phon.gui.action.PhonActionEvent;
import ca.phon.gui.action.PhonUIAction;
import ca.phon.gui.recordeditor.DelegateEditorAction;
import ca.phon.gui.recordeditor.EditorAction;
import ca.phon.gui.recordeditor.EditorEvent;
import ca.phon.gui.recordeditor.EditorEventType;
import ca.phon.gui.recordeditor.RecordEditorModel;
import ca.phon.gui.recordeditor.RecordEditorView;
import ca.phon.gui.recordeditor.SystemTierType;
import ca.phon.jsendpraat.SendPraat;
import ca.phon.media.util.MediaLocator;
import ca.phon.phone.Phone;
import ca.phon.system.logger.PhonLogger;
import ca.phon.system.prefs.UserPrefManager;
import ca.phon.textgrid.TextGrid;
import ca.phon.textgrid.TextGridInterval;
import ca.phon.textgrid.TextGridReader;
import ca.phon.textgrid.TextGridWriter;

/**
 * Display a table representing a textgrid for a record
 * and allow for Praat operations on the data.
 */
public class TextGridPanel extends RecordEditorView {

	private final String VIEW_TITLE = "Text Grid";

	/** The table */
	private JXTreeTable tgTable;
	
	/* UI */
	private JToolBar toolbar;
	
	private JButton generateButton;
	
	private JButton openTgButton;

	/** Model
	 */
	private RecordEditorModel model;
	
	/**
	 * Velocity templates
	 */
	private final static String OPEN_TEXTGRID_TEMPLATE = "ca/phon/plugins/praat/OpenTextGrid.vm";

	/**
	 * Constructor
	 */
	public TextGridPanel() {
		super();
		
		init();
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

	@Override
	public void setModel(RecordEditorModel model) {
		this.model = model;
		registerEvents();
		updatePanel();
	}

	@Override
	public RecordEditorModel getModel() {
		return this.model;
	}

	@Override
	public String getTitle() {
		return VIEW_TITLE;
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
		final TextGrid retVal = tgExporter.createEmptyTextGrid(model.getRecord());

		// create some default tiers
		tgExporter.setupTextGrid(model.getProject(), model.getRecord(), retVal);
		
		// save text grid to file
		saveTextGrid(retVal);
		
		return retVal;
	}

	/**
	 * Look for the text grid in project resources.
	 */
	private TextGrid readTextGrid() {
		final TextGridManager tgManager = TextGridManager.getInstance(getModel().getProject());
		return tgManager.loadTextGrid(model.getSession().getCorpus(), model.getSession().getID(), model.getRecord().getID());
	}

	private void saveTextGrid(TextGrid tg) {
		final TextGridManager tgManager = TextGridManager.getInstance(getModel().getProject());
		tgManager.saveTextGrid(tg, model.getSession().getCorpus(), model.getSession().getID(), model.getRecord().getID());
	}

	/**
	 * Get the location of the audio file.
	 * 
	 */
	public File getAudioFile() {
		if(getModel() == null) return null;
		
		File selectedMedia = 
				MediaLocator.findMediaFile(getModel().getProject(), getModel().getSession());
		if(selectedMedia == null) return null;
		File audioFile = null;
		
		int lastDot = selectedMedia.getName().lastIndexOf('.');
		String mediaName = selectedMedia.getName();
		if(lastDot >= 0) {
			mediaName = mediaName.substring(0, lastDot);
		}
		if(!selectedMedia.isAbsolute()) selectedMedia = 
			MediaLocator.findMediaFile(getModel().getSession().getMediaLocation(), getModel().getProject(), getModel().getSession().getCorpus());
		
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

		final IMedia media = getModel().getRecord().getMedia();
		if(media == null) return;

		final TextGridManager tgManager = TextGridManager.getInstance(getModel().getProject());
		String tgPath = tgManager.textGridPath(model.getSession().getCorpus(), model.getSession().getID(), model.getRecord().getID());

		final Map<String, Object> map = new HashMap<String, Object>();
		map.put("soundFile", mediaFile.getAbsolutePath());
		map.put("tgFile", tgPath);
		map.put("interval", media);
		
		final PraatScript ps = new PraatScript();
		String script;
		try {
			script = ps.generateScript(OPEN_TEXTGRID_TEMPLATE, map);
			
			String errVal = SendPraat.sendPraat(script);
			if(errVal != null) {
				// try to open praat
				SendPraat.openPraat();
				// wait
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
				
				// try again
				SendPraat.sendPraat(script);
			}
		} catch (IOException e) {
			PhonLogger.severe(e.getMessage());
			e.printStackTrace();
		}
	}

	public void refreshAction(PhonActionEvent pae) {
		updatePanel();
	}
	
	public void generateTextGrids() {
		final TextGridExportWizard wizard = new TextGridExportWizard(getModel().getProject(), getModel().getSession());
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
		model.registerActionForEvent(EditorEventType.RECORD_CHANGED_EVT, recordChangedAct);
	}

	public void onRecordChanged(EditorEvent evt) {
		// update on any change to the record
		updatePanel();
	}
	
}
