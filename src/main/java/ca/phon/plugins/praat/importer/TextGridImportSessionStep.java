package ca.phon.plugins.praat.importer;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;

import org.jdesktop.swingx.VerticalLayout;

import ca.phon.app.project.NewSessionPanel;
import ca.phon.app.session.SessionSelector;
import ca.phon.project.Project;
import ca.phon.session.Session;
import ca.phon.session.SessionPath;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.toast.Toast;
import ca.phon.ui.toast.ToastFactory;
import ca.phon.ui.wizard.WizardStep;

public class TextGridImportSessionStep extends WizardStep {

	private static final long serialVersionUID = 6186921366173491102L;
	
	private static final Logger LOGGER = Logger.getLogger(TextGridImportSessionStep.class.getName());

	private ButtonGroup btnGrp = new ButtonGroup();
	
	private JRadioButton currentSessionBtn;
	private SessionSelector sessionSelector;
	
	private JRadioButton newSessionBtn;
	private NewSessionPanel newSessionPanel;
	
	private Project project;
	
	public TextGridImportSessionStep(Project project) {
		super();
		this.project = project;
		
		init();
	}
	
	private void init() {
		setLayout(new BorderLayout());
		
		final DialogHeader header = new DialogHeader("TextGrid Import", "Select destination Session");
		add(header, BorderLayout.NORTH);
		
		final JPanel outsidePane = new JPanel(new BorderLayout());
		final JPanel contentPane = new JPanel(new VerticalLayout());
		
		newSessionBtn = new JRadioButton("New Session:");
		newSessionBtn.addActionListener(btnListener);
		newSessionBtn.setSelected(true);
		btnGrp.add(newSessionBtn);
		contentPane.add(newSessionBtn);
		
		newSessionPanel = new NewSessionPanel(project);
		newSessionPanel.setEnabled(true);
		contentPane.add(newSessionPanel);
		
		currentSessionBtn = new JRadioButton("Existing Session:");
		currentSessionBtn.addActionListener(btnListener);
		currentSessionBtn.setSelected(false);
		btnGrp.add(currentSessionBtn);
		contentPane.add(currentSessionBtn);
		outsidePane.add(contentPane, BorderLayout.NORTH);
		
		sessionSelector = new SessionSelector(project);
		sessionSelector.getCheckingModel().setCheckingMode(CheckingMode.SINGLE);
		sessionSelector.setEnabled(false);
		final JScrollPane scoller = new JScrollPane(sessionSelector);
		
		outsidePane.add(scoller, BorderLayout.CENTER);
		
		add(outsidePane, BorderLayout.CENTER);
	}
	
	private final ActionListener btnListener = new ActionListener() {
		
		@Override
		public void actionPerformed(ActionEvent arg0) {
			newSessionPanel.setEnabled(newSessionBtn.isSelected());
			sessionSelector.setEnabled(currentSessionBtn.isSelected());
		}
		
	};
	
	public Session getSession() {
		Session retVal = null;
		
		if(newSessionBtn.isSelected()) {
			try {
				retVal = project.createSessionFromTemplate(newSessionPanel.getSelectedCorpus(), newSessionPanel.getSessionName());
				retVal.removeRecord(0);
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
		} else {
			final List<SessionPath> selectedSessions = sessionSelector.getSelectedSessions();
			if(selectedSessions.size() == 1) {
				final SessionPath selectedSession = selectedSessions.get(0);
				try {
					retVal = project.openSession(selectedSession.getCorpus(), selectedSession.getSession());
				} catch (IOException e) {
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
		}
		
		return retVal;
	}

	@Override
	public boolean validateStep() {
		boolean retVal = true;
		
		if(newSessionBtn.isSelected()) {
			if(project.getCorpusSessions(newSessionPanel.getSelectedCorpus()).contains(newSessionPanel.getSessionName())) {
				final Toast t = ToastFactory.makeToast("Session already exists, please enter a different name.");
				t.start(newSessionBtn);
				retVal = false;
			}
		} else if(currentSessionBtn.isSelected()) {
			final List<SessionPath> selectedSessions = sessionSelector.getSelectedSessions();
			if(selectedSessions.size() != 1) {
				final Toast t = ToastFactory.makeToast("Please select a Session.");
				t.start(currentSessionBtn);
				retVal = false;
			}
		}
		
		return retVal;
	}
	
}
