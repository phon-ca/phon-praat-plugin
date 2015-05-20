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
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.VerticalLayout;

import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.phon.formatter.Formatter;
import ca.phon.plugins.praat.TextGridManager;
import ca.phon.session.Session;
import ca.phon.session.SessionFactory;
import ca.phon.session.SystemTierType;
import ca.phon.session.TierDescription;
import ca.phon.ui.HidablePanel;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.text.FormatterTextField;
import ca.phon.ui.toast.Toast;
import ca.phon.ui.toast.ToastFactory;
import ca.phon.ui.wizard.WizardStep;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

public class TextGridImportSettingsStep extends WizardStep {

	private final static Logger LOGGER = Logger.getLogger(TextGridImportSettingsStep.class.getName());
	
	private static final long serialVersionUID = 4032976189938565114L;
	
	private final static String HIDABLE_PANEL_PROP = TextGridImportSettingsStep.class.getName() + ".hideInfoPanel";
	
	private final static String INFO_MESSAGE = 
			"<html><p>Generate records from a TextGrid for this session.</p></html>";
	
	private JComboBox<String> textGridSelector;
	
	private JComboBox<String> tierNameSelector;
	
	private ButtonGroup recordOptionsGroup;
	
	private JRadioButton addRecordsButton;
	
	private JRadioButton replaceRecordsButton;
	
	private FormatterTextField<Double> thresholdField;
	
	private JXTable table;
	
	private TextGridImportTableModel tableModel;
	
	private List<String> textGridTiers = new ArrayList<String>();
	
	private Map<String, TierDescription> tierMap = new LinkedHashMap<String, TierDescription>();
	
	private Map<String, String> groupMarkers = new LinkedHashMap<String, String>();
	
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
		
		final HidablePanel infoPanel = new HidablePanel(HIDABLE_PANEL_PROP);
		infoPanel.setBottomLabelText(INFO_MESSAGE);
		
		final List<String> tgNames = tgManager.textGridNamesForSession(session.getCorpus(), session.getName());
		textGridSelector = new JComboBox<>(tgNames.toArray(new String[0]));
		textGridSelector.addItemListener( this::onSelectTextGrid );
		
		tierNameSelector = new JComboBox<>();
		
		Formatter<Double> formatter = new Formatter<Double>() {

			private NumberFormat nf = 
					NumberFormat.getNumberInstance();
			
			@Override
			public String format(Double arg0) {
				nf.setMaximumFractionDigits(6);
				return nf.format(arg0);
			}

			@Override
			public Double parse(String arg0) throws ParseException {
				return Double.parseDouble(arg0);
			}
			
		};
		
		thresholdField = new FormatterTextField<Double>(formatter);
		thresholdField.setValue(0.05);
		thresholdField.setToolTipText("Mac interval distance in seconds");
		
		JPanel textGridOptionsPanel = new JPanel();
		textGridOptionsPanel.setBorder(BorderFactory.createTitledBorder("TextGrid Options"));
		textGridOptionsPanel.setLayout(new FormLayout("right:pref, 3dlu, fill:pref:grow", "pref, 3dlu, pref, 3dlu, pref"));
		final CellConstraints cc = new CellConstraints();
		textGridOptionsPanel.add(new JLabel("TextGrid"), cc.xy(1,1));
		textGridOptionsPanel.add(textGridSelector, cc.xy(3,1));
		textGridOptionsPanel.add(new JLabel("Tier"), cc.xy(1, 3));
		textGridOptionsPanel.add(tierNameSelector, cc.xy(3,3));
		textGridOptionsPanel.add(new JLabel("Max interval distance"), cc.xy(1, 5));
		textGridOptionsPanel.add(thresholdField, cc.xy(3, 5));
		
		
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
		topPanel.add(infoPanel);
		topPanel.add(textGridOptionsPanel);
//		topPanel.add(recordOptionsPanel);
		
		tableModel = new TextGridImportTableModel();
		table = new JXTable(tableModel);
		final JScrollPane scroller = new JScrollPane(table);
		scroller.setBorder(BorderFactory.createTitledBorder("Tier Options"));
		
		add(topPanel, BorderLayout.NORTH);
		add(scroller, BorderLayout.CENTER);
		
		SwingUtilities.invokeLater( () -> {
			textGridSelector.setSelectedItem(tgManager.defaultTextGridName(session.getCorpus(), session.getName()));
			onSelectTextGrid(new ItemEvent(textGridSelector, -1, textGridSelector.getSelectedItem(), ItemEvent.SELECTED));
		});
	}
	
	public void onSelectTextGrid(ItemEvent itemEvent) {
		final String textGridName = textGridSelector.getSelectedItem().toString();
		
		try {
			final TextGrid textGrid = tgManager.openTextGrid(session.getCorpus(), session.getName(), textGridName);
			final List<String> tierNames = new ArrayList<>();
			for(long i = 1; i <= textGrid.numberOfTiers(); i++) {
				final Function tier = textGrid.tier(i);
				tierNames.add(tier.getName().toString());
			}
			tierNameSelector.setModel(new DefaultComboBoxModel<>(tierNames.toArray(new String[0])));
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
	
	public String getSelectedTextGrid() {
		return 
				(textGridSelector.getSelectedItem() != null ? textGridSelector.getSelectedItem().toString() : "");
	}
	
	public long getSelectedTier() {
		return (long)(tierNameSelector.getSelectedIndex() >= 0 ? tierNameSelector.getSelectedIndex() + 1 : 0);
	}
	
	public double getThreshold() {
		return thresholdField.getValue();
	}
	
	public boolean isDeleteAllRecords() {
		return replaceRecordsButton.isSelected();
	}
	
	public Map<String, TierDescription> getTierMap() {
		return this.tierMap;
	}
	
	public Map<String, String> getMarkerMap() {
		return this.groupMarkers;
	}

	public enum Col {
		TG_TIER("TextGrid Tier"),
		PHON_TIER("Phon Tier"),
		GROUPED("Grouped"),
		GROUP_MARKER("Group Marker");
		
		private String name;
		
		private Col(String name) {
			this.name = name;
		}
		
		public String getName() { return this.name; }
		
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
			
			if(columnIndex == Col.GROUPED.ordinal()) {
				retVal = Boolean.class;
			}
			
			return retVal;
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			if(columnIndex == Col.PHON_TIER.ordinal()) {
				final String selectedTier = (String)aValue;
				if(selectedTier.length() == 0) {
					tierMap.remove((String)getValueAt(rowIndex, Col.TG_TIER.ordinal()));
					super.fireTableRowsUpdated(rowIndex, rowIndex);
				} else if(SystemTierType.isSystemTier(selectedTier)) {
					final SystemTierType systemTier = SystemTierType.tierFromString(selectedTier);
					final TierDescription td = (SessionFactory.newFactory()).createTierDescription(systemTier.getName(), systemTier.isGrouped());
					
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
			} else if(columnIndex == Col.GROUPED.ordinal()) {
				final String tierName = (String)getValueAt(rowIndex, Col.PHON_TIER.ordinal());
				final TierDescription td = (SessionFactory.newFactory()).createTierDescription(tierName, (Boolean)aValue);
				tierMap.put((String)getValueAt(rowIndex, Col.TG_TIER.ordinal()), td);
				super.fireTableCellUpdated(rowIndex, columnIndex);
			} else if(columnIndex == Col.GROUP_MARKER.ordinal()) {
				final String tierName = (String)getValueAt(rowIndex, Col.TG_TIER.ordinal());
				groupMarkers.put(tierName, (String)aValue);
			}
		}

		@Override
		public boolean isCellEditable(int row, int col) {
			if(col == Col.PHON_TIER.ordinal()) {
				return true;
			} else if(col == Col.GROUPED.ordinal()) {
				// check column 1
				final String selectedTier = (String)getValueAt(row, Col.PHON_TIER.ordinal());
				if(SystemTierType.isSystemTier(selectedTier)) {
					return false;
				} else {
					return true;
				}
			} else if(col == Col.GROUP_MARKER.ordinal()) {
				final Boolean isGrouped = (Boolean)getValueAt(row, Col.GROUPED.ordinal());
				return isGrouped;
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
			} else if(col == Col.GROUPED.ordinal()) {
				final TierDescription td = tierMap.get(tgTier);
				retVal = (td != null ? td.isGrouped() : false);
			} else if(col == Col.GROUP_MARKER.ordinal()) {
				final String gm = groupMarkers.get(tgTier);
				retVal = (gm != null ? gm : "#");
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
