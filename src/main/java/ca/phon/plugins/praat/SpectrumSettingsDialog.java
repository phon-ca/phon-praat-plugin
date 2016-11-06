package ca.phon.plugins.praat;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import ca.phon.ui.CommonModuleFrame;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.layout.ButtonBarBuilder;

/**
 * Dialog for editing settings for Spectrumt tier of Phon's speech
 * analysis view.
 * 
 * 
 */
public class SpectrumSettingsDialog extends JDialog {

	private JTabbedPane tabPanel;
	
	private SpectrogramSettingsPanel spectrogramSettingsPanel;
	
	private FormantSettingsPanel formantSettingsPanel;
	
	private PitchSettingsPanel pitchSettingsPanel;
	
	private IntensitySettingsPanel intensitySettingsPanel;
	
	private JButton okButton;
	
	private JButton cancelButton;
	
	private JButton applyButton;
	
	public SpectrumSettingsDialog() {
		super();
		
		setModal(true);
		
		init();
	}
	
	private void init() {
		setLayout(new BorderLayout());
		
		final DialogHeader header = new DialogHeader("Spectrum Settigns", "Edit settings for Spectrum tier.");
		add(header, BorderLayout.NORTH);
		
		tabPanel = new JTabbedPane();
		add(tabPanel, BorderLayout.CENTER);
		
		spectrogramSettingsPanel = new SpectrogramSettingsPanel();
		tabPanel.add("Spectrogram", spectrogramSettingsPanel);
		
		formantSettingsPanel = new FormantSettingsPanel();
		tabPanel.add("Formant", formantSettingsPanel);
		
		pitchSettingsPanel = new PitchSettingsPanel();
		tabPanel.add("Pitch", pitchSettingsPanel);
		
		intensitySettingsPanel = new IntensitySettingsPanel();
		tabPanel.add("Intensity", intensitySettingsPanel);
		
		final PhonUIAction okAct = new PhonUIAction(this, "onOk");
		okAct.putValue(PhonUIAction.NAME, "Ok");
		okButton = new JButton(okAct);
		
		final PhonUIAction cancelAct = new PhonUIAction(this, "onCancel");
		cancelAct.putValue(PhonUIAction.NAME, "Cancel");
		cancelButton = new JButton(cancelAct);
		
		final PhonUIAction applyAct = new PhonUIAction(this, "onApply");
		applyAct.putValue(PhonUIAction.NAME, "Apply");
		applyButton = new JButton(applyAct);
		
		final JComponent btnPanel = 
				ButtonBarBuilder.buildOkCancelBar(okButton, cancelButton, applyButton);
		add(btnPanel, BorderLayout.SOUTH);
	}
	
	public void loadSpectrogramSettings(SpectrogramSettings spectrogramSettings) {
		spectrogramSettingsPanel.loadSettings(spectrogramSettings);
	}
	
	public void loadFormantSettings(FormantSettings formantSettings) {
		formantSettingsPanel.loadSettings(formantSettings);
	}
	
	public void loadPitchSettings(PitchSettings pitchSettings) {
		pitchSettingsPanel.loadSettings(pitchSettings);
	}
	
	public void loadIntensitySettings(IntensitySettings intensitySettings) {
		intensitySettingsPanel.loadSettings(intensitySettings);
	}
	
}
