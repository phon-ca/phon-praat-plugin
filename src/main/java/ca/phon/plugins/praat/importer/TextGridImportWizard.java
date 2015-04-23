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
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JScrollPane;

import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.sys.Data;
import ca.hedlund.jpraat.binding.sys.MelderFile;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.app.modules.EntryPointArgs;
import ca.phon.plugin.PluginEntryPointRunner;
import ca.phon.plugin.PluginException;
import ca.phon.project.Project;
import ca.phon.session.Session;
import ca.phon.session.SessionFactory;
import ca.phon.session.SystemTierType;
import ca.phon.session.TierDescription;
import ca.phon.session.TierDescriptions;
import ca.phon.session.TierViewItem;
import ca.phon.ui.PhonLoggerConsole;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.wizard.WizardFrame;
import ca.phon.ui.wizard.WizardStep;
import ca.phon.worker.PhonTask;
import ca.phon.worker.PhonTask.TaskStatus;
import ca.phon.worker.PhonTaskListener;
import ca.phon.worker.PhonWorker;

public class TextGridImportWizard extends WizardFrame {
	
	private final static Logger LOGGER =
			Logger.getLogger(TextGridImportWizard.class.getName());

	private static final long serialVersionUID = -7074300145973546703L;
	
	private final Project project;

	private final TextGridImportSettingsStep step1;
	
	private final TextGridImportSessionStep step2;
	
	private WizardStep step3;
	
	private PhonLoggerConsole console;

	public TextGridImportWizard(Project project) {
		super("Import TextGrids");
		this.project = project;
		step1 = new TextGridImportSettingsStep();
		step2 = new TextGridImportSessionStep(project);
		init();
		
		btnFinish.setText("Open Session");
		btnFinish.setVisible(false);
	}

	private void init() {
		addWizardStep(step1);
		addWizardStep(step2);
		
		step3 = new WizardStep();
		step3.setLayout(new BorderLayout());
		
		final DialogHeader header = new DialogHeader("TextGrid Import", "Importing TextGrids, this may takes some time...");
		step3.add(header, BorderLayout.NORTH);
		
		console = new PhonLoggerConsole();
		console.addLogger(Logger.getLogger("ca.phon.plugins.praat.importer"));
		final JScrollPane scroller = new JScrollPane(console);
		step3.add(scroller, BorderLayout.CENTER);
		addWizardStep(step3);
		
		step1.setNextStep(1);
		step2.setPrevStep(0);
		step2.setNextStep(2);
		step3.setPrevStep(1);
	}
	
	@Override
	public void prev() {
		super.prev();
		if(getCurrentStep() == step2) {
			btnFinish.setVisible(false);
		}
	}

	@Override
	protected void next() {
		super.next();
		
		if(getCurrentStep() == step3) {
			// import text grids
			btnCancel.setEnabled(false);
			btnBack.setEnabled(false);
			generateTask.addTaskListener(new PhonTaskListener() {
				
				@Override
				public void statusChanged(PhonTask task, TaskStatus oldStatus,
						TaskStatus newStatus) {
					if(newStatus != TaskStatus.RUNNING) {
						btnCancel.setEnabled(true);
						btnBack.setEnabled(true);
					}
					if(newStatus == TaskStatus.FINISHED) {
						btnFinish.setVisible(true);
					}
				}
				
				@Override
				public void propertyChanged(PhonTask task, String property,
						Object oldValue, Object newValue) {
				}
			});
			PhonWorker.getInstance().invokeLater(generateTask);
		}
	}

	private Session session = null;
	
	private final PhonTask generateTask = new PhonTask() {

		@Override
		public void performTask() {
			setStatus(TaskStatus.RUNNING);
			
			final TextGridImporter importer = new TextGridImporter();
			
			session = step2.getSession();
			if(session == null) {
				LOGGER.log(Level.SEVERE, "Unable to open/create Session");
				setStatus(TaskStatus.ERROR);
				return;
			}
			
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
			
			final File selectedFolder = step1.getSelectedFolder();
			final FileFilter filter = new FileFilter() {
				
				@Override
				public boolean accept(File pathname) {
					return pathname.getName().endsWith(".TextGrid");
				}
				
			};
			for(File f:selectedFolder.listFiles(filter)) {
				LOGGER.log(Level.INFO, "Importing file " + f.getAbsolutePath());
				try {
					final TextGrid tg = Data.readFromFile(TextGrid.class, MelderFile.fromPath(f.getAbsolutePath()));
					importer.importTextGrid(project, session, tg, tierMap, step1.getMarkerMap());
				} catch (PraatException e) {
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
			
			try {
				final UUID writeLock = project.getSessionWriteLock(session);
				project.saveSession(session, writeLock);
				project.releaseSessionWriteLock(session, writeLock);
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}

			setStatus(TaskStatus.FINISHED);
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
