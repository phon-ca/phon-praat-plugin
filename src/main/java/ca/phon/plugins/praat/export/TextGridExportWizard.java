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
package ca.phon.plugins.praat.export;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import org.jdesktop.swingx.JXBusyLabel;
import org.jdesktop.swingx.VerticalLayout;

import ca.phon.app.session.RecordFilterPanel;
import ca.phon.app.session.SessionSelector;
import ca.phon.app.session.editor.EditorEvent;
import ca.phon.app.session.editor.SessionEditor;
import ca.phon.plugins.praat.TextGridManager;
import ca.phon.plugins.praat.TextGridViewer;
import ca.phon.project.Project;
import ca.phon.session.RecordFilter;
import ca.phon.session.Session;
import ca.phon.session.SessionPath;
import ca.phon.ui.CommonModuleFrame;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.toast.ToastFactory;
import ca.phon.ui.wizard.WizardFrame;
import ca.phon.ui.wizard.WizardStep;
import ca.phon.worker.PhonTask;
import ca.phon.worker.PhonTask.TaskStatus;
import ca.phon.worker.PhonTaskListener;
import ca.phon.worker.PhonWorker;

public class TextGridExportWizard extends WizardFrame {
	
	private static final Logger LOGGER = Logger
			.getLogger(TextGridExportWizard.class.getName());

	private static final long serialVersionUID = 4037035439180386352L;
	
	private Session session;
	
	/*
	 * UI
	 */
	private SessionSelector sessionSelector;
	
	private RecordFilterPanel recordFilterPanel;
	
	private JTextField nameField;
	
	private ExportEntryCheckboxTree exportsTree;
	
	/*
	 * radio buttons for selection output location
	 */
	private ButtonGroup nameGroup;
	private JRadioButton defaultNameButton;
	private JRadioButton customNameButton;
	
	/*
	 * overwrite/use existing TextGrids
	 */
	private JCheckBox overwriteBox;
	
	private final static String OVERWRITE_MESSAGE = "Keep existing data for records";
	
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
		super.btnFinish.setText("Generate TextGrids");
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
		sessionSelector.getCheckingModel().setCheckingMode(CheckingMode.SINGLE);
		
		final JScrollPane sp = new JScrollPane(sessionSelector);
		
		retVal.setLayout(new BorderLayout());
		retVal.add(header, BorderLayout.NORTH);
		retVal.add(sp, BorderLayout.CENTER);
		
		return retVal;
	}
	
	private WizardStep setupRecordsStep() {
		final WizardStep retVal = new WizardStep();
		
		final DialogHeader header = new DialogHeader("Generate TextGrids", "Select records.");
		
		// add record filter panel
		recordFilterPanel = new RecordFilterPanel(getProject(), session);
		recordFilterPanel.setBorder(BorderFactory.createTitledBorder("Select records"));
		final JScrollPane scroller = new JScrollPane(recordFilterPanel);
		
		retVal.setLayout(new BorderLayout());
		retVal.add(scroller, BorderLayout.CENTER);
		retVal.add(header, BorderLayout.NORTH);
		
		return retVal;
	}
	
	private WizardStep setupExportOptionsStep() {
		final WizardStep retVal = new WizardStep();
		
		final DialogHeader header = new DialogHeader("Generate TextGrids", "Setup export options.");
		
		overwriteBox = new JCheckBox();
		overwriteBox.setText(OVERWRITE_MESSAGE);
		
		nameField = new JTextField();
		nameField.setEnabled(false);
		
		defaultNameButton = new JRadioButton("Save as default TextGrid for session (__res/textgrids/.../default.TextGrid)");
		customNameButton = new JRadioButton("Save TextGrid with custom name");
		defaultNameButton.setSelected(true);

		defaultNameButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean state = defaultNameButton.isSelected();
				if(state) {
					nameField.setEnabled(false);
				}
			}
			
		});
		
		customNameButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				boolean state = customNameButton.isSelected();
				if(state) {
					nameField.setEnabled(true);
				}
			}
		});
		
		nameGroup = new ButtonGroup();
		nameGroup.add(defaultNameButton);
		nameGroup.add(customNameButton);
		
		final TextGridExporter exporter = new TextGridExporter();
		exportsTree = new ExportEntryCheckboxTree(session);
		exportsTree.setBorder(BorderFactory.createTitledBorder("Select tiers"));
		exportsTree.setChecked(exporter.getExports(getProject()));
		final JScrollPane exportsScroller = new JScrollPane(exportsTree);
		
		final JPanel topPanel = new JPanel();
		topPanel.setLayout(new VerticalLayout());
		topPanel.setBorder(BorderFactory.createTitledBorder("Options"));
		topPanel.add(defaultNameButton);
		topPanel.add(customNameButton);
		topPanel.add(nameField);
		topPanel.add(overwriteBox);
		
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
					retVal = getProject().openSession(loc.getCorpus(), loc.getSession());
				} catch (IOException e) {
					LOGGER
							.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
		}
		
		return retVal;
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
				} else {
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
				if(cmf instanceof SessionEditor && ((SessionEditor)cmf).getSession() == session) {
					editor = (SessionEditor)cmf;
					break;
				}
			}
			
			final TextGridExporter exporter = new TextGridExporter();
			String name = TextGridManager.DEFAULT_TEXTGRID_NAME;
			if(customNameButton.isSelected()) {
				name = nameField.getText();
			}
			name = name.trim();
			
			try {
				exporter.generateTextGrid(getProject(), getSession(), getRecordFilter(), exportsTree.getSelectedExports(), name,
						overwriteBox.isSelected());
				if(editor != null) {
					final EditorEvent ee = new EditorEvent(TextGridViewer.TEXT_GRID_CHANGED_EVENT, 
							this, name);
					editor.getEventManager().queueEvent(ee);
				}
				super.setStatus(TaskStatus.FINISHED);
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
				ToastFactory.makeToast(e.getLocalizedMessage()).start(btnFinish);
			}
		}
		
	};
}
