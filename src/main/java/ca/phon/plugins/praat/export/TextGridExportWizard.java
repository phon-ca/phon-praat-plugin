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
package ca.phon.plugins.praat.export;

import ca.phon.app.log.LogUtil;
import ca.phon.app.session.*;
import ca.phon.app.session.editor.*;
import ca.phon.media.MediaLocator;
import ca.phon.plugins.praat.*;
import ca.phon.project.Project;
import ca.phon.session.*;
import ca.phon.session.check.*;
import ca.phon.session.filter.RecordFilter;
import ca.phon.ui.*;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.nativedialogs.FileFilter;
import ca.phon.ui.nativedialogs.*;
import ca.phon.ui.toast.ToastFactory;
import ca.phon.ui.tristatecheckbox.TristateCheckBoxTreeModel;
import ca.phon.ui.tristatecheckbox.TristateCheckBoxTreeModel.CheckingMode;
import ca.phon.ui.wizard.*;
import ca.phon.worker.*;
import ca.phon.worker.PhonTask.TaskStatus;
import org.apache.commons.io.FilenameUtils;
import org.jdesktop.swingx.*;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;

public class TextGridExportWizard extends WizardFrame {

	private Session session;

	/*
	 * UI
	 */
	private HidablePanel msgPanel;
	
	private SessionSelector sessionSelector;

	private RecordFilterPanel recordFilterPanel;

	private ExportEntryCheckboxTree exportsTree;

	/*
	 * radio buttons for selection output location
	 */
	private ButtonGroup nameGroup;
	private JRadioButton withMediaButton;
	private JRadioButton projectLocationButton;
	private JRadioButton customNameButton;

	/*
	 * Wizard steps
	 */
	private WizardStep selectSessionStep;

	private WizardStep selectRecordsStep;

	private WizardStep exportOptionsStep;

	public TextGridExportWizard(Project project) {
		super("TextGrid Export");

		super.putExtension(Project.class, project);

		setupWizard();
		super.btnFinish.setText("Generate TextGrids");
	}


	public TextGridExportWizard(Project project, Session session) {
		super("TextGrid Export");

		super.putExtension(Project.class, project);
		super.putExtension(Session.class, session);
		this.session = session;

		setupWizard();
		selectRecordsStep.setPrevStep(-1);
		gotoStep(1);
		super.btnFinish.setText("Generate TextGrid");
	}

	public TextGridExportWizard(Project project, Session session, RecordFilter filter) {
		super("TextGrid Export");

		super.putExtension(Project.class, project);
		super.putExtension(Session.class, session);
		this.session = session;
		setOverrideRecordFilter(filter);

		setupWizard();
		selectRecordsStep.setPrevStep(-1);
		exportOptionsStep.setPrevStep(-1);
		gotoStep(2);
		super.btnFinish.setText("Generate TextGrids");
	}

	private void setupWizard() {
		selectSessionStep = setupSessionsStep();
		selectSessionStep.setPrevStep(-1);
		selectSessionStep.setNextStep(1);

		selectRecordsStep = setupRecordsStep();
		selectRecordsStep.setPrevStep(0);
		selectRecordsStep.setNextStep(2);

		exportOptionsStep = setupExportOptionsStep();
		exportOptionsStep.setPrevStep(1);
		exportOptionsStep.setNextStep(-1);

		addWizardStep(selectSessionStep);
		addWizardStep(selectRecordsStep);
		addWizardStep(exportOptionsStep);
	}

	public Project getProject() {
		return getExtension(Project.class);
	}

	private WizardStep setupSessionsStep() {
		final WizardStep retVal = new WizardStep();

		final DialogHeader header = new DialogHeader("Generate TextGrids", "Select a single session.");
		sessionSelector = new SessionSelector(getProject());
		((TristateCheckBoxTreeModel)sessionSelector.getModel()).setCheckingMode(CheckingMode.SINGLE);

		final JScrollPane sp = new JScrollPane(sessionSelector);

		retVal.setLayout(new BorderLayout());
		retVal.add(header, BorderLayout.NORTH);
		retVal.add(sp, BorderLayout.CENTER);

		return retVal;
	}

	private WizardStep setupRecordsStep() {
		final WizardStep retVal = new WizardStep();

		final DialogHeader header = new DialogHeader("Generate TextGrids", "Select records.");
		
		msgPanel = new HidablePanel(TextGridExportWizard.class.getName() + ".msgPanel");
		msgPanel.setVisible(false);
		
		final JPanel centerPanel = new JPanel(new BorderLayout());

		// add record filter panel
		recordFilterPanel = new RecordFilterPanel(getProject(), session);
		recordFilterPanel.setBorder(BorderFactory.createTitledBorder("Select records"));
		final JScrollPane scroller = new JScrollPane(recordFilterPanel);
		
		centerPanel.add(msgPanel, BorderLayout.NORTH);
		centerPanel.add(scroller, BorderLayout.CENTER);
		
		retVal.setLayout(new BorderLayout());
		retVal.add(header, BorderLayout.NORTH);
		retVal.add(centerPanel, BorderLayout.CENTER);
		
		return retVal;
	}

	private WizardStep setupExportOptionsStep() {
		final WizardStep retVal = new WizardStep();

		final DialogHeader header = new DialogHeader("Generate TextGrids", "Setup export options.");

		withMediaButton = new JRadioButton("Save TextGrid in folder with same name as media file");
		final String projectButtonText =
				(session.getMediaLocation() != null ? "Save in project folder with same name as media file"
						: "Save in project folder with same name as session");
		projectLocationButton = new JRadioButton(projectButtonText);
		customNameButton = new JRadioButton("Choose where to save TextGrid (save dialog will display after clicking 'Generate')");

		if(session.getMediaLocation() == null)
			projectLocationButton.setSelected(true);
		else
			withMediaButton.setSelected(true);

		nameGroup = new ButtonGroup();
		nameGroup.add(withMediaButton);
		nameGroup.add(projectLocationButton);
		nameGroup.add(customNameButton);

		final TextGridExporter exporter = new TextGridExporter();
		exportsTree = new ExportEntryCheckboxTree(session);
		exportsTree.setBorder(BorderFactory.createTitledBorder("Select tiers"));
		exportsTree.setChecked(exporter.getExports(getProject()));
		final JScrollPane exportsScroller = new JScrollPane(exportsTree);

		final JPanel topPanel = new JPanel();
		topPanel.setLayout(new VerticalLayout());
		topPanel.setBorder(BorderFactory.createTitledBorder("Options"));
		topPanel.add(withMediaButton);
		topPanel.add(projectLocationButton);
		topPanel.add(customNameButton);

		final JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BorderLayout());
		centerPanel.add(topPanel, BorderLayout.NORTH);
		centerPanel.add(exportsScroller, BorderLayout.CENTER);

		retVal.setLayout(new BorderLayout());
		retVal.add(header, BorderLayout.NORTH);
		retVal.add(centerPanel, BorderLayout.CENTER);

		return retVal;
	}

	private RecordFilter overrideRecordFilter = null;

	public RecordFilter getRecordFilter() {
		return (overrideRecordFilter != null ? overrideRecordFilter : recordFilterPanel.getRecordFilter());
	}

	public void setOverrideRecordFilter(RecordFilter filter) {
		this.overrideRecordFilter = filter;
	}

	public Session getSession() {
		Session retVal = this.session;

		if(retVal == null) {
			final List<SessionPath> selectedSessions = sessionSelector.getSelectedSessions();
			if(selectedSessions.size() > 0) {
				final SessionPath loc = selectedSessions.get(0);
				try {
					retVal = getProject().openSession(loc.getFolder(), loc.getSessionFile());
				} catch (IOException e) {
					LogUtil.warning(e);
				}
			}
		}

		return retVal;
	}

	@Override
	public void gotoStep(int stepIndex) {
		super.gotoStep(stepIndex);
		
		if(getCurrentStep() == selectRecordsStep) {
			// run range checks and report if issues are found
			final List<SessionCheck> checks = new ArrayList<>();
			checks.add(new CheckTranscripts());
			
			final SessionValidator validator = new SessionValidator(checks);
			validator.addValidationListener( (e) -> {
				final String title = "Session Check";
				String msg = "";
				if(e.getMessage().startsWith("Segment overlaps")) {
					msg = "This session has overlapping records which may cause TextGrid generation to fail.";
				} else {
					// transcription errors
					msg = "This session has IPA errors which may cause TextGrid generation to fail.";
				}
				msgPanel.setTopLabelText(title);
				msgPanel.setBottomLabelText(msg);
				msgPanel.setVisible(true);
				
				selectRecordsStep.revalidate();
			});
			
			PhonWorker.getInstance().invokeLater( () -> validator.validate(session) );
		}
	}
	
	@Override
	public void next() {
		if(getCurrentStep() == selectRecordsStep) {
			if(exportsTree.getSession() == null) {
				exportsTree.setSession(getSession());
				exportsTree.setChecked((new TextGridExporter()).getExports(getProject()));
			}
		} else if(getCurrentStep() == selectSessionStep) {
			if(recordFilterPanel.getSession() == null) {
				final Session session = getSession();
				if(session == null) {
					ToastFactory.makeToast("Please select a single session").start(sessionSelector);
					return;
				}
				recordFilterPanel.setSession(getSession());
			}
		}
		super.next();
	}

	@Override
	public void finish() {
		final JPanel glassPane = new JPanel();
		glassPane.setLayout(null);
		glassPane.setOpaque(false);

		final Rectangle exportsRect = exportsTree.getBounds();

		final JXBusyLabel busyLabel = new JXBusyLabel(new Dimension(32, 32));

		final Point busyPoint = new Point( (exportsRect.x + exportsRect.width) - 42, 10);
		busyLabel.setLocation(busyPoint);
		glassPane.add(busyLabel);

		final PhonTaskListener busyListener = new PhonTaskListener() {

			@Override
			public void statusChanged(PhonTask task, TaskStatus oldstatus, TaskStatus status) {
				if(status == TaskStatus.RUNNING) {
					busyLabel.setBusy(true);
					glassPane.setVisible(true);
				} else if(status == TaskStatus.FINISHED) {
					busyLabel.setBusy(false);
					glassPane.setVisible(false);

					generateTask.removeTaskListener(this);
					TextGridExportWizard.super.finish();
				}
			}

			@Override
			public void propertyChanged(PhonTask arg0, String arg1, Object arg2,
					Object arg3) {
			}
		};
		generateTask.addTaskListener(busyListener);

		final PhonWorker worker = PhonWorker.getInstance();
		worker.invokeLater(generateTask);
	}

	final PhonTask generateTask = new PhonTask() {

		@Override
		public void performTask() {
			super.setStatus(TaskStatus.RUNNING);

			// look for window editing this session
			SessionEditor editor = null;
			for(CommonModuleFrame cmf:CommonModuleFrame.getOpenWindows()) {
				if(cmf instanceof SessionEditorWindow sessionEditorWindow && sessionEditorWindow.getSession() == session) {
					editor = sessionEditorWindow.getSessionEditor();
					break;
				}
			}

			final TextGridManager tgManager = new TextGridManager(getProject());
			final TextGridExporter exporter = new TextGridExporter();

			// determine location of TextGrid file
			File tgFile = null;
			final String defaultName =
					(session.getMediaLocation() != null ? FilenameUtils.getBaseName(session.getMediaLocation())
							: session.getName()).trim();
			if(!customNameButton.isSelected()) {

				if(withMediaButton.isSelected()) {
					final File mediaFile = MediaLocator.findMediaFile(getProject(), getSession());
					if(mediaFile == null) {
						super.err = new FileNotFoundException(session.getMediaLocation());
						setStatus(TaskStatus.ERROR);
						return;
					}

					tgFile = new File(mediaFile.getParentFile(), defaultName + TextGridManager.TEXTGRID_EXT);
				} else if(projectLocationButton.isSelected()) {
					final File textGridFolder = new File(tgManager.textGridFolder(session.getCorpus(), session.getName()));
					tgFile = new File(textGridFolder, defaultName + TextGridManager.TEXTGRID_EXT);
				}
			} else {
				final File sessionTgFolder = new File(tgManager.textGridFolder(session.getCorpus(), session.getName()));
				if(!sessionTgFolder.exists()) {
					sessionTgFolder.mkdirs();
				}
				// show save-as dialog
				final SaveDialogProperties props = new SaveDialogProperties();
				props.setParentWindow(TextGridExportWizard.this);
				props.setCanCreateDirectories(true);
				props.setInitialFolder(sessionTgFolder.getAbsolutePath());
				props.setInitialFile(defaultName);
				props.setFileFilter(  new FileFilter("TextGrids", TextGridManager.TEXTGRID_EXT.substring(1)) );
				props.setRunAsync(false);

				final String selectedPath = NativeDialogs.showSaveDialog(props);
				if(selectedPath != null) {
					tgFile = new File(selectedPath);
				} else {
					super.setStatus(TaskStatus.TERMINATED);
					return;
				}
			}

			if(tgFile == null) {
				super.err = new FileNotFoundException();
				super.setStatus(TaskStatus.ERROR);
				return;
			}

			// if exists, ask to overwrite or append tiers
			boolean appendTiers = false;
			if(tgFile.exists()) {
				final MessageDialogProperties props = new MessageDialogProperties();
				props.setParentWindow(TextGridExportWizard.this);
				props.setTitle("Overwrite or Append Tiers");
				props.setHeader("Overwrite or Append Tiers?");
				props.setMessage("A TextGrid already exists at " + tgFile.getAbsolutePath() + ". Overwrite data or "
						+ "append tiers to the existing TextGrid?");

				final String[] opts = new String[] { "Overwrite", "Append Tiers", "Cancel" };
				props.setOptions(opts);
				props.setRunAsync(false);

				int selectedOption = NativeDialogs.showMessageDialog(props);
				if(selectedOption == 2) {
					setStatus(TaskStatus.TERMINATED);
					return;
				}
				appendTiers = (selectedOption == 1);
			}

			try {
				exporter.generateTextGrid(getProject(), getSession(), getRecordFilter(), exportsTree.getSelectedExports(),
						tgFile, appendTiers);
				if(editor != null) {
					final EditorEvent<File> ee = new EditorEvent<>(TextGridSpeechAnalysisTier.TextGridChanged,
							TextGridExportWizard.this, tgFile);
					editor.getEventManager().queueEvent(ee);
				}
				super.setStatus(TaskStatus.FINISHED);
			} catch (Exception e) {
				LogUtil.warning(e);
				ToastFactory.makeToast(e.getLocalizedMessage()).start(btnFinish);
			}
		}

	};
}
