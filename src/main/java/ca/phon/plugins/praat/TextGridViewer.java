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
package ca.phon.plugins.praat;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.event.MouseInputAdapter;

import org.jdesktop.swingx.VerticalLayout;

import ca.hedlund.jpraat.binding.fon.IntervalTier;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.binding.sys.Data;
import ca.hedlund.jpraat.binding.sys.MelderFile;
import ca.hedlund.jpraat.binding.sys.PraatDir;
import ca.hedlund.jpraat.binding.sys.SendPraat;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.app.session.editor.DelegateEditorAction;
import ca.phon.app.session.editor.EditorAction;
import ca.phon.app.session.editor.EditorEvent;
import ca.phon.app.session.editor.EditorEventType;
import ca.phon.app.session.editor.RunOnEDT;
import ca.phon.app.session.editor.SessionEditor;
import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisEditorView;
import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisTier;
import ca.phon.media.sampled.PCMSegmentView;
import ca.phon.media.util.MediaLocator;
import ca.phon.plugins.praat.export.TextGridExportWizard;
import ca.phon.plugins.praat.painters.TextGridPainter;
import ca.phon.plugins.praat.script.PraatScript;
import ca.phon.plugins.praat.script.PraatScriptContext;
import ca.phon.plugins.praat.script.PraatScriptTcpHandler;
import ca.phon.plugins.praat.script.PraatScriptTcpServer;
import ca.phon.session.MediaSegment;
import ca.phon.session.RangeRecordFilter;
import ca.phon.session.Record;
import ca.phon.session.RecordFilter;
import ca.phon.session.SystemTierType;
import ca.phon.session.Tier;
import ca.phon.ui.action.PhonActionEvent;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.ui.fonts.FontPreferences;
import ca.phon.ui.toast.Toast;
import ca.phon.ui.toast.ToastFactory;
import ca.phon.util.PrefHelper;
import ca.phon.util.icons.IconManager;
import ca.phon.util.icons.IconSize;

/**
 * Display a TextGrid as a vertical list of tiers.
 */
public class TextGridViewer extends JPanel implements SpeechAnalysisTier {
	
	public static final String TEXT_GRID_CHANGED_EVENT = 
			TextGridViewer.class.getName() + ".textGridChangedEvent";
	
	private static final Logger LOGGER = Logger
			.getLogger(TextGridViewer.class.getName());
	
	private static final long serialVersionUID = -4777676504641976886L;
	
	private final static String OPEN_TEXTGRID_TEMPLATE = "ca/phon/plugins/praat/OpenTextGrid.vm";
	
	private TextGridManager tgManager;
	
	private TextGrid tg;
	
	// parent panel
	private SpeechAnalysisEditorView parent;
	
	private TextGridContentPanel contentPane;
	
	private TextGridPainter tgPainter = new TextGridPainter();
	
	private JPanel buttonPane;
	
	public final static String SHOW_TEXTGRID_PROP = TextGridViewer.class.getName() + ".showTextGrid";
	private boolean showTextGrid = 
			PrefHelper.getBoolean(SHOW_TEXTGRID_PROP, false);
	
	public TextGridViewer(SpeechAnalysisEditorView parent) {
		super();
		setVisible(showTextGrid);
		setFocusable(true);
		
		this.parent = parent;
		
		buttonPane = new JPanel(new VerticalLayout());
		contentPane = new TextGridContentPanel();
		
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.SELECTION_LENGTH_PROP, tierUpdater);
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.WINDOW_START_PROT, tierUpdater);
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.WINDOW_LENGTH_PROP, tierUpdater);
		
		setLayout(new BorderLayout());
		add(buttonPane, BorderLayout.NORTH);
		add(contentPane, BorderLayout.CENTER);
		
		tgManager = new TextGridManager(parent.getEditor().getProject());
		// load default TextGrid
		final File defaultTextGridFile = tgManager.defaultTextGridFile(parent.getEditor().getSession().getCorpus(),
				parent.getEditor().getSession().getName());
		if(defaultTextGridFile != null) {
			try {
				final TextGrid tg = TextGridManager.loadTextGrid(defaultTextGridFile);
				setTextGrid(tg);
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
				ToastFactory.makeToast(e.getLocalizedMessage()).start(this);
			}
		} else {
			setTextGrid(null);
		}
		
		// setup toolbar buttons
		setupToolbar();
		
		setupEditorActions();
	}
	
	@Override
	public void addNotify() {
		super.addNotify();
		update();
	}
	
	private void setupEditorActions() {
		// setup editor actions
		final EditorAction recordChangedAct = new DelegateEditorAction(this, "onRecordChanged");
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.RECORD_CHANGED_EVT, recordChangedAct);
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.RECORD_REFRESH_EVT, recordChangedAct);
		
		final EditorAction textGridChangedAct = new DelegateEditorAction(this, "onTextGridChanged");
		parent.getEditor().getEventManager().registerActionForEvent(TEXT_GRID_CHANGED_EVENT, textGridChangedAct);
		
		final EditorAction segChangedAct = new DelegateEditorAction(this, "onTierChanged");
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.TIER_CHANGED_EVT, segChangedAct);
	}
	
	public void setTextGrid(TextGrid tg) {
		this.tg = tg;
		setupTextGrid();
	}
	
	public TextGrid getTextGrid() {
		return this.tg;
	}
	
	private final List<TextGridTierComponent> tiers = 
			Collections.synchronizedList(new ArrayList<TextGridTierComponent>());
	
	private final PropertyChangeListener tierUpdater = new PropertyChangeListener() {
		
		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			tgPainter.setRepaintBuffer(true);
			repaint();
		}
		
	};
	
	private void setupTextGrid() {
		contentPane.removeAll();
		tiers.clear();
		
//		if(tg != null) {
//			final Record currentRecord = parent.getEditor().currentRecord();
//			final Tier<MediaSegment> segTier = currentRecord.getSegment();
//			final MediaSegment seg = (segTier.numberOfGroups() == 1 ? segTier.getGroup(0) : null);
//			if(seg == null
//					) {
//				final JLabel errLabel = new JLabel("<html><b>TextGrid dimensions do not match segment</b></html>");
//				errLabel.setBackground(Color.red);
//				errLabel.setOpaque(true);
//				
//				final PhonUIAction generateTextGridAct = new PhonUIAction
//						(this, "onGenerateTextGrid");
//				generateTextGridAct.putValue(PhonUIAction.NAME, "Generate TextGrid");
//				generateTextGridAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Generate TextGrid for record.");
//				
//				final JButton generateTextGridBtn = new JButton(generateTextGridAct);
//				contentPane.add(generateTextGridBtn);
//				
//				contentPane.add(errLabel);
//				contentPane.add(generateTextGridBtn);
//			} else {
//				double segStart = seg.getStartValue() / 1000.0 - 0.1;
//				double segEnd = seg.getEndValue() / 1000.0 + 0.1;
//				if(segStart >= tg.getXmin() && segEnd <= tg.getXmax()) {
//					try {
//						TextGrid tg = this.tg.extractPart(segStart, segEnd, 1);
//						tg.setForgetOnFinalize(false);
//						for(long i = 1; i <= tg.numberOfTiers(); i++) {
//							try {
//								final IntervalTier tier = tg.checkSpecifiedTierIsIntervalTier(i);
//								final TextGridTierComponent tierComp = new TextGridTierComponent(tier, parent.getWavDisplay());
//								tierComp.setEnabled(isEnabled());
//								tierComp.setFont(getFont());
//								tierComp.addPropertyChangeListener(TextGridTierComponent.SELECTED_INTERVAL_PROP, selectionListener);
//								contentPane.add(tierComp);
//								tiers.add(tierComp);
//							} catch (PraatException pe) {
//								// not an interval tier
//							}
//						}
//					} catch (PraatException e) {
//						LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
//						ToastFactory.makeToast(e.getLocalizedMessage()).start(this);
//					}
//				}
//			}
//		} else {
//			final PhonUIAction generateTextGridAct = new PhonUIAction
//					(this, "onGenerateTextGrid");
//			generateTextGridAct.putValue(PhonUIAction.NAME, "Generate TextGrid");
//			generateTextGridAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Generate TextGrid for record.");
//			
//			final JButton generateTextGridBtn = new JButton(generateTextGridAct);
//			contentPane.add(generateTextGridBtn);
//		}
		
		revalidate();
		repaint();
	}
	
	@Override
	public void setEnabled(boolean enabled) {
		for(TextGridTierComponent comp:tiers) {
			comp.setEnabled(enabled);
		}
		super.setEnabled(enabled);
	}
	
	public void onGenerateTextGrid() {
		final SessionEditor editor = parent.getEditor();
		final int currentRecord = editor.getCurrentRecordIndex()+1;
		
		try {
			final RecordFilter filter = new RangeRecordFilter(editor.getSession(), "" + (currentRecord));
			
			final TextGridExportWizard wizard = new TextGridExportWizard(editor.getProject(), editor.getSession(), filter);
			wizard.showWizard();
		} catch (ParseException e) {}
		
	}
	
	@Override
	public JComponent getTierComponent() {
		return this;
	}
	
	/**
	 * Adds extra buttons to the segment panel toolbar
	 */
	private void setupToolbar() {
		PraatMenuButton menuBtn = parent.getExtension(PraatMenuButton.class);
		if(menuBtn == null) {
			menuBtn = new PraatMenuButton(parent);
			parent.getToolbar().addSeparator();
			parent.getToolbar().add(menuBtn);
			parent.putExtension(PraatMenuButton.class, menuBtn);
		}
	}
	
	/**
	 * Get the location of the audio file.
	 * 
	 */
	public File getAudioFile() {
		final SessionEditor editor = parent.getEditor();
		if(editor == null) return null;
		
		File selectedMedia = 
				MediaLocator.findMediaFile(editor.getProject(), editor.getSession());
		if(selectedMedia == null) return null;
		File audioFile = null;
		
		int lastDot = selectedMedia.getName().lastIndexOf('.');
		String mediaName = selectedMedia.getName();
		if(lastDot >= 0) {
			mediaName = mediaName.substring(0, lastDot);
		}
		if(!selectedMedia.isAbsolute()) selectedMedia = 
			MediaLocator.findMediaFile(editor.getSession().getMediaLocation(), editor.getProject(), editor.getSession().getCorpus());
		
		if(selectedMedia != null) {
			File parentFile = selectedMedia.getParentFile();
			audioFile = new File(parentFile, mediaName + ".wav");
			
			if(!audioFile.exists()) {
				audioFile = null;
			}
		}
		return audioFile;
	}
	
	private String loadTextGridTemplate() throws IOException {
		final ClassLoader cl = getClass().getClassLoader();
		final InputStream is = cl.getResourceAsStream(OPEN_TEXTGRID_TEMPLATE);
		if(is == null) return new String();
		
		final StringBuilder sb = new StringBuilder();
		final BufferedReader in = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		String line = null;
		while((line = in.readLine()) != null) {
			sb.append(line + "\n");
		}
		is.close();
		
		return sb.toString();
	}
	
	private final Map<Record, PraatScriptTcpServer> serverMap = 
			Collections.synchronizedMap(new HashMap<Record, PraatScriptTcpServer>());
	/**
	 * Send TextGrid to Praat.
	 */
	public void openTextGrid() {
		File mediaFile =
				getAudioFile();
		if(mediaFile == null) return;

		final SessionEditor model = parent.getEditor();
		final Tier<MediaSegment> segmentTier = model.currentRecord().getSegment();
		
		if(segmentTier.numberOfGroups() == 0) return;

		final MediaSegment media = segmentTier.getGroup(0);
		File tgFile = tgManager.defaultTextGridFile(model.getSession().getCorpus(), model.getSession().getName());
		String tgPath = (tgFile != null ? tgFile.getAbsolutePath() : "");
		
		final PraatScriptContext map = new PraatScriptContext();
		final PraatScriptTcpServer server = new PraatScriptTcpServer();
		server.setHandler(new TextGridTcpHandler(model.currentRecord()));
		
		map.put("replyToPhon", Boolean.TRUE);
		map.put("socket", server.getPort());
		map.put("audioPath", mediaFile.getAbsolutePath());
		map.put("textGridPath", tgPath);
		map.put("segment", media);
		
		try {
			serverMap.put(parent.getEditor().currentRecord(), server);
			lock(server);
			server.startServer();
			final PraatScript ps = new PraatScript(loadTextGridTemplate());
			final String script = ps.generateScript(map);
			final String err = SendPraat.sendpraat(null, "Praat", 0, script);
			if(err != null && err.length() > 0) {
				final Toast toast = ToastFactory.makeToast("Praat error: " + err);
				toast.start(this);
			}
		} catch (IOException e) {
			server.stop();
			ToastFactory.makeToast(e.getLocalizedMessage()).start();
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	private void lock(PraatScriptTcpServer server) {
		setEnabled(false);
		
		final JButton forceUnlockBtn = new JButton();
		final PhonUIAction forceUnlockAct = new PhonUIAction(this, "onForceUnlock", server);
		forceUnlockAct.putValue(PhonUIAction.NAME, "Unlock TextGrid");
		forceUnlockAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "TextGrid is open in Praat.  Use 'File' -> 'Send back to calling program' in Praat or click to unlock.");
		forceUnlockAct.putValue(PhonUIAction.SMALL_ICON, IconManager.getInstance().getIcon("emblems/emblem-readonly", IconSize.SMALL));
		forceUnlockBtn.setAction(forceUnlockAct);
		
		buttonPane.add(forceUnlockBtn);
		buttonPane.revalidate();
	}
	
	public void onForceUnlock(PhonActionEvent pae) {
		setEnabled(true);
		
		// stop server
		final PraatScriptTcpServer server = (PraatScriptTcpServer)pae.getData();
		server.stop();
		
		serverMap.remove(parent.getEditor().currentRecord());
		
		buttonPane.remove((JButton)pae.getActionEvent().getSource());
		buttonPane.revalidate();
	}
	
	private void unlock() {
		setEnabled(true);
		buttonPane.removeAll();
	}
	
	private void update() {
		repaint();
	}
	
	@RunOnEDT
	public void onRecordChanged(EditorEvent ee) {
		update();
	}
	
	@RunOnEDT
	public void onTextGridChanged(EditorEvent ee) {
		if(ee.getEventData() != null && ee.getEventData() instanceof Integer) {
			final int rIdx = (Integer)ee.getEventData();
			if(rIdx == parent.getEditor().getCurrentRecordIndex())
				update();
		}
	}
	
	@RunOnEDT
	public void onTierChanged(EditorEvent ee) {
		if(ee.getEventData() != null && ee.getEventData().equals(SystemTierType.Segment.getName())) {
			update();
		}
	}
	
	public void onSendPraat() {
		final SendPraatDialog dlg = new SendPraatDialog(parent.getEditor());
		dlg.pack();
		
		dlg.centerWindow();
		dlg.setVisible(true);
	}
	
	public void onToggleTextGrid() {
		showTextGrid = !showTextGrid;
		PrefHelper.getUserPreferences().putBoolean(SHOW_TEXTGRID_PROP, showTextGrid);
		TextGridViewer.this.setVisible(showTextGrid);
		if(showTextGrid) update();
	}
	
	public void onPlayInterval() {
		parent.getWavDisplay().play();
	}
	
	@Override
	public void onRefresh() {
		update();
	}
	
	@Override
	public void addMenuItems(JMenu menu) {
		JMenu praatMenu = null;
		for(int i = 0; i < menu.getItemCount(); i++) {
			if(menu.getItem(i) != null && menu.getItem(i).getText() != null 
					&& menu.getItem(i).getText().equals("Praat")) {
				praatMenu = (JMenu)menu.getItem(i);
			}
		}
		if(praatMenu == null) {
			praatMenu = new JMenu("Praat");
			praatMenu.setIcon(IconManager.getInstance().getIcon("apps/praat", IconSize.SMALL));
			menu.addSeparator();
			menu.add(praatMenu);
		} else {
			praatMenu.addSeparator();
		}
		
		final PhonUIAction toggleAct = new PhonUIAction(this, "onToggleTextGrid");
		toggleAct.putValue(PhonUIAction.NAME, "Show TextGrid");
		toggleAct.putValue(PhonUIAction.SELECTED_KEY, TextGridViewer.this.isVisible());
		final JCheckBoxMenuItem toggleItem = new JCheckBoxMenuItem(toggleAct);
		praatMenu.add(toggleItem);
		
		final PhonUIAction openTextGridAct = new PhonUIAction(this, "openTextGrid");
		openTextGridAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Open TextGrid in an open instance of Praat");
		openTextGridAct.putValue(PhonUIAction.NAME, "Open TextGrid in Praat");
		praatMenu.add(openTextGridAct);
		
		final PhonUIAction genTextGridAct = new PhonUIAction(this, "onGenerateTextGrid");
		genTextGridAct.putValue(PhonUIAction.NAME, "Generate TextGrid...");
		genTextGridAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Generate TextGrid for current record...");
		praatMenu.add(genTextGridAct);
		
		praatMenu.addSeparator();
		
		final PhonUIAction sendPraatAct = new PhonUIAction(this, "onSendPraat");
		sendPraatAct.putValue(PhonUIAction.NAME, "SendPraat...");
		sendPraatAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Execute Praat script...");
		praatMenu.add(sendPraatAct);
	}

	private class TextGridMouseListener extends MouseInputAdapter {

		@Override
		public void mousePressed(MouseEvent e) {
			long tierIdx = tierForPoint(e.getPoint());
			
			if(tg != null && tierIdx > 0 && tierIdx <= tg.numberOfTiers()) {
				try {
					IntervalTier intervalTier = tg.checkSpecifiedTierIsIntervalTier(tierIdx);
					long intervalIdx = 
							intervalTier.timeToLowIndex(parent.getWavDisplay().viewToModel(e.getX()));
					
					if(intervalIdx > 0 && intervalIdx <= intervalTier.numberOfIntervals()) {
						final TextInterval interval = intervalTier.interval(intervalIdx);
						
						if(interval != null) {
							// set selection to interval
							final PCMSegmentView wavDisplay = parent.getWavDisplay();
							wavDisplay.setValuesAdusting(true);
							wavDisplay.setSelectionStart((float)interval.getXmin());
							wavDisplay.setSelectionLength(0.0f);
							wavDisplay.setValuesAdusting(false);
							wavDisplay.setSelectionLength((float)(interval.getXmax()-interval.getXmin()));
							requestFocus();
						}
					}
				} catch (PraatException pe) {
					// do nothing
				}
			}
		}
		
		private long tierForPoint(Point p) {
			long retVal = -1;
			
			if(tg != null) {
				retVal = (long)(p.getY() / 50) + 1;
			}
			
			return retVal;
		}
		
	}
	
	private class TextGridContentPanel extends JPanel {

		private static final long serialVersionUID = 1370029937245278277L;
		
		public TextGridContentPanel() {
			super();
			
			addMouseListener(new TextGridMouseListener());
			
			setFont(FontPreferences.getUIIpaFont());
		}
		
		@Override
		public Dimension getPreferredSize() {
			Dimension retVal = super.getPreferredSize();
			
			if(tg != null) {
				retVal.height = 50 * (int)tg.numberOfTiers();
			}
			
			return retVal;
		}
		
		@Override
		public void paintComponent(Graphics g) {
			final Graphics2D g2 = (Graphics2D)g;
			
			g2.setColor(parent.getWavDisplay().getBackground());
			g2.fillRect(0, 0, getWidth(), getHeight());
			g2.setColor(parent.getWavDisplay().getExcludedColor());
			g2.fillRect(0, 0, getWidth(), getHeight());
			
			final PCMSegmentView wavDisplay = parent.getWavDisplay();
			final int height = getHeight();
			
			final double segX1 = wavDisplay.modelToView(wavDisplay.getSegmentStart());
			final double segX2 = 
							wavDisplay.modelToView(wavDisplay.getSegmentStart()+wavDisplay.getSegmentLength());
			
			final Rectangle2D contentRect = new Rectangle2D.Double(
					segX1, 0, segX2-segX1, height);
			
			if((int)contentRect.getWidth() <= 0
					|| (int)contentRect.getHeight() <= 0) {
				return;
			}
			
			if(tg != null) {
				// get text grid for visible rect
				double windowStart = parent.getWavDisplay().getWindowStart();
				double windowEnd = windowStart + parent.getWavDisplay().getWindowLength();
				
				try {
					TextGrid textGrid = tg.extractPart(windowStart, windowEnd, 1);
					tgPainter.paint(textGrid, g2, getBounds());
				} catch (PraatException e) {
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
			
			// paint selection
			if(wavDisplay.hasSelection()) {
				double x1 = wavDisplay.modelToView(wavDisplay.getSelectionStart());
				double x2 = wavDisplay.modelToView(wavDisplay.getSelectionStart()+wavDisplay.getSelectionLength());
				
				Rectangle2D selRect = new Rectangle2D.Double(x1, contentRect.getY(), x2-x1, 
								contentRect.getHeight());
				
				g2.setColor(parent.getWavDisplay().getSelectionColor());
				g2.fill(selRect);
			}
		}
		
	}
	
	private class TextGridTcpHandler implements PraatScriptTcpHandler {
		
		private Record record;
		
		public TextGridTcpHandler(Record record) {
			this.record = record;
		}
		
		@Override
		public void praatScriptFinished(String data) {
			final Record currentRecord = parent.getEditor().currentRecord();
			serverMap.remove(record);
			if(currentRecord == record) {
				unlock();
				
				// grab new TextGrid from default praat save location
				final File tgFile = new File(
						PraatDir.getPath() + File.separator + "praat_backToCaller.Data");
				if(tgFile.exists() && tgFile.isFile()) {
					
					try {
						TextGrid tg = Data.readFromFile(TextGrid.class, MelderFile.fromPath(tgFile.getAbsolutePath()));
						final SessionEditor model = parent.getEditor();
						tgManager.saveTextGrid(tg, model.currentRecord().getUuid().toString());
					} catch (PraatException e) {
						LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
					}
					
				}
				
				update();
			}
		}
	}
	
}
