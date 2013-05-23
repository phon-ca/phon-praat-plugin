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
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;

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
import ca.phon.gui.recordeditor.RecordEditorModel;
import ca.phon.gui.recordeditor.RecordEditorView;
import ca.phon.gui.recordeditor.SystemTierType;
import ca.phon.jsendpraat.SendPraat;
import ca.phon.media.util.MediaLocator;
import ca.phon.phone.Phone;
import ca.phon.plugins.praat.TextGridExporter.ExportType;
import ca.phon.plugins.praat.textgrid.TextGrid;
import ca.phon.plugins.praat.textgrid.TextGridInterval;
import ca.phon.plugins.praat.textgrid.TextGridReader;
import ca.phon.plugins.praat.textgrid.TextGridWriter;
import ca.phon.system.logger.PhonLogger;

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

	private JButton openTgBtn;
	private JButton refreshBtn;
	
	private JButton exportSettingsBtn;

	/* Actions */
	private Action openTgAct;
	private Action refreshAct;

	/** Model
	 */
	private RecordEditorModel model;

	/**
	 * Constructor
	 */
	public TextGridPanel() {
		super();

//		this.model = model;

		init();
//		registerEvents();
	}

	private void init() {
		setLayout(new BorderLayout());

		toolbar = new JToolBar();
		
		final PhonUIAction exportSettingsAct = 
				new PhonUIAction(this, "onExportSettings");
		exportSettingsAct.putValue(PhonUIAction.NAME, "Settings");
		exportSettingsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Setup TextGrid defaults");
		exportSettingsBtn = new JButton(exportSettingsAct);
		toolbar.add(exportSettingsBtn);
		
		openTgBtn = new JButton("Open TextGrid");
		openTgAct = new PhonUIAction("Open TextGrid", this, "openTextGridAction");
		openTgBtn.setAction(openTgAct);
		toolbar.add(openTgBtn);

		refreshBtn = new JButton("Refresh");
		refreshAct = new PhonUIAction("Refresh", this, "refreshAction");
		refreshBtn.setAction(refreshAct);
		toolbar.add(refreshBtn);

		add(toolbar, BorderLayout.NORTH);

		tgTable = new JXTreeTable();
		tgTable.setColumnControlVisible(true);
		tgTable.setRootVisible(false);
		tgTable.setSortable(false);
		JScrollPane scroller = new JScrollPane(tgTable);

		add(scroller, BorderLayout.CENTER);

//		updatePanel();
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
//		tgTable.expandRow(0);
//		tgTable.expandAll();
	}

	/*
	 * Get the TextGrid for the current record. Generate if not found.
	 */
	public TextGrid getTextGrid() {
		TextGrid retVal = readTextGrid();

		if(retVal == null)
			retVal = generateTextGrid();

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
		final TextGridManager tgManager = new TextGridManager(model.getProject());
		return tgManager.loadTextGrid(model.getSession().getCorpus(), model.getSession().getID(), model.getRecord().getID());
	}

	private void saveTextGrid(TextGrid tg) {
		final TextGridManager tgManager = new TextGridManager(model.getProject());
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
	public void openTextGridAction(PhonActionEvent pae)
		throws IOException {
		File mediaFile =
				getAudioFile();
		if(mediaFile == null)
			throw new IOException("Audio file not found");


		String script =
			"Open long sound file... " + mediaFile.getAbsolutePath() + "\n";
		script += "segment = Extract part... " +
			(model.getRecord().getMedia().getStartValue()/1000.0f) + " " + (model.getRecord().getMedia().getEndValue()/1000.0f) + " yes\n";

//		File tgFile = writeTextGridTempFile();
		final TextGridManager tgManager = new TextGridManager(model.getProject());
		String tgPath = tgManager.textGridPath(model.getSession().getCorpus(), model.getSession().getID(), model.getRecord().getID());

		script += "tg = Read from file... " + tgPath + "\n";

		script += "select segment\n";
		script += "plus tg\n";
		script += "Edit\n";

		String errVal = SendPraat.sendPraat(script);
		if(errVal != null) {
			PhonLogger.severe(errVal);
			throw new IOException(errVal);
		}
	}

	public void refreshAction(PhonActionEvent pae) {
		updatePanel();
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
//		model.addListenerForEvent(RecordEditorModel.RECORD_CHANGED_EVT, this);
//		model.addListenerForEvent(RecordEditor.RECORD_REFRESH_EVT, this);
	}

//	public void editorEvent(RecordEditorEvent evt) {
//		// update on any change to the record
//		updatePanel();
//	}

	/**
	 * Get the formant listing script and replace
	 * requrired values for given interval.
	 */
	private String getFormantListingScript(TextGridInterval interval) {
		String retVal = null;
		// read formant script template
		try {
			BufferedReader in = new BufferedReader(
					new InputStreamReader(getClass().getResourceAsStream("FormantListing.praatscript")));
			String fullScript = new String();
			String line = null;
			while((line = in.readLine()) != null) {
				fullScript += line + "\n";
			}


//			File media
//			fullScript = fullScript.replaceAll("$SOUND_FILE");
		} catch (IOException e) {
			PhonLogger.severe(e.getMessage());
		}
		return null;
	}
}
