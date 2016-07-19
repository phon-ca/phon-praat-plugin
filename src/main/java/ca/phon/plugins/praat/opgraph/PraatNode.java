package ca.phon.plugins.praat.opgraph;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.jdesktop.swingx.JXTitledSeparator;
import org.jdesktop.swingx.VerticalLayout;

import ca.gedge.opgraph.InputField;
import ca.gedge.opgraph.OpContext;
import ca.gedge.opgraph.app.GraphDocument;
import ca.gedge.opgraph.app.extensions.NodeSettings;
import ca.gedge.opgraph.exceptions.ProcessingException;
import ca.hedlund.jpraat.TextGridUtils;
import ca.hedlund.jpraat.binding.fon.IntervalTier;
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
	
	/**
	 * These options determine what interval is passed
	 * to the implementing subclass in the operate method.
	 */
	private JPanel settingsPanel;
	private JRadioButton recordIntervalBox;
	private JRadioButton textGridTierBox;
	private PromptedTextField textGridTierField;
	private PromptedTextField intervalFilterField;
	private JRadioButton fromColumnBox;
	private PromptedTextField columnField;	

	private boolean useRecordInterval = false;
	private boolean useTextGridInterval = false;
	private String textGridTier = "";
	private String intervalFilter = "";
	private boolean useColumnInterval = true;
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
		
		List<Integer> recordList = new ArrayList<>();
		
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
			
			TextInterval textInterval = null;
			if(isUseRecordInterval()) {
				if(recordList.contains(result.getRecordIndex())) continue;
				try {
					textInterval = TextInterval.create(startTime, endTime, "");
					addRowToTable(longSound, textInterval, sessionName, segment, result, null, null, outputTable);
					recordList.add(result.getRecordIndex());
				} catch (PraatException pe) {
					LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
				}
			} else if(isUseTextGridInterval()) {
				if(recordList.contains(result.getRecordIndex())) continue;
				String textGridTier = getTextGridTier();
				final long tierNum = TextGridUtils.tierNumberFromName(textGrid, textGridTier);
				if(tierNum <= 0) continue;
				
				try {
					final IntervalTier intervalTier = textGrid.checkSpecifiedTierIsIntervalTier(tierNum);
					recordList.add(result.getRecordIndex());
					
					for(long i = 1; i <= intervalTier.numberOfIntervals(); i++) {
						final TextInterval interval = intervalTier.interval(i);
						
						// check interval filter
						if(checkFilter(interval)) {
							addRowToTable(longSound, interval, sessionName, segment, result, null, null, outputTable);
						}
					}
				} catch (PraatException pe) {
					LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
				}
				
			} else {
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
				textInterval = getTextInterval(extendable);
				if(textInterval == null) continue;
				addRowToTable(longSound, textInterval, sessionName, segment, result, rv, resultValue, outputTable);
			}
		}
		
		List<String> colNames = getColumnNames();
		for(int i = 0; i < colNames.size(); i++) {
			outputTable.setColumnTitle(i, colNames.get(i));
		}
		
		context.put(tableOutput, outputTable);
	}
	
	private boolean checkFilter(TextInterval interval) {
		if(getIntervalFilter().trim().length() > 0) {
			return interval.getText().matches(getIntervalFilter());
		} else {
			// no filter
			return true;
		}
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
	
	public boolean isUseRecordInterval() {
		return (this.recordIntervalBox != null ? this.recordIntervalBox.isSelected() : useRecordInterval);
	}

	public void setUseRecordInterval(boolean useRecordInterval) {
		this.useRecordInterval = useRecordInterval;
		if(this.recordIntervalBox != null)
			this.recordIntervalBox.setSelected(useRecordInterval);
	}

	public boolean isUseTextGridInterval() {
		return (this.textGridTierBox != null ? this.textGridTierBox.isSelected() : useTextGridInterval);
	}

	public void setUseTextGridInterval(boolean useTextGridInterval) {
		this.useTextGridInterval = useTextGridInterval;
		if(this.textGridTierBox != null)
			this.textGridTierBox.setSelected(useTextGridInterval);
	}

	public String getIntervalFilter() {
		return (this.intervalFilterField != null ? this.intervalFilterField.getText() : intervalFilter);
	}

	public void setIntervalFilter(String intervalFilter) {
		this.intervalFilter = intervalFilter;
		if(this.intervalFilterField != null)
			this.intervalFilterField.setText(intervalFilter);
	}

	public boolean isUseColumnInterval() {
		return useColumnInterval;
	}

	public void setUseColumnInterval(boolean useColumnInterval) {
		this.useColumnInterval = useColumnInterval;
	}
	
	public String getTextGridTier() {
		return (this.textGridTierField != null ? this.textGridTierField.getText() : textGridTier);
	}
	
	public void setTextGridTier(String textGridTier) {
		this.textGridTier = textGridTier;
		if(this.textGridTierField != null)
			this.textGridTierField.setText(textGridTier);
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
	
	private JPanel createSettingsPanel() {
		final JPanel retVal = new JPanel();
		
		final GridBagLayout layout = new GridBagLayout();
		retVal.setLayout(layout);
		final GridBagConstraints gbc = new GridBagConstraints();
		
		final ButtonGroup btnGroup = new ButtonGroup();
		recordIntervalBox = new JRadioButton("Use full record segment");
		btnGroup.add(recordIntervalBox);
		
		textGridTierBox = new JRadioButton("Use intervals from TextGrid tier");
		btnGroup.add(textGridTierBox);
		
		fromColumnBox = new JRadioButton("Use interval for tier/column value");
		btnGroup.add(fromColumnBox);
		
		final ActionListener btnListener = (e) -> {
			this.textGridTierField.setEnabled(this.textGridTierBox.isSelected());
			this.intervalFilterField.setEnabled(this.textGridTierBox.isSelected());
			this.columnField.setEnabled(this.fromColumnBox.isSelected());
		};
		recordIntervalBox.addActionListener(btnListener);
		textGridTierBox.addActionListener(btnListener);
		fromColumnBox.addActionListener(btnListener);
		
		textGridTierField = new PromptedTextField("Enter TextGrid tier name");
		textGridTierField.setText(textGridTier);
		
		intervalFilterField = new PromptedTextField("Enter interval filter (leave empty to select all)");
		intervalFilterField.setText(intervalFilter);
		
		columnField = new PromptedTextField("Enter tier/column name");
		columnField.setText(column);
		
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.weighty = 0.0;
		gbc.gridwidth = 1;
		gbc.gridheight = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(2, 2, 2, 2);
		
		retVal.add(new JXTitledSeparator("Interval for Analysis"), gbc);

		++gbc.gridy;
		retVal.add(recordIntervalBox, gbc);
		
		++gbc.gridy;
		retVal.add(textGridTierBox, gbc);
		gbc.insets = new Insets(2, 20, 2, 2);
		++gbc.gridy;
		retVal.add(textGridTierField, gbc);
		++gbc.gridy;
		retVal.add(intervalFilterField, gbc);
		++gbc.gridy;
		gbc.insets = new Insets(2, 2, 2, 2);
		retVal.add(fromColumnBox, gbc);
		++gbc.gridy;
		gbc.insets = new Insets(2, 20, 2, 2);
		retVal.add(columnField, gbc);
		
		recordIntervalBox.setSelected(useRecordInterval);
		textGridTierBox.setSelected(useTextGridInterval);
		fromColumnBox.setSelected(useColumnInterval);
		
		return retVal;
	}

	@Override
	public Component getComponent(GraphDocument document) {
		if(settingsPanel == null) {
			settingsPanel = createSettingsPanel();
		}
		return settingsPanel;
	}

	@Override
	public Properties getSettings() {
		Properties retVal = new Properties();
		retVal.put("useRecordInterval", Boolean.toString(isUseRecordInterval()));
		retVal.put("useTextGridInterval", Boolean.toString(isUseTextGridInterval()));
		retVal.put("textGridTier", getTextGridTier());
		retVal.put("intervalFilter", getIntervalFilter());
		retVal.put("useColumnInterval", Boolean.toString(isUseColumnInterval()));
		retVal.put("column", getColumn());
		return retVal;
	}


	@Override
	public void loadSettings(Properties properties) {
		setUseRecordInterval(Boolean.parseBoolean(properties.getProperty("useRecordInterval", "false")));
		setUseTextGridInterval(Boolean.parseBoolean(properties.getProperty("useTextGridInterval", "false")));
		setTextGridTier(properties.getProperty("textGridTier", ""));
		setIntervalFilter(properties.getProperty("intervalFilter", ""));
		setUseColumnInterval(Boolean.parseBoolean(properties.getProperty("useColumnInterval", "true")));
		setColumn(properties.getProperty("column", "IPA Actual"));
	}
	
}
