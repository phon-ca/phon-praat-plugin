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
package ca.phon.plugins.praat.importer;

import java.awt.BorderLayout;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.undo.CompoundEdit;

import au.com.bytecode.opencsv.CSVWriter;
import ca.hedlund.jpraat.TextGridUtils;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.app.log.BufferPanel;
import ca.phon.app.log.LogBuffer;
import ca.phon.app.modules.EntryPointArgs;
import ca.phon.app.session.editor.SessionEditor;
import ca.phon.app.session.editor.undo.AddRecordEdit;
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
				if(!SystemTierType.isSystemTier(td.getName())) {
					boolean doImport = true;
					for(TierDescription tierDesc:descs) {
						if(tierDesc.getName().equals(td.getName())) {
							doImport = false;
							break;
						}
					}
					
					if(doImport) {
						session.addUserTier(td);
						final TierViewItem tvi = SessionFactory.newFactory().createTierViewItem(td.getName(), true);
						final List<TierViewItem> newView = new ArrayList<TierViewItem>(session.getTierView());
						newView.add(tvi);
						session.setTierView(newView);
					}
				}
			}
			
			final TextGridManager tgManager = new TextGridManager(project);
			TextGrid textGrid = null;
			try ( CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(console.getLogBuffer().getStdOutStream(), "UTF-8")) ) {
				textGrid = tgManager.openTextGrid(session.getCorpus(), session.getName(), step1.getSelectedTextGrid());
				
				try {
					console.getLogBuffer().getStdOutStream().flush();
					console.getLogBuffer().getStdOutStream().write( 
							new String(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.SHOW_BUSY).getBytes() );
					console.getLogBuffer().getStdOutStream().flush();
				} catch (IOException e) {}
				
				// TODO
//				if(step1.isDeleteAllRecords()) {
//					MessageDialogProperties props = new MessageDialogProperties();
//					props.setParentWindow(TextGridImportWizard.this);
//					props.setRunAsync(false);
//					props.setOptions(MessageDialogProperties.okCancelOptions);
//					props.setTitle("Delete all records");
//					props.setHeader("Delete all records");
//					props.setMessage("This will delete all current record data, do you wish to continue?");
//					
//					int retVal = NativeDialogs.showMessageDialog(props);
//					if(retVal != 0) {
//						throw new IOException("User canceled record replacement");
//					}
//					
//					while(session.getRecordCount() > 0) {
//						DeleteRecordEdit edit = new DeleteRecordEdit(editor);
//						edit.doIt();
//						cmpEdit.addEdit(edit);
//					}
//				}
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
				final List<TextInterval> contiguousIntervals = 
						TextGridUtils.getContiguousIntervals(textGrid, step1.getSelectedTier(), step1.getThreshold());
				for(TextInterval recordInterval:contiguousIntervals) {
					TextGrid tg = textGrid.extractPart(recordInterval.getXmin(), recordInterval.getXmax(), 1);
					// create a new record for each interval
					final Record newRecord =
							importer.createRecordFromTextGrid(session, tg, step1.getTierMap(), step1.getMarkerMap());
					
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
				cmpEdit.end();
				editor.getUndoSupport().postEdit(cmpEdit);
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