package ca.phon.plugins.praat;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;

import org.jdesktop.swingx.JXBusyLabel;
import org.jdesktop.swingx.VerticalLayout;

import ca.phon.application.PhonTask;
import ca.phon.application.PhonTaskListener;
import ca.phon.application.PhonWorker;
import ca.phon.application.PhonTask.TaskStatus;
import ca.phon.application.project.IPhonProject;
import ca.phon.application.transcript.ITranscript;
import ca.phon.gui.DialogHeader;
import ca.phon.gui.components.FileSelectionField;
import ca.phon.gui.components.FileSelectionField.SelectionMode;
import ca.phon.gui.components.UtteranceFilterPanel;
import ca.phon.gui.wizard.WizardFrame;
import ca.phon.gui.wizard.WizardStep;

public class TextGridExportWizard extends WizardFrame {

	private static final long serialVersionUID = 4037035439180386352L;
	
	private final ITranscript session;
	
	/*
	 * UI
	 */
	private UtteranceFilterPanel recordFilterPanel;
	
	private FileSelectionField outputFolderField;
	
	private ExportEntryCheckboxTree exportsTree;
	
	/*
	 * radio buttons for selection output location
	 */
	private ButtonGroup exportLocationGroup;
	private JRadioButton forProjectButton;
	private JRadioButton toFolderButton;
	
	/*
	 * overwrite/use existing TextGrids
	 */
	private JCheckBox overwriteBox;
	
	private final static String OVERWRITE_MESSAGE = "Overwrite existing TextGrids";
	private final static String USE_MESSAGE = "Copy existing TextGrids";
	
	/*
	 * Wizard steps
	 */
	private WizardStep selectRecordsStep;
	
	private WizardStep exportOptionsStep;
	
	
	public TextGridExportWizard(IPhonProject project, ITranscript session) {
		super("TextGrid Export");
		super.setProject(project);
		this.session = session;
		
		setupWizard();
		super.btnFinish.setText("Generate TextGrids");
	}

	private void setupWizard() {
		selectRecordsStep = setupRecordsStep();
		selectRecordsStep.setPrevStep(-1);
		selectRecordsStep.setNextStep(1);
		exportOptionsStep = setupExportOptionsStep();
		exportOptionsStep.setPrevStep(0);
		exportOptionsStep.setNextStep(-1);
		
		addWizardStep(selectRecordsStep);
		addWizardStep(exportOptionsStep);
	}
	
	private WizardStep setupRecordsStep() {
		final WizardStep retVal = new WizardStep();
		
		final DialogHeader header = new DialogHeader("Generate TextGrids", "Select records.");
		
		// add record filter panel
		recordFilterPanel = new UtteranceFilterPanel(getProject(), session);
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
		
		outputFolderField = new FileSelectionField();
		outputFolderField.setMode(SelectionMode.FOLDERS);
		outputFolderField.setEnabled(false);
		
		forProjectButton = new JRadioButton("Save TextGrids in project resources (i.e., __res/plugin_data/textgrid/data)");
		toFolderButton = new JRadioButton("Save TextGrids in external folder (select below)");
		forProjectButton.setSelected(true);

		forProjectButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean state = forProjectButton.isSelected();
				if(state) {
					overwriteBox.setText(OVERWRITE_MESSAGE);
					outputFolderField.setEnabled(false);
				}
			}
			
		});
		
		toFolderButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				boolean state = toFolderButton.isSelected();
				if(state) {
					overwriteBox.setText(USE_MESSAGE);
					outputFolderField.setEnabled(true);
				}
			}
		});
		
		exportLocationGroup = new ButtonGroup();
		exportLocationGroup.add(toFolderButton);
		exportLocationGroup.add(forProjectButton);
		
		final TextGridExporter exporter = new TextGridExporter();
		exportsTree = new ExportEntryCheckboxTree(session);
		exportsTree.setBorder(BorderFactory.createTitledBorder("Select tiers"));
		exportsTree.setChecked(exporter.getExports(getProject()));
		final JScrollPane exportsScroller = new JScrollPane(exportsTree);
		
		final JPanel topPanel = new JPanel();
		topPanel.setLayout(new VerticalLayout());
		topPanel.setBorder(BorderFactory.createTitledBorder("Options"));
		topPanel.add(forProjectButton);
		topPanel.add(toFolderButton);
		topPanel.add(outputFolderField);
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
			
			final TextGridExporter exporter = new TextGridExporter();
			try {
				if(forProjectButton.isSelected()) {
					exporter.exportTextGrids(getProject(), session, 
							recordFilterPanel.getRecordFilter(), exportsTree.getSelectedExports(), overwriteBox.isSelected()); 
				} else {
					exporter.exportTextGrids(getProject(), session, recordFilterPanel.getRecordFilter(),
							exportsTree.getSelectedExports(), outputFolderField.getSelectedFile().getAbsolutePath(), overwriteBox.isSelected());
				}
			} catch (IOException e) {
				super.err = e;
				super.setStatus(TaskStatus.ERROR);
				return;
			}
			
			super.setStatus(TaskStatus.FINISHED);
		}
		
	};
}
