package ca.phon.plugins.praat;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import ca.phon.ui.decorations.DialogHeader;

/**
 * Dialog for editing settings for Spectrumt tier of Phon's speech
 * analysis view.
 * 
 * 
 */
public class SpectrumSettingsPanel extends JPanel {

	private static final long serialVersionUID = -7778566797207084580L;

	private JTabbedPane tabPanel;
	
	private SpectrogramSettingsPanel spectrogramSettingsPanel;
	
	private FormantSettingsPanel formantSettingsPanel;
	
	private PitchSettingsPanel pitchSettingsPanel;
	
	private IntensitySettingsPanel intensitySettingsPanel;
	
	public SpectrumSettingsPanel() {
		super();
		
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
	}
	
	public void showSpectrogramSettings() {
		tabPanel.setSelectedComponent(spectrogramSettingsPanel);
	}
	
	public void loadSpectrogramSettings(SpectrogramSettings spectrogramSettings) {
		spectrogramSettingsPanel.loadSettings(spectrogramSettings);
	}
	
	public void showFormantSettings() {
		tabPanel.setSelectedComponent(formantSettingsPanel);
	}
	
	public void loadFormantSettings(FormantSettings formantSettings) {
		formantSettingsPanel.loadSettings(formantSettings);
	}
	
	public void showPitchSettings() {
		tabPanel.setSelectedComponent(pitchSettingsPanel);
	}
	
	public void loadPitchSettings(PitchSettings pitchSettings) {
		pitchSettingsPanel.loadSettings(pitchSettings);
	}
	
	public void showIntensitySettings() {
		tabPanel.setSelectedComponent(intensitySettingsPanel);
	}
	
	public void loadIntensitySettings(IntensitySettings intensitySettings) {
		intensitySettingsPanel.loadSettings(intensitySettings);
	}
	
}
