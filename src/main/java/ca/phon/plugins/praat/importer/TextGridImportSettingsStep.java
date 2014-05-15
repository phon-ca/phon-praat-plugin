package ca.phon.plugins.praat.importer;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.table.AbstractTableModel;

import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.VerticalLayout;

import ca.phon.session.SessionFactory;
import ca.phon.session.SystemTierType;
import ca.phon.session.TierDescription;
import ca.phon.ui.HidablePanel;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.text.FileSelectionField;
import ca.phon.ui.toast.Toast;
import ca.phon.ui.toast.ToastFactory;
import ca.phon.ui.wizard.WizardStep;

public class TextGridImportSettingsStep extends WizardStep {

	private static final long serialVersionUID = 4032976189938565114L;
	
	private final static String HIDABLE_PANEL_PROP = TextGridImportSettingsStep.class.getName() + ".hideInfoPanel";
	
	private final static String INFO_MESSAGE = 
			"<html><p>This wizard will scan a folder for .TextGrid files and collect a set of tier names found"
			+ " in the located files.</p></html>";
	
	private FileSelectionField folderSelectionField;
	
	private JXTable table;
	
	private TextGridImportTableModel tableModel;
	
	private List<String> textGridTiers = new ArrayList<String>();
	
	private Map<String, TierDescription> tierMap = new LinkedHashMap<String, TierDescription>();
	
	private Map<String, String> groupMarkers = new LinkedHashMap<String, String>();
	
	public TextGridImportSettingsStep() {
		super();
		super.setPrevStep(-1);
		
		init();
	}
	
	private void init() {
		setLayout(new BorderLayout());
		
		final HidablePanel infoPanel = new HidablePanel(HIDABLE_PANEL_PROP);
		infoPanel.setBottomLabelText(INFO_MESSAGE);
		
		folderSelectionField = new FileSelectionField();
		folderSelectionField.setMode(ca.phon.ui.text.FileSelectionField.SelectionMode.FOLDERS);
		folderSelectionField.setEditable(false);
		folderSelectionField.setBorder(BorderFactory.createTitledBorder("Select TextGrid folder:"));
		folderSelectionField.addPropertyChangeListener(FileSelectionField.FILE_PROP, new PropertyChangeListener() {
			
			@Override
			public void propertyChange(PropertyChangeEvent arg0) {
				final File selectedFolder = folderSelectionField.getSelectedFile();
				if(selectedFolder == null) return;
				final TextGridImporter importer = new TextGridImporter();
				final Set<String> tierNames = importer.scanTextGrids(selectedFolder);
				textGridTiers.addAll(tierNames);
				
				final Map<String, TierDescription> mapping = importer.setupDefaultTierMapping(tierNames);
				tierMap.putAll(mapping);
				
				tableModel.fireTableDataChanged();
			}
			
		});
		
		final DialogHeader header = new DialogHeader("Import TextGrids", "Select location of TextGrid files.");
		final JPanel topPanel = new JPanel(new VerticalLayout());
		topPanel.add(header);
		topPanel.add(infoPanel);
		topPanel.add(folderSelectionField);
		
		tableModel = new TextGridImportTableModel();
		table = new JXTable(tableModel);
		final JScrollPane scroller = new JScrollPane(table);
		scroller.setBorder(BorderFactory.createTitledBorder("Setup tier mapping:"));
		
		
		add(topPanel, BorderLayout.NORTH);
		add(scroller, BorderLayout.CENTER);
	}
	
	public File getSelectedFolder() {
		return folderSelectionField.getSelectedFile();
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
		
		if(folderSelectionField.getSelectedFile() == null) {
			final Toast toast = ToastFactory.makeToast("Please select a folder containing .TextGrid files.");
			toast.start(folderSelectionField);
			retVal = false;
		} else if(tierMap.size() == 0) {
			final Toast toast = ToastFactory.makeToast("Please assign at least one TextGrid tier to a Phon tier.");
			toast.start(table);
			retVal = false;
		}
		
		return retVal;
	}
	
}
