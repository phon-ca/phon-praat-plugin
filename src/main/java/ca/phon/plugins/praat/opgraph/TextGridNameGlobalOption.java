package ca.phon.plugins.praat.opgraph;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import ca.phon.app.opgraph.wizard.WizardGlobalOption;
import ca.phon.plugin.IPluginExtensionFactory;
import ca.phon.plugin.IPluginExtensionPoint;
import ca.phon.plugin.PhonPlugin;
import ca.phon.ui.ipamap.io.Grid;
import ca.phon.ui.text.PromptedTextField;

@PhonPlugin(name="praat", author="Greg Hedlund", minPhonVersion="2.2")
public class TextGridNameGlobalOption implements WizardGlobalOption, IPluginExtensionPoint<WizardGlobalOption> {
	
	public static final String TEXTGRIDNAME_KEY = "__textGridName";
	
	private JPanel panel;
	private PromptedTextField textGridNameField;

	@Override
	public String getName() {
		return TEXTGRIDNAME_KEY;
	}

	@Override
	public Class<?> getType() {
		return String.class;
	}

	@Override
	public Object getDefaultValue() {
		return "";
	}

	@Override
	public Object getValue() {
		return textGridNameField.getText();
	}

	@Override
	public JComponent getGlobalOptionsComponent() {
		if(panel == null) {
			panel = new JPanel(new GridBagLayout());
			final GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridheight = 1;
			gbc.gridwidth = 1;
			gbc.gridx = 0;
			gbc.gridy = 0;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 0.0;
			
			panel.add(new JLabel("TextGrid: "), gbc);
			
			textGridNameField = new PromptedTextField("Enter TextGrid name, blank for default");
			
			gbc.gridx++;
			gbc.weightx = 1.0;
			panel.add(textGridNameField, gbc);
		}
		return panel;
	}

	@Override
	public Class<?> getExtensionType() {
		return WizardGlobalOption.class;
	}

	@Override
	public IPluginExtensionFactory<WizardGlobalOption> getFactory() {
		return (Object...args) -> this;
	}

}
