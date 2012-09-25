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
package ca.phon.plugins.praat.textgrid;

import java.awt.BorderLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.JButton;
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
import ca.phon.gui.action.PhonActionEvent;
import ca.phon.gui.action.PhonUIAction;
import ca.phon.gui.recordeditor.RecordEditorModel;
import ca.phon.gui.recordeditor.RecordEditorView;
import ca.phon.media.util.MediaLocator;
import ca.phon.phone.Phone;
import ca.phon.system.logger.PhonLogger;
import ca.phon.util.SendPraat;

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
		tgTable.expandAll();
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
		TextGrid retVal = new TextGrid();

		if(model.getRecord().getMedia() != null) {
			IMedia media = model.getRecord().getMedia();

			float startTime = media.getStartValue();
			if(media.getUnitType() == MediaUnit.Millisecond)
				startTime /= 1000.0f;
			float endTime = media.getEndValue();
			if(media.getUnitType() == MediaUnit.Millisecond)
				endTime /= 1000.0f;
			retVal.setMin(startTime);
			retVal.setMax(endTime);

			float duration = endTime - startTime;

			List<Phone> allPhones = new ArrayList<Phone>();
			for(IWord w:model.getRecord().getWords()) {
				IPhoneticRep phoRep = w.getPhoneticRepresentation(Form.Actual);
				allPhones.addAll(phoRep.getSoundPhones());
			}

			float phoneDuration =
					duration / (float)allPhones.size();

			TextGridTier ipaTier = new TextGridTier("IPA", TextGridTierType.INTERVAL);

			float currentTime = startTime;
			for(Phone p:allPhones) {
				float startPhone = currentTime;
				float endPhone = currentTime + phoneDuration;

				TextGridInterval tgInterval =
						new TextGridInterval(p.getPhoneString(), startPhone, endPhone);
				ipaTier.addInterval(tgInterval);

				currentTime += phoneDuration;
			}

			retVal.addTier(ipaTier);

			// save text grid to file
			saveTextGrid(retVal);
		}

		return retVal;
	}

	/**
	 * Look for the text grid in project resources.
	 */
	private TextGrid readTextGrid() {
		TextGrid retVal = null;

		String tgPath = getTextGridPath();
		try {
			File tgFile = new File(tgPath);

			if(tgFile != null && tgFile.exists()) {
				TextGridReader tgReader =
						new TextGridReader(tgFile, "UTF-16");
				retVal = tgReader.readTextGrid();
			}
		} catch (ParserException pe) {
			PhonLogger.severe(TextGridPanel.class, pe.toString());
		}

		return retVal;
	}

	private void saveTextGrid(TextGrid tg) {
		File tgFile = new File(getTextGridPath());
		
		File tgParent = tgFile.getParentFile();
		if(!tgParent.exists()) {
			tgParent.mkdirs();
		}
		
		TextGridWriter tgWriter = new TextGridWriter(tg, tgFile, "UTF-16");
		tgWriter.writeTextGrid();
		tgWriter.close();
	}
	
	private String getTextGridPath() {
		String tgPath =
				model.getProject().getProjectLocation() + File.separator +
				"__res" + File.separator + "textgrids" + File.separator +
				model.getSession().getCorpus().replaceAll(" ", "_") + "_" +
				model.getSession().getID().replaceAll(" ", "_") + "_" +
				model.getRecord().getID() + ".TextGrid";
		return tgPath;
	}

	public File getAudioFile() {
//		File movFile = MediaLocator.getMediaFile(model.getProject(), model.getSession().getCorpus(),
//				model.getSession().getMediaLocation());
//		File audioFile = null;
//		if(movFile != null) {
//			if(movFile.getAbsolutePath().endsWith(".wav")) {
//				audioFile = movFile;
//			} else {
//				int lastDot = movFile.getName().lastIndexOf(".");
//				if(lastDot > 0) {
//					String audioFileName =
//						movFile.getName().substring(0, movFile.getName().lastIndexOf(".")) +
//							".wav";
//					audioFile = MediaLocator.getMediaFile(model.getProject(), model.getSession().getCorpus(),
//							audioFileName);
//				}
//			}
//		}
		File retVal = null;
		File movFile = MediaLocator.findMediaFile(model.getSession().getMediaLocation(), model.getProject());
		if(movFile != null) {
			if(movFile.getAbsolutePath().endsWith(".wav")) {
				retVal = movFile;
			} else {
				int lastDot = movFile.getName().lastIndexOf(".");
				if(lastDot > 0) {
					String audioFileName =
						movFile.getName().substring(0, movFile.getName().lastIndexOf(".")) +
							".wav";
					retVal = MediaLocator.findMediaFile(audioFileName, model.getProject());
				}
			}
		}
		return retVal;
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
		String tgPath = getTextGridPath();

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
					new InputStreamReader(new FileInputStream("data/praat/FormantListing.praatscript")));
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
