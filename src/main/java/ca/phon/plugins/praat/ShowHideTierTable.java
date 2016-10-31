package ca.phon.plugins.praat;

import javax.swing.JPanel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import org.jdesktop.swingx.JXTable;

import ca.hedlund.jpraat.binding.fon.Function;

/**
 * UI for allowing user to show/hide TextGrid tiers in the viewer.
 */
public class ShowHideTierTable extends JXTable {

	private final TextGridView textGridView;

	public ShowHideTierTable(TextGridView viewer) {
		super();
		
		this.textGridView = viewer;
	
		setModel(new TextGridTableModel());
	}
	
	class TextGridTableModel extends AbstractTableModel {

		@Override
		public int getRowCount() {
			return (int)textGridView.getTextGrid().numberOfTiers();
		}

		@Override
		public int getColumnCount() {
			return 2;
		}
		
		@Override
		public String getColumnName(int col) {
			if(col == 0) {
				return "Show";
			} else {
				return "Name";
			}
		}
		
		@Override
		public Class<?> getColumnClass(int col) {
			if(col == 0) {
				return Boolean.class;
			} else {
				return String.class;
			}
		}
		
		@Override
		public boolean isCellEditable(int row, int col) {
			return (col == 0);
		}

		@Override
		public void setValueAt(Object value, int rowIndex, int columnIndex) {
			final Function tier = textGridView.getTextGrid().tier(rowIndex+1);
			
			final Boolean show = Boolean.parseBoolean(value.toString());
			textGridView.getTextGridPainter().setHidden(tier.getName(), !show);
		}
		
		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			final Function tier = textGridView.getTextGrid().tier(rowIndex+1);
			
			if(columnIndex == 0) {
				return !textGridView.getTextGridPainter().isHidden(tier.getName());
			} else {
				return tier.getName();
			}
		}
		
	}
	
}
