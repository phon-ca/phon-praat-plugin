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

import ca.hedlund.jpraat.binding.fon.*;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.app.session.editor.EditorEventName;
import ca.phon.app.session.editor.view.tierManagement.TierEditorDialog;
import ca.phon.formatter.Formatter;
import ca.phon.formatter.*;
import ca.phon.plugins.praat.TextGridManager;
import ca.phon.session.*;
import ca.phon.session.tierdata.TierData;
import ca.phon.ui.HidablePanel;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.toast.*;
import ca.phon.ui.wizard.WizardStep;
import org.apache.commons.io.FilenameUtils;
import org.jdesktop.swingx.*;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.logging.*;

public class TextGridImportSettingsStep extends WizardStep {

	private final static Logger LOGGER = Logger.getLogger(TextGridImportSettingsStep.class.getName());

	private final static String MSG1_PROP = TextGridImportSettingsStep.class.getName() + ".msg1";
	private final static String INFO_MESSAGE =
			"<html><p>This function will create a record for each interval in the reference tier.</p></html>";

	private final static String MSG2_PROP = TextGridImportSettingsStep.class.getName() + "msg2";
	private final static String INFO_MESSAGE2 =
			"<html><p>For each TextGrid tier you wish to import, enter the corresponding Phon tier name and group options.</p></html>";

	private JComboBox<File> textGridSelector;

	private JComboBox<String> tierNameSelector;

	private JCheckBox ignoreEmptyIntervalsBox;

	private ButtonGroup recordOptionsGroup;

	private JRadioButton addRecordsButton;

	private JRadioButton replaceRecordsButton;

	private JXTable table;

	private TextGridImportTableModel tableModel;

	private List<String> textGridTiers = new ArrayList<String>();

	private Map<String, TierDescription> tierMap = new LinkedHashMap<String, TierDescription>();

	private Session session;

	private TextGridManager tgManager;

	public TextGridImportSettingsStep(Session session, TextGridManager tgManager) {
		super();
		super.setPrevStep(-1);

		this.session = session;
		this.tgManager = tgManager;

		init();
	}

	private void init() {
		setLayout(new BorderLayout());

		final HidablePanel infoPanel = new HidablePanel(MSG1_PROP);
		infoPanel.setBottomLabelText(INFO_MESSAGE);

		final HidablePanel tblInfoPanel = new HidablePanel(MSG2_PROP);
		tblInfoPanel.setBottomLabelText(INFO_MESSAGE2);

		final List<File> tgNames = tgManager.textGridFilesForSession(session);
		textGridSelector = new JComboBox<>(tgNames.toArray(new File[0]));
		textGridSelector.addItemListener( this::onSelectTextGrid );
		textGridSelector.setRenderer(new TextGridCellRenderer());

		tierNameSelector = new JComboBox<>();

		JPanel textGridOptionsPanel = new JPanel();
		textGridOptionsPanel.setBorder(BorderFactory.createTitledBorder("Record Detection Options"));
		final GridBagLayout textGridOptionsLayout = new GridBagLayout();
		textGridOptionsPanel.setLayout(textGridOptionsLayout);
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridheight = 1;
		gbc.gridwidth = 1;
		gbc.anchor = GridBagConstraints.EAST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.insets = new Insets(2, 2, 2, 2);

		gbc.weightx = 1.0;
		gbc.gridwidth = 2;
		textGridOptionsPanel.add(infoPanel, gbc);

		gbc.weightx = 0.0;
		gbc.gridwidth = 1;
		++gbc.gridy;
		textGridOptionsPanel.add(new JLabel("TextGrid"), gbc);
		gbc.weightx = 1.0;
		++gbc.gridx;
		gbc.anchor = GridBagConstraints.WEST;
		textGridOptionsPanel.add(textGridSelector, gbc);

		++gbc.gridy;
		gbc.gridx = 0;
		gbc.weightx = 0.0;
		gbc.anchor = GridBagConstraints.EAST;
		textGridOptionsPanel.add(new JLabel("Record reference tier"), gbc);
		gbc.weightx = 1.0;
		++gbc.gridx;
		gbc.anchor = GridBagConstraints.WEST;
		textGridOptionsPanel.add(tierNameSelector, gbc);

		++gbc.gridy;
		gbc.weightx = 1.0;
		gbc.anchor = GridBagConstraints.WEST;
		ignoreEmptyIntervalsBox = new JCheckBox("Ignore empty intervals");
		ignoreEmptyIntervalsBox.setSelected(true);
//		ignoreEmptyIntervalsBox.setSelected(
//				PrefHelper.getBoolean(TextGridImporter.IGNORE_EMPTY_INTERVALS_PROP, Boolean.TRUE));
//		ignoreEmptyIntervalsBox.addActionListener( (e) -> PrefHelper.getUserPreferences().putBoolean(
//				TextGridImporter.IGNORE_EMPTY_INTERVALS_PROP, ignoreEmptyIntervalsBox.isSelected()) );
		textGridOptionsPanel.add(ignoreEmptyIntervalsBox, gbc);

		recordOptionsGroup = new ButtonGroup();
		addRecordsButton = new JRadioButton("Add records to session");
		replaceRecordsButton = new JRadioButton("Replace records in session");
		recordOptionsGroup.add(addRecordsButton);
		recordOptionsGroup.add(replaceRecordsButton);
		addRecordsButton.setSelected(true);

		JPanel recordOptionsPanel = new JPanel();
		recordOptionsPanel.setBorder(BorderFactory.createTitledBorder("Record Options"));
		recordOptionsPanel.setLayout(new VerticalLayout());
		recordOptionsPanel.add(addRecordsButton);
		recordOptionsPanel.add(replaceRecordsButton);

		final DialogHeader header = new DialogHeader("Create Records", "Create records from TextGrid data");
		final JPanel topPanel = new JPanel(new VerticalLayout());
		topPanel.add(header);
		topPanel.add(textGridOptionsPanel);

		add(topPanel, BorderLayout.NORTH);

		tableModel = new TextGridImportTableModel();
		table = new JXTable(tableModel);
		final JScrollPane scroller = new JScrollPane(table);


		final JComboBox<String> tierNameBox = new JComboBox<>(new TierNameModel());
		tierNameBox.addItemListener( e -> {
			if(e.getStateChange() == ItemEvent.SELECTED && tierNameBox.getSelectedItem() != null
					&& tierNameBox.getSelectedItem().equals("New tier...")) {
				showNewTierDialog();
			}
		});

		table.getColumn(Col.PHON_TIER.ordinal()).setCellEditor(new DefaultCellEditor(tierNameBox));
		table.setSortable(false);

		final JPanel btmPanel = new JPanel(new BorderLayout());
		btmPanel.add(tblInfoPanel, BorderLayout.NORTH);
		btmPanel.add(scroller, BorderLayout.CENTER);
		btmPanel.setBorder(BorderFactory.createTitledBorder("Tier Names & Options"));
		add(btmPanel, BorderLayout.CENTER);

		SwingUtilities.invokeLater( () -> {
			textGridSelector.setSelectedIndex(0);
			onSelectTextGrid(new ItemEvent(textGridSelector, -1, textGridSelector.getSelectedItem(), ItemEvent.SELECTED));
		});
	}

	private void showNewTierDialog() {
		final int row = table.getSelectedRow();
		if(row < 0) return;

		final String tgTier = (String)table.getValueAt(row, Col.TG_TIER.ordinal());

		final TierEditorDialog ted = new TierEditorDialog(session, false);
		ted.getTierEditor().setTierName(tgTier);
		ted.setModal(true);
		ted.pack();
		if(ted.showDialog()) {
			final SessionFactory factory = SessionFactory.newFactory();
			// add tier to session
			final TierDescription tierDesc = factory.createTierDescription(
					ted.getTierEditor().getTierName(), TierData.class, new HashMap<>(), false, false);
			session.addUserTier(tierDesc);

			final Formatter<Font> fontFormatter = FormatterFactory.createFormatter(Font.class);
			final String fontString = fontFormatter.format(ted.getTierEditor().getTierFont());
			final TierViewItem tvi = factory.createTierViewItem(
					ted.getTierEditor().getTierName(), true, fontString, false);
			final List<TierViewItem> tierView = new ArrayList<>(session.getTierView());
			tierView.add(tvi);
			session.setTierView(tierView);

			firePropertyChange(EditorEventName.TIER_VIEW_CHANGED_EVT.getEventName(), true, false);

			SwingUtilities.invokeLater( () ->
				table.setValueAt(ted.getTierEditor().getTierName(), row, Col.PHON_TIER.ordinal()) );
		} else {
			SwingUtilities.invokeLater( () ->
				table.setValueAt("", row, Col.PHON_TIER.ordinal()) );
		}
	}

	public boolean isIgnoreEmptyIntervals() {
		return this.ignoreEmptyIntervalsBox.isSelected();
	}

	public void onSelectTextGrid(ItemEvent itemEvent) {
		final File textGridFile = (File)textGridSelector.getSelectedItem();

		try {
			final TextGrid textGrid = TextGridManager.loadTextGrid(textGridFile);
			final List<String> tierNames = new ArrayList<>();
			for(long i = 1; i <= textGrid.numberOfTiers(); i++) {
				try {
					final IntervalTier it = textGrid.checkSpecifiedTierIsIntervalTier(i);
					tierNames.add(it.getName());
				} catch (PraatException pe) {
					// ignore point tiers
				}
			}
			tierNameSelector.setModel(new DefaultComboBoxModel<>(tierNames.toArray(new String[0])));
			if(tierNames.size() > 0) {
				tierNameSelector.setSelectedIndex(0);
			}
			textGridTiers.clear();
			textGridTiers.addAll(tierNames);

			// setup tier mapping
			final TextGridImporter importer = new TextGridImporter();
			Map<String, TierDescription> tierMapping = importer.setupDefaultTierMapping(new LinkedHashSet<>(tierNames));
			tierMap.clear();
			tierMap.putAll(tierMapping);
			tableModel.fireTableDataChanged();
		} catch (IOException e) {
			ToastFactory.makeToast(e.getLocalizedMessage()).start(textGridSelector);
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	public File getSelectedTextGrid() {
		return
				(textGridSelector.getSelectedItem() != null ? (File)textGridSelector.getSelectedItem() : null);
	}

	public String getSelectedTier() {
		return (String)tierNameSelector.getSelectedItem();
	}

	public boolean isDeleteAllRecords() {
		return replaceRecordsButton.isSelected();
	}

	public Map<String, TierDescription> getTierMap() {
		return this.tierMap;
	}

	public enum Col {
		TG_TIER("TextGrid Tier"),
		PHON_TIER("Phon Tier");

		private String name;

		private Col(String name) {
			this.name = name;
		}

		public String getName() { return this.name; }

	}

	private class TextGridCellRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			final JLabel retVal = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			final File f = (File)value;
			retVal.setText(FilenameUtils.getBaseName(f.getAbsolutePath()));

			return retVal;
		}

	}

	private class TierNameModel extends DefaultComboBoxModel<String> {

		public List<String> getTierList() {
			List<String> retVal = new ArrayList<>();

			retVal.add("");
			retVal.add(SystemTierType.Orthography.getName());
			retVal.add(SystemTierType.IPATarget.getName());
			retVal.add(SystemTierType.IPAActual.getName());
			retVal.add(SystemTierType.Notes.getName());

			session.getUserTiers().forEach( tier -> retVal.add(tier.getName()) );

			retVal.add("New tier...");

			return retVal;
		}

		@Override
		public int getSize() {
			int retVal = 2; // blank + New tier...

			retVal += 4; // default tiers
			retVal += session.getUserTierCount();

			return retVal;
		}

		@Override
		public String getElementAt(int index) {
			return getTierList().get(index);
		}

	}

	private class TextGridImportTableModel extends AbstractTableModel {

		private static final long serialVersionUID = 1L;

		@Override
		public int getColumnCount() {
			return Col.values().length;
		}

		@Override
		public String getColumnName(int col) {
			return Col.values()[col].getName();
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			Class<?> retVal = String.class;
			return retVal;
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			if(columnIndex == Col.PHON_TIER.ordinal()) {
				final String selectedTier = (String)aValue;
				if(selectedTier == null) return;
				if(selectedTier.length() == 0) {
					tierMap.remove((String)getValueAt(rowIndex, Col.TG_TIER.ordinal()));
					super.fireTableRowsUpdated(rowIndex, rowIndex);
				} else if(SystemTierType.isSystemTier(selectedTier)) {
					final SystemTierType systemTier = SystemTierType.tierFromString(selectedTier);
					final TierDescription td = (SessionFactory.newFactory()).createTierDescription(systemTier);

					// make sure no other tier is mapped to this system tier
					for(String tierName:textGridTiers) {
						final TierDescription tierDesc = tierMap.get(tierName);
						if(tierDesc != null && tierDesc.getName().equals(selectedTier)) {
							tierMap.remove(tierName);

							final int tierRow = textGridTiers.indexOf(tierName);
							super.fireTableRowsUpdated(tierRow, tierRow);
						}
					}

					tierMap.put((String)getValueAt(rowIndex, Col.TG_TIER.ordinal()), td);
					super.fireTableRowsUpdated(rowIndex, rowIndex);
				} else {
					// create a new phon tier with the name of the text grid tier
					final String tgTier = (String)getValueAt(rowIndex, Col.TG_TIER.ordinal());
					final TierDescription td = (SessionFactory.newFactory()).createTierDescription(selectedTier, true);

					for(String tierName:textGridTiers) {
						final TierDescription tierDesc = tierMap.get(tierName);
						if(tierDesc != null && tierDesc.getName().equals(selectedTier)) {
							tierMap.remove(tierName);

							final int tierRow = textGridTiers.indexOf(tierName);
							super.fireTableRowsUpdated(tierRow, tierRow);
						}
					}

					tierMap.put(tgTier, td);
					super.fireTableRowsUpdated(rowIndex, rowIndex);
				}
			}
		}

		@Override
		public boolean isCellEditable(int row, int col) {
			if(col == Col.PHON_TIER.ordinal()) {
				return true;
			}
			return false;
		}

		@Override
		public int getRowCount() {
			return textGridTiers.size();
		}

		@Override
		public Object getValueAt(int row, int col) {
			final String tgTier = textGridTiers.get(row);

			Object retVal = null;

			if(col == Col.TG_TIER.ordinal()) {
				retVal = tgTier;
			} else if(col == Col.PHON_TIER.ordinal()) {
				final TierDescription td = tierMap.get(tgTier);
				retVal = (td != null ? td.getName() : null);
			}

			return retVal;
		}

	}

	@Override
	public boolean validateStep() {
		boolean retVal = true;

		if(tierMap.size() == 0) {
			final Toast toast = ToastFactory.makeToast("Please assign at least one TextGrid tier to a Phon tier.");
			toast.start(table);
			retVal = false;
		}

		return retVal;
	}

}
