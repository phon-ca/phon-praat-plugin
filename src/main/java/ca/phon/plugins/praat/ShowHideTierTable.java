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
package ca.phon.plugins.praat;

import javax.swing.table.AbstractTableModel;

import org.jdesktop.swingx.JXTable;

import ca.hedlund.jpraat.binding.fon.Function;

/**
 * UI for allowing user to show/hide TextGrid tiers in the viewer.
 */
public class ShowHideTierTable extends JXTable {

	private final TextGridSpeechAnalysisTier textGridSpeechAnalysisTier;

	public ShowHideTierTable(TextGridSpeechAnalysisTier viewer) {
		super();
		
		this.textGridSpeechAnalysisTier = viewer;
	
		setModel(new TextGridTableModel());
	}
	
	class TextGridTableModel extends AbstractTableModel {

		@Override
		public int getRowCount() {
			return (int)textGridSpeechAnalysisTier.getTextGrid().numberOfTiers();
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
			final Function tier = textGridSpeechAnalysisTier.getTextGrid().tier(rowIndex+1);
			
			final Boolean show = Boolean.parseBoolean(value.toString());
			textGridSpeechAnalysisTier.setTextGridTierVisible(tier.getName(), show);
		}
		
		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			final Function tier = textGridSpeechAnalysisTier.getTextGrid().tier(rowIndex+1);
			
			if(columnIndex == 0) {
				return !textGridSpeechAnalysisTier.isTextGridTierVisible(tier.getName());
			} else {
				return tier.getName();
			}
		}
		
	}
	
}
