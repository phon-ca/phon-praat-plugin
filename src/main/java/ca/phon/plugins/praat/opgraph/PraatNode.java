package ca.phon.plugins.praat.opgraph;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JPanel;

import org.jdesktop.swingx.JXTitledSeparator;
import org.jdesktop.swingx.VerticalLayout;

import ca.gedge.opgraph.InputField;
import ca.gedge.opgraph.OpContext;
import ca.gedge.opgraph.app.GraphDocument;
import ca.gedge.opgraph.app.extensions.NodeSettings;
import ca.gedge.opgraph.exceptions.ProcessingException;
import ca.hedlund.jpraat.binding.fon.LongSound;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.binding.sys.MelderFile;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.app.opgraph.nodes.query.TableOpNode;
import ca.phon.extensions.IExtendable;
import ca.phon.ipa.IPATranscript;
import ca.phon.media.util.MediaLocator;
import ca.phon.orthography.OrthoElement;
import ca.phon.orthography.Orthography;
import ca.phon.plugins.praat.TextGridAnnotator;
import ca.phon.plugins.praat.TextGridManager;
import ca.phon.project.Project;
import ca.phon.query.db.Result;
import ca.phon.query.db.ResultValue;
import ca.phon.query.report.datasource.DefaultTableDataSource;
import ca.phon.session.MediaSegment;
import ca.phon.session.Record;
import ca.phon.session.Session;
import ca.phon.session.SessionPath;
import ca.phon.session.SystemTierType;
import ca.phon.session.Tier;
import ca.phon.ui.text.PromptedTextField;

public abstract class PraatNode extends TableOpNode implements NodeSettings {
	
	private final static Logger LOGGER = Logger.getLogger(PraatNode.class.getName());
	
	protected final InputField projectInput = 
			new InputField("project", "project", Project.class);
	
	private JPanel settingsPanel;
	private PromptedTextField columnField;
	
	private String column = "IPA Actual";
	
	public PraatNode() {
		super();
		
		putField(projectInput);
		putExtension(NodeSettings.class, this);
	}
	

	// get media file
	private File getMediaFile(Project project, Session session) {
		File retVal = null;
		final File mediaFile = 
				MediaLocator.findMediaFile(project, session);
		if(mediaFile != null) {
			String mediaName = mediaFile.getName();
			if(!mediaName.endsWith(".wav")) {
				int lastDot = mediaName.lastIndexOf('.');
				if(lastDot > 0) {
					mediaName = mediaName.substring(0, lastDot) + ".wav";
				}
			}
			retVal = new File(mediaFile.getParentFile(), mediaName);
		}
		
		return retVal;
	}

	private <T extends IExtendable> TextInterval getTextInterval(T obj) {
		TextInterval retVal = obj.getExtension(TextInterval.class);
		
		if(retVal == null) {
			if(obj instanceof Orthography) {
				final Orthography ortho = (Orthography)obj;
				if(ortho.length() > 0) {
					TextInterval firstInterval = ortho.elementAt(0).getExtension(TextInterval.class);
					TextInterval lastInterval = ortho.elementAt(ortho.length()-1).getExtension(TextInterval.class);
					
					if(firstInterval != null && lastInterval != null) {
						try {
							retVal = TextInterval.create(firstInterval.getXmin(), lastInterval.getXmax(), "");
						} catch (PraatException e) {
							LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
						}
					}
				}
			} else if(obj instanceof IPATranscript) {
				final IPATranscript ipa = ((IPATranscript)obj).removePunctuation();
				
				if(ipa.length() > 0) {
					// check first and last phones
					TextInterval firstInterval = ipa.elementAt(0).getExtension(TextInterval.class);
					TextInterval lastInterval = ipa.elementAt(ipa.length()-1).getExtension(TextInterval.class);
					
					if(firstInterval != null && lastInterval != null) {
						try {
							retVal = TextInterval.create(firstInterval.getXmin(), lastInterval.getXmax(), "");
						} catch (PraatException e) {
							LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
						}
					}
				}
			}
		}
		
		return retVal;
	}

	@Override
	public void operate(OpContext context) throws ProcessingException {
		final Project project = (Project)context.get(projectInput);
		
		final DefaultTableDataSource table = (DefaultTableDataSource)context.get(tableInput);
		final DefaultTableDataSource outputTable = new DefaultTableDataSource();
		
		final String globalTgName = (String)context.get(TextGridNameGlobalOption.TEXTGRIDNAME_KEY);

		final TextGridManager tgManager = new TextGridManager(project);
		final TextGridAnnotator annotator = new TextGridAnnotator();
		
		final int resultCol = table.getColumnIndex("Result");
		final int sessionNameCol = table.getColumnIndex("Session");
		
		SessionPath lastSessionName = new SessionPath();
		Session session = null;
		TextGrid textGrid = null;
		LongSound longSound = null;
		for(int row = 0; row < table.getRowCount(); row++) {
			final SessionPath sessionName = (SessionPath)table.getValueAt(row, sessionNameCol);
			if(session == null || !lastSessionName.equals(sessionName)) {
				try {
					session = project.openSession(sessionName.getCorpus(), sessionName.getSession());
					String tgName = 
							(globalTgName == null || globalTgName.length() == 0
							? tgManager.defaultTextGridName(sessionName.getCorpus(), sessionName.getSession())
							: globalTgName);
					textGrid = tgManager.openTextGrid(sessionName.getCorpus(), sessionName.getSession(), tgName);
					File mediaFile = getMediaFile(project, session);
					longSound = LongSound.open(MelderFile.fromPath(mediaFile.getAbsolutePath()));
				} catch (IOException | PraatException e) {
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
			lastSessionName = sessionName;
			if(textGrid == null || longSound == null) continue;
			
			final Result result = (Result)table.getValueAt(row, resultCol);
			final Record record = session.getRecord(result.getRecordIndex());
			final Tier<MediaSegment> segTier = record.getSegment();
			if(segTier.numberOfGroups() == 0) continue;
			final MediaSegment segment = segTier.getGroup(0);
			double startTime = segment.getStartValue() / 1000.0;
			double endTime = segment.getEndValue() / 1000.0;
			try {
				TextGrid recordTextGrid = textGrid.extractPart(startTime, endTime, 1);
				annotator.annotateRecord(recordTextGrid, record);
			} catch (PraatException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
			
			// find correct result value in result
			ResultValue rv = null;
			for(int i = 0; i < result.getNumberOfResultValues(); i++) {
				ResultValue v = result.getResultValue(i);
				if(v.getTierName().equalsIgnoreCase(getColumn())) {
					rv = v;
					break;
				}
			}
			if(rv == null) continue;
			
			Object tierVal = null;
			SystemTierType systemTier = SystemTierType.tierFromString(rv.getTierName());
			if(systemTier != null) {
				switch(systemTier)
				{
				case Orthography:
					tierVal = record.getOrthography().getGroup(rv.getGroupIndex());
					break;
				case IPATarget:
					tierVal = record.getIPATarget().getGroup(rv.getGroupIndex());
					break;
				case IPAActual:
					tierVal = record.getIPAActual().getGroup(rv.getGroupIndex());
					break;
				case Notes:
					tierVal = record.getNotes().getGroup(0);
					break;
					
				default:
					break;
				}
			} else {
				final Tier<?> tier = record.getTier(rv.getTierName());
				tierVal = tier.getGroup(rv.getGroupIndex());
			}
			if(tierVal == null) continue;
			
			Object resultValue = null;
			if(tierVal instanceof Orthography) {
				Orthography ortho = (Orthography)tierVal;
				
				if(rv.getRange().getFirst() == 0
						&& rv.getRange().getRange() == ortho.toString().length()) {
					resultValue = ortho;
				} else {
					// try to copy whole words if possible to retain annotations
					List<OrthoElement> orthoEles = new ArrayList<>();
					int startIdx = 0;
					for(OrthoElement ele:ortho) {
						if(rv.getRange().getFirst() <= startIdx &&
								rv.getRange().getLast() >= (startIdx + ele.text().length())) {
							orthoEles.add(ele);
						}
						startIdx += ele.text().length()+1;
					}
					resultValue = new Orthography(orthoEles);
				}
			} else if(tierVal instanceof IPATranscript) {
				IPATranscript ipa = (IPATranscript)tierVal;
				
				if(rv.getRange().getFirst() == 0 && rv.getRange().getRange() == ipa.toString().length()) {
					resultValue = ipa;
				} else {
					int startPhone = ipa.ipaIndexOf(rv.getRange().getFirst());
					int endPhone = ipa.ipaIndexOf(rv.getRange().getLast()-1);
					resultValue = ipa.subsection(startPhone, endPhone+1);
				}
			} else {
				String txt = tierVal.toString();
				resultValue = txt.substring(rv.getRange().getFirst(), rv.getRange().getLast());
			}
			if(resultValue == null || !(resultValue instanceof IExtendable)) continue;
			
			IExtendable extendable = (IExtendable)resultValue;
			TextInterval textInterval = getTextInterval(extendable);
			if(textInterval == null) continue;
			
			// add formants row
			addRowToTable(longSound, textInterval, sessionName, segment, result, rv, resultValue, outputTable);
		}
		
		List<String> colNames = getColumnNames();
		for(int i = 0; i < colNames.size(); i++) {
			outputTable.setColumnTitle(i, colNames.get(i));
		}
		
		context.put(tableOutput, outputTable);
	}
	
	/**
	 * Get tier/column name for analysis
	 * @return
	 */
	public String getColumn() {
		return (this.columnField != null ? this.columnField.getText() : this.column);
	}
	
	public void setColumn(String column) {
		this.column = column;
		if(this.columnField != null)
			this.columnField.setText(column);
	}
	
	/**
	 * Add data to output table
	 *
	 * @param longSound
	 * @param textInerval
	 * @param sessionPath
	 * @param result
	 * @param rv
	 * @param value
	 * @param table
	 */
	public abstract void addRowToTable(LongSound longSound, TextInterval textInerval,
			SessionPath sessionPath, MediaSegment segment, Result result, ResultValue rv, Object value,
			DefaultTableDataSource table);
	
	public abstract List<String> getColumnNames();

	@Override
	public Component getComponent(GraphDocument document) {
		if(settingsPanel == null) {
			settingsPanel = new JPanel(new VerticalLayout());
			
			settingsPanel.add(new JXTitledSeparator("Tier Name/Column"));
			columnField = new PromptedTextField("Enter tier/column name");
			columnField.setText(this.column);
			settingsPanel.add(columnField);
		}
		return settingsPanel;
	}

	@Override
	public Properties getSettings() {
		Properties retVal = new Properties();
		retVal.put("column", getColumn());
		return retVal;
	}


	@Override
	public void loadSettings(Properties properties) {
		setColumn(properties.getProperty("column", "IPA Actual"));
	}
	
}
