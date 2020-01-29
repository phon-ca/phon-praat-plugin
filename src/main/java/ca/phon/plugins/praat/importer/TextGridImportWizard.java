/*
 * Copyright (C) 2012-2018 Gregory Hedlund
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *    http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.phon.plugins.praat.importer;

import java.awt.BorderLayout;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;

import au.com.bytecode.opencsv.CSVWriter;
import ca.hedlund.jpraat.binding.fon.IntervalTier;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.app.log.BufferPanel;
import ca.phon.app.log.LogBuffer;
import ca.phon.app.modules.EntryPointArgs;
import ca.phon.app.session.editor.EditorEvent;
import ca.phon.app.session.editor.EditorEventType;
import ca.phon.app.session.editor.SessionEditor;
import ca.phon.app.session.editor.undo.AddRecordEdit;
import ca.phon.app.session.editor.undo.AddTierEdit;
import ca.phon.plugin.PluginEntryPointRunner;
import ca.phon.plugin.PluginException;
import ca.phon.plugins.praat.TextGridManager;
import ca.phon.project.Project;
import ca.phon.session.Record;
import ca.phon.session.Session;
import ca.phon.session.SessionFactory;
import ca.phon.session.SystemTierType;
import ca.phon.session.TierDescription;
import ca.phon.session.TierDescriptions;
import ca.phon.session.TierViewItem;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.wizard.WizardFrame;
import ca.phon.ui.wizard.WizardStep;
import ca.phon.worker.PhonTask;
import ca.phon.worker.PhonWorker;

public class TextGridImportWizard extends WizardFrame {

	private final static Logger LOGGER =
			Logger.getLogger(TextGridImportWizard.class.getName());

	private static final long serialVersionUID = -7074300145973546703L;

	private final SessionEditor editor;

	private final Project project;

	private final Session session;

	private final TextGridImportSettingsStep step1;

	private WizardStep step3;

	private BufferPanel console;

	public TextGridImportWizard(SessionEditor editor) {
		super("Import TextGrids");
		this.editor = editor;
		this.project = editor.getProject();
		this.session = editor.getSession();

		putExtension(Project.class, project);
		putExtension(Session.class, session);

		step1 = new TextGridImportSettingsStep(session, new TextGridManager(project));
		init();

		btnFinish.setVisible(false);
	}

	private void init() {
		addWizardStep(step1);
		step1.addPropertyChangeListener(EditorEventType.TIER_VIEW_CHANGED_EVT, e -> {
			final EditorEvent ee = new EditorEvent(EditorEventType.TIER_VIEW_CHANGED_EVT, step1);
			editor.getEventManager().queueEvent(ee);
		});

		step3 = new WizardStep();
		step3.setLayout(new BorderLayout());

		final DialogHeader header = new DialogHeader("Create Records", "Creating records from TextGrid data");
		step3.add(header, BorderLayout.NORTH);

		console = new BufferPanel("Create records from TextGrid");
		step3.add(console, BorderLayout.CENTER);
		addWizardStep(step3);

		step1.setNextStep(1);
		step3.setPrevStep(0);
	}

	@Override
	protected void next() {
		super.next();

		if(getCurrentStep() == step3) {
			// import text grids
			btnCancel.setEnabled(false);
			btnBack.setEnabled(false);
			
			PhonWorker.getInstance().invokeLater(generateTask);
		}
	}

	private final PhonTask generateTask = new PhonTask() {

		@Override
		public void performTask() {
			setStatus(TaskStatus.RUNNING);

			// create compound edit
			CompoundEdit cmpEdit = new CompoundEdit() {

				@Override
				public String getUndoPresentationName() {
					return "Undo create records from TextGrid";
				}

				@Override
				public String getRedoPresentationName() {
					return "Redo create record from TextGrid";
				}

			};

			final TextGridImporter importer = new TextGridImporter();
			final TierDescriptions descs = session.getUserTiers();

			// add user defined tiers to session
			final Map<String, TierDescription> tierMap = step1.getTierMap();
			for(TierDescription td:tierMap.values()) {
				if(td.getName() == null || td.getName().length() == 0) continue;
				if(!SystemTierType.isSystemTier(td.getName())) {
					boolean doImport = true;
					for(TierDescription tierDesc:descs) {
						if(tierDesc.getName().equals(td.getName())) {
							doImport = false;
							break;
						}
					}

					if(doImport) {
						final TierViewItem tvi = SessionFactory.newFactory().createTierViewItem(td.getName(), true);
						final AddTierEdit addTierEdit = new AddTierEdit(editor, td, tvi);
						addTierEdit.doIt();
						cmpEdit.addEdit(addTierEdit);
					}
				}
			}

			final TextGridManager tgManager = new TextGridManager(project);
			TextGrid textGrid = null;
			try ( CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(console.getLogBuffer().getStdOutStream(), "UTF-8")) ) {
				textGrid = TextGridManager.loadTextGrid(step1.getSelectedTextGrid());

				try {
					console.getLogBuffer().getStdOutStream().flush();
					console.getLogBuffer().getStdOutStream().write(
							new String(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.SHOW_BUSY).getBytes() );
					console.getLogBuffer().getStdOutStream().flush();
				} catch (IOException e) {}

				List<String> colNames = new ArrayList<>();
				colNames.add("Record #");
				colNames.add("Segment");
				// add any imported tiers
				for(String tgTier:tierMap.keySet()) {
					final TierDescription td = tierMap.get(tgTier);
					colNames.add(td.getName());
				}
				csvWriter.writeNext(colNames.toArray(new String[0]));
				csvWriter.flush();

				int rIdx = session.getRecordCount();

				final String tgTierName = step1.getSelectedTier();
				long tgTierIdx = 0;
				IntervalTier intervalTier = new IntervalTier();
				for(long tierIdx = 1; tierIdx <= textGrid.numberOfTiers(); tierIdx++) {
					try {
						intervalTier = textGrid.checkSpecifiedTierIsIntervalTier(tierIdx);
						if(intervalTier.getName().equals(tgTierName)) {
							tgTierIdx = tierIdx;
							break;
						}
					} catch (PraatException pe) {}
				}

				for(int i = 1; i <= intervalTier.numberOfIntervals(); i++) {
					TextInterval recordInterval = intervalTier.interval(i);

					if(recordInterval.getText().trim().length() == 0 &&
							step1.isIgnoreEmptyIntervals()) continue;

					TextGrid tg = textGrid.extractPart(recordInterval.getXmin(), recordInterval.getXmax(), true);
					// create a new record for each interval
					final Record newRecord =
							importer.createRecordFromTextGrid(session, tg, step1.getTierMap());

					List<String> rowData = new ArrayList<>();
					rowData.add( (++rIdx) + "");
					rowData.add(newRecord.getSegment().getGroup(0).toString());

					for(String tgTier:tierMap.keySet()) {
						final TierDescription td = tierMap.get(tgTier);
						rowData.add(newRecord.getTier(td.getName(), String.class).toString());
					}

					csvWriter.writeNext(rowData.toArray(new String[0]));
					csvWriter.flush();

					final AddRecordEdit addRecordEdit = new AddRecordEdit(editor, newRecord);
					addRecordEdit.setFireEvent(false);
					cmpEdit.addEdit(addRecordEdit);
					addRecordEdit.doIt();
				}
				setStatus(TaskStatus.FINISHED);
			} catch (IOException | PraatException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);

				try {
					console.getLogBuffer().getStdErrStream().write(e.getLocalizedMessage().getBytes());
					console.getLogBuffer().getStdErrStream().flush();
				} catch (IOException ex) {}

				setStatus(TaskStatus.ERROR);
			} finally {
				cmpEdit.addEdit(new AbstractUndoableEdit() {

					@Override
					public void undo() throws CannotUndoException {
						SwingUtilities.invokeLater(() -> {
							final EditorEvent sessionModifiedEvent = new EditorEvent(EditorEventType.RECORD_ADDED_EVT, this,
									editor.getSession().getRecord(0));
							editor.getEventManager().queueEvent(sessionModifiedEvent);
						});
					}

					@Override
					public void redo() throws CannotRedoException {
						SwingUtilities.invokeLater(() -> {
							final EditorEvent sessionModifiedEvent = new EditorEvent(EditorEventType.RECORD_ADDED_EVT, this,
									editor.getSession().getRecord(editor.getSession().getRecordCount()-1));
							editor.getEventManager().queueEvent(sessionModifiedEvent);
						});
					}

				});
				cmpEdit.end();
				editor.getUndoSupport().postEdit(cmpEdit);

				final EditorEvent sessionModifiedEvent = new EditorEvent(EditorEventType.RECORD_ADDED_EVT, this,
						editor.getSession().getRecord(editor.getSession().getRecordCount()-1));
				editor.getEventManager().queueEvent(sessionModifiedEvent);

				btnCancel.setEnabled(true);

				try {
					console.getLogBuffer().getStdOutStream().flush();
					console.getLogBuffer().getStdOutStream().write(
							new String(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.STOP_BUSY).getBytes() );
					console.getLogBuffer().getStdOutStream().flush();
					console.getLogBuffer().getStdOutStream().write(
							new String(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.SHOW_TABLE_CODE).getBytes() );
					console.getLogBuffer().getStdOutStream().flush();
					console.getLogBuffer().getStdOutStream().write(
							new String(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.PACK_TABLE_COLUMNS).getBytes() );
					console.getLogBuffer().getStdOutStream().flush();
				} catch (IOException e) {}
			}
		}

	};

	@Override
	public void finish() {
		if(session != null) {
			final EntryPointArgs epArgs = new EntryPointArgs();
			epArgs.put(EntryPointArgs.PROJECT_OBJECT, project);
			epArgs.put(EntryPointArgs.CORPUS_NAME, session.getCorpus());
			epArgs.put(EntryPointArgs.SESSION_OBJECT, session);

			try {
				PluginEntryPointRunner.executePlugin("SessionEditor", epArgs);
			} catch (PluginException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
		}
	}

}
