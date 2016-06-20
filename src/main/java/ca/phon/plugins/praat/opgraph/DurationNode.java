package ca.phon.plugins.praat.opgraph;

import java.util.ArrayList;
import java.util.List;

import ca.gedge.opgraph.OpNodeInfo;
import ca.hedlund.jpraat.binding.fon.LongSound;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.phon.query.db.Result;
import ca.phon.query.db.ResultValue;
import ca.phon.query.report.datasource.DefaultTableDataSource;
import ca.phon.session.SessionPath;

@OpNodeInfo(
		name="Duration",
		category="Praat",
		description="Print start/end time and length of result values in the specified column",
		showInLibrary=true
)
public class DurationNode extends PraatNode {

	@Override
	public void addRowToTable(LongSound longSound, TextInterval textInterval, SessionPath sessionPath, Result result,
			ResultValue rv, Object value, DefaultTableDataSource table) {
		// columns
		int cols = 5  + // session + record + tier + group + rv
				3; // start/end time + length;
		
		Object[] rowData = new Object[cols];
		rowData[0] = sessionPath;
		rowData[1] = result.getRecordIndex()+1;
		rowData[2] = rv.getTierName();
		rowData[3] = rv.getGroupIndex()+1;
		rowData[4] = value;
		rowData[5] = textInterval.getXmin();
		rowData[6] = textInterval.getXmax();
		rowData[7] = textInterval.getXmax() - textInterval.getXmin();
		
		table.addRow(rowData);
	}

	@Override
	public List<String> getColumnNames() {
		List<String> colNames = new ArrayList<>();
		
		colNames.add("Session");
		colNames.add("Record");
		colNames.add("Tier");
		colNames.add("Group");
		colNames.add(getColumn());
		colNames.add("Start Time");
		colNames.add("End Time");
		colNames.add("Duration");
		
		return colNames;
	}

}
