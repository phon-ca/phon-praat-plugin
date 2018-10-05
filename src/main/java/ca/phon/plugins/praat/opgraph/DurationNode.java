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
package ca.phon.plugins.praat.opgraph;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import ca.hedlund.jpraat.binding.fon.*;
import ca.phon.opgraph.OpNodeInfo;
import ca.phon.opgraph.app.GraphDocument;
import ca.phon.query.db.*;
import ca.phon.query.report.datasource.DefaultTableDataSource;
import ca.phon.session.*;

@OpNodeInfo(
		name="Duration",
		category="Praat",
		description="Print start/end time and length of result values in the specified column",
		showInLibrary=true
)
public class DurationNode extends PraatNode {

	private JPanel settingsPanel;

	@Override
	public void addRowToTable(LongSound longSound, TextGrid textGrid, TextInterval textInterval, 
			Session session, SessionPath sessionPath,
			MediaSegment segment, Result result,
			ResultValue rv, Object value, DefaultTableDataSource table) {
		final Record r = (result.getRecordIndex() < session.getRecordCount() ? session.getRecord(result.getRecordIndex()) : null);
		final Participant speaker = (r != null ? r.getSpeaker() : Participant.UNKNOWN);
		
		Object[] rowData = new Object[getColumnNames().size()];
		int col = 0;
		rowData[col++] = sessionPath;
		rowData[col++] = speaker;
		rowData[col++] = (speaker != Participant.UNKNOWN ? speaker.getAge(session.getDate()) : "");
		rowData[col++] = result.getRecordIndex()+1;
		rowData[col++] = result;

		if(isUseRecordInterval()) {
			// add nothing
		} else if(isUseTextGridInterval()) {
			rowData[col++] = textInterval.getText();
		} else {
			rowData[col++] = rv.getTierName();
			rowData[col++] = rv.getGroupIndex()+1;
			rowData[col++] = value;
		}

		rowData[col++] = textInterval.getXmin();
		rowData[col++] = textInterval.getXmax();
		rowData[col++] = textInterval.getXmax() - textInterval.getXmin();

		table.addRow(rowData);
	}

	@Override
	public Component getComponent(GraphDocument document) {
		if(settingsPanel == null) {
			final GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;
			gbc.gridy = 7;
			gbc.weightx = 1.0;
			gbc.weighty = 1.0;
			gbc.fill = GridBagConstraints.BOTH;

			settingsPanel = (JPanel)super.getComponent(document);
			settingsPanel.add(Box.createVerticalGlue(), gbc);
		}
		return settingsPanel;
	}



	@Override
	public List<String> getColumnNames() {
		List<String> colNames = new ArrayList<>();

		colNames.add("Session");
		colNames.add("Speaker");
		colNames.add("Age");
		colNames.add("Record #");
		colNames.add("Result");

		if(isUseRecordInterval()) {
			// no extra tiers
		} else if (isUseTextGridInterval()) {
			colNames.add("Text");
		} else {
			colNames.add("Tier");
			colNames.add("Group #");
			colNames.add(getColumn());
		}

		colNames.add("Start Time");
		colNames.add("End Time");
		colNames.add("Duration");

		return colNames;
	}

}
