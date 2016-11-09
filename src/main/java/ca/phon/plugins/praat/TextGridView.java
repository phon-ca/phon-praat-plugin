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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ActionMap;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.event.MouseInputAdapter;

import org.jdesktop.swingx.VerticalLayout;
import org.jdesktop.swingx.action.ActionManager;

import ca.hedlund.jpraat.TextGridUtils;
import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.IntervalTier;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.binding.sys.Daata;
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
import ca.phon.plugins.praat.importer.TextGridImportWizard;
import ca.phon.plugins.praat.painters.TextGridPainter;
import ca.phon.plugins.praat.script.PraatScript;
import ca.phon.plugins.praat.script.PraatScriptContext;
import ca.phon.plugins.praat.script.PraatScriptTcpHandler;
import ca.phon.plugins.praat.script.PraatScriptTcpServer;
import ca.phon.session.MediaSegment;
import ca.phon.session.Session;
import ca.phon.session.SystemTierType;
import ca.phon.session.Tier;
import ca.phon.ui.CommonModuleFrame;
import ca.phon.ui.HidablePanel;
import ca.phon.ui.action.PhonActionEvent;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.fonts.FontPreferences;
import ca.phon.ui.layout.ButtonBarBuilder;
import ca.phon.ui.nativedialogs.FileFilter;
import ca.phon.ui.nativedialogs.NativeDialogEvent;
import ca.phon.ui.nativedialogs.NativeDialogs;
import ca.phon.ui.nativedialogs.OpenDialogProperties;
import ca.phon.ui.toast.ToastFactory;
import ca.phon.util.FileUtil;
import ca.phon.util.OpenFileLauncher;
import ca.phon.util.PrefHelper;
import ca.phon.util.icons.IconManager;
import ca.phon.util.icons.IconSize;

/**
 * Display a TextGrid as a vertical list of tiers.
 */
public class TextGridView extends JPanel implements SpeechAnalysisTier {
	
	/*
	 * Editor event
	 * data - String name of textGrid
	 */
	public static final String TEXT_GRID_CHANGED_EVENT = 
			TextGridView.class.getName() + ".textGridChangedEvent";
	
	private static final Logger LOGGER = Logger
			.getLogger(TextGridView.class.getName());
	
	private static final long serialVersionUID = -4777676504641976886L;
	
	private final static String OPEN_TEXTGRID_TEMPLATE = "ca/phon/plugins/praat/OpenTextGrid.vm";
	
	private TextGridManager tgManager;
	
	private TextGrid tg;
	
	private String currentTextGridName = null;
	
	// parent panel
	private SpeechAnalysisEditorView parent;
	
	private TextGridContentPanel contentPane;
	
	private TextGridPainter tgPainter = new TextGridPainter();
	
	private JPanel buttonPane;
	
	public final static String SHOW_TEXTGRID_PROP = TextGridView.class.getName() + ".showTextGrid";
	private boolean showTextGrid = 
			PrefHelper.getBoolean(SHOW_TEXTGRID_PROP, false);
	
	public final static String SHOW_TIER_LABELS_PROP = TextGridView.class.getName() + ".showTierLabels";
	private boolean showTierLabels = 
			PrefHelper.getBoolean(SHOW_TIER_LABELS_PROP, true);
	
	private final HidablePanel textGridMessage = new HidablePanel("TextGridView.message");
	
	public TextGridView(SpeechAnalysisEditorView parent) {
		super();
		setVisible(showTextGrid);
		setFocusable(true);
		
		this.parent = parent;
		
		init();
		
		// setup toolbar buttons
		setupToolbar();
		
		setupEditorActions();
		installKeyStrokes(parent);
	}
	
	private void init() {
		buttonPane = new JPanel(new VerticalLayout());
		contentPane = new TextGridContentPanel();
		contentPane.setLayout(new BorderLayout());
		
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.SELECTION_LENGTH_PROP, tierUpdater);
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.WINDOW_START_PROT, tierUpdater);
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.WINDOW_LENGTH_PROP, tierUpdater);
		
		setLayout(new BorderLayout());
		contentPane.add(buttonPane, BorderLayout.NORTH);
		add(contentPane, BorderLayout.CENTER);
		
		tgPainter.setPaintTierLabels(showTierLabels);
		
		tgManager = new TextGridManager(parent.getEditor().getProject());
		// load default TextGrid
		final File defaultTextGridFile = tgManager.defaultTextGridFile(parent.getEditor().getSession().getCorpus(),
				parent.getEditor().getSession().getName());
		if(defaultTextGridFile != null) {
			try {
				final TextGrid tg = TextGridManager.loadTextGrid(defaultTextGridFile);
				currentTextGridName = tgManager.defaultTextGridName(parent.getEditor().getSession().getCorpus(),
						parent.getEditor().getSession().getName());
				setTextGrid(tg);
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
				ToastFactory.makeToast(e.getLocalizedMessage()).start(this);
			}
		} else {
			setTextGrid(null);
			
			textGridMessage.setTopLabelText("<html><b>No TextGrid found</b></html>");
			boolean hasRecordTextGrids = hasOldTextGridFiles();
			if(hasRecordTextGrids) {
				// Deal with older TextGrid setups.  Check to see if there are TextGrids available for the
				// individual records of a session.  If so, show a message prompting the user to 'merge'
				// the old TextGrid files.
				
				final PhonUIAction mergeAct = new PhonUIAction(this, "mergeOldTextGrids");
				mergeAct.putValue(PhonUIAction.NAME, "Merge TextGrids");
				mergeAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Merge older TextGrid files.");
				mergeAct.putValue(PhonUIAction.SMALL_ICON, IconManager.getInstance().getIcon("actions/folder_import", IconSize.SMALL));
				mergeAct.putValue(PhonUIAction.LARGE_ICON_KEY, IconManager.getInstance().getIcon("actions/folder_import", IconSize.SMALL));
				
				textGridMessage.setDefaultAction(mergeAct);
				textGridMessage.addAction(mergeAct);
				
				textGridMessage.setBottomLabelText("<html>TextGrid files in an older format were found for this Session.  "
						+ " Click here to merge these files as the default TextGrid.");
			} else {
				// show a message to open the Generate TextGrid wizard
				final PhonUIAction generateAct = new PhonUIAction(this, "onGenerateTextGrid");
				generateAct.putValue(PhonUIAction.NAME, "Generate TextGrid");
				generateAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Generate TextGrid for Session");
				generateAct.putValue(PhonUIAction.LARGE_ICON_KEY, IconManager.getInstance().getIcon("actions/folder_import", IconSize.SMALL));
				
				textGridMessage.setDefaultAction(generateAct);
				textGridMessage.addAction(generateAct);
				
				textGridMessage.setBottomLabelText("<html>Click here to generate a TextGrid.</html>");
			}
			parent.getErrorPane().add(textGridMessage);
		}
	}
	
	@Deprecated
	private boolean hasOldTextGridFiles() {
		// deprecated as scanning for old TextGrids is expensive (i.e., it loads all record data during 
		// editor startup)
		return false;
		
		/*boolean hasRecordTextGrids = false;
		for(Record r:parent.getEditor().getSession().getRecords()) {
			final String recordTgPath = tgManager.textGridPath(r.getUuid().toString());
			final File recordTgFile = new File(recordTgPath);
			if(recordTgFile.exists()) {
				hasRecordTextGrids = true;
				break;
			}
		}
		return hasRecordTextGrids;*/
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
	
	private void installKeyStrokes(SpeechAnalysisEditorView p) {
		final InputMap inputMap = p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		final ActionMap actionMap = p.getActionMap();
		
		final String toggleTextGridId = "onToggleTextGrid";
		final PhonUIAction toggleTextGridAct = new PhonUIAction(this, toggleTextGridId);
		actionMap.put(toggleTextGridId, toggleTextGridAct);
		final KeyStroke toggleTextGridKs = KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.SHIFT_MASK);
		inputMap.put(toggleTextGridKs, toggleTextGridId);
		
		p.setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, inputMap);
		p.setActionMap(actionMap);
	}
	
	public void setTextGrid(TextGrid tg) {
		this.tg = tg;
		updateHiddenTiers();
		setupTextGrid();
	}
	
	public TextGrid getTextGrid() {
		return this.tg;
	}
	
	public TextGridPainter getTextGridPainter() {
		return this.tgPainter;
	}
	
	private final PropertyChangeListener tierUpdater = new PropertyChangeListener() {
		
		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			tgPainter.setRepaintBuffer(true);
			repaint();
		}
		
	};
	
	
	private void setupTextGrid() {
		tgPainter.setRepaintBuffer(true);
		contentPane.revalidate();
		contentPane.repaint();
	}
	
	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		contentPane.setEnabled(enabled);
	}
	
	public void mergeOldTextGrids() {
		final Session session = parent.getEditor().getSession();
		try {
			final TextGrid mergedTextGrid = tgManager.mergeTextGrids(session);
			tgManager.saveTextGrid(session.getCorpus(), session.getName(),
					mergedTextGrid, TextGridManager.DEFAULT_TEXTGRID_NAME);
			
			contentPane.removeAll();
			contentPane.add(buttonPane, BorderLayout.NORTH);
			currentTextGridName = tgManager.defaultTextGridName(session.getCorpus(), session.getName());
			setTextGrid(mergedTextGrid);
		} catch (IOException e) {
			ToastFactory.makeToast(e.getLocalizedMessage()).start(this);
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	public void onGenerateTextGrid() {
		final SessionEditor editor = parent.getEditor();
		
		final TextGridExportWizard wizard = new TextGridExportWizard(editor.getProject(), editor.getSession());
		wizard.showWizard();
	}
	
	@Override
	public JComponent getTierComponent() {
		return this;
	}
	
	/**
	 * Adds extra buttons to the segment panel toolbar
	 */
	private void setupToolbar() {
		final PhonUIAction menuAct = new PhonUIAction(this, "onShowTextGridMenu");
		menuAct.putValue(PhonUIAction.NAME, "TextGrid");
		menuAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Show TextGrid menu");
		menuAct.putValue(PhonUIAction.SMALL_ICON, IconManager.getInstance().getIcon("apps/praat", IconSize.SMALL));
		
		final JButton textGridBtn = new JButton(menuAct);
		parent.getToolbar().add(textGridBtn);
	}

	public void onShowTextGridMenu(PhonActionEvent pae) {
		final JButton btn = (JButton)pae.getActionEvent().getSource();
		final JMenu praatMenu = new JMenu("Praat");
		final JMenu textGridMenu = new JMenu("TextGrid");
		praatMenu.add(textGridMenu);
		addMenuItems(praatMenu, true);
		
		textGridMenu.getPopupMenu().show(btn, 0, btn.getHeight());
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
	
	/**
	 * Show text grid
	 * 
	 * @param textGridName
	 */
	public void showTextGrid(String textGridName) {
		final TextGridManager manager = new TextGridManager(parent.getEditor().getProject());
		try {
			final TextGrid textGrid = manager.openTextGrid(parent.getEditor().getSession().getCorpus(), 
					parent.getEditor().getSession().getName(), textGridName);
			if(textGrid != null) {
				currentTextGridName = textGridName;
				setTextGrid(textGrid);
				
				// lock textgrid if necessary
				if(serverMap.containsKey(textGridName)) {
					lock(textGridName);
				} else {
					unlock();
				}
			}
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			ToastFactory.makeToast(e.getLocalizedMessage()).start(this);
		}
	}
	
	private final Map<String, PraatScriptTcpServer> serverMap = 
			Collections.synchronizedMap(new HashMap<String, PraatScriptTcpServer>());
	/**
	 * Send TextGrid to Praat.
	 */
	public void openTextGrid(boolean useFullAudio) {
		File mediaFile =
				getAudioFile();
		if(mediaFile == null) return;
		
		// don't open if contentpane is current disabled (locked)
		if(!contentPane.isEnabled()) return;
		
		final SessionEditor model = parent.getEditor();
		final Tier<MediaSegment> segmentTier = model.currentRecord().getSegment();
		
		if(segmentTier.numberOfGroups() == 0) return;

		final MediaSegment media = segmentTier.getGroup(0);
		
		final Session session = parent.getEditor().getSession();
		
		final String tgName = (currentTextGridName != null ? currentTextGridName : 
			tgManager.defaultTextGridName(session.getCorpus(), session.getName()));
		File tgFile = new File(tgManager.textGridPath(session.getCorpus(), session.getName(), tgName));
		String tgPath = (tgFile != null ? tgFile.getAbsolutePath() : "");
		
		final PraatScriptContext map = new PraatScriptContext();
		final PraatScriptTcpServer server = new PraatScriptTcpServer();
		server.setHandler(new TextGridTcpHandler(tgName));
		
		map.put("replyToPhon", Boolean.TRUE);
		map.put("socket", server.getPort());
		map.put("audioPath", mediaFile.getAbsolutePath());
		map.put("textGridPath", tgPath);
		map.put("textGridName", tgName);
		map.put("segment", media);
		map.put("useFullAudio", useFullAudio);
		
		try {
			server.startServer();
			lock(tgName);
			serverMap.put(tgName, server);
			final PraatScript ps = new PraatScript(loadTextGridTemplate());
			final String script = ps.generateScript(map);
			final String err = SendPraat.sendpraat(null, "Praat", 0, script);
			if(err != null && err.length() > 0) {
				throw new IOException(err);
			}
		} catch (IOException e) {
			server.stop();
			unlock();
			serverMap.remove(tgName);
			Toolkit.getDefaultToolkit().beep();
			ToastFactory.makeToast(e.getLocalizedMessage()).start(parent.getToolbar());
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	private void lock(String textGridName) {
		contentPane.setEnabled(false);
		
		final JButton forceUnlockBtn = new JButton();
		final PhonUIAction forceUnlockAct = new PhonUIAction(this, "onForceUnlock", textGridName);
		forceUnlockAct.putValue(PhonUIAction.NAME, "Unlock TextGrid");
		forceUnlockAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "TextGrid is open in Praat.  Use 'File' -> 'Send back to calling program' in Praat or click to unlock.");
		forceUnlockAct.putValue(PhonUIAction.SMALL_ICON, IconManager.getInstance().getIcon("emblems/emblem-readonly", IconSize.SMALL));
		forceUnlockBtn.setAction(forceUnlockAct);
		
		buttonPane.add(forceUnlockBtn);

		revalidate();
		contentPane.repaint();
	}
	
	public void onForceUnlock(PhonActionEvent pae) {
		contentPane.setEnabled(true);
		
		final String textGridName = (pae.getData() != null ? pae.getData().toString() : "");
		
		// stop server
		final PraatScriptTcpServer server = serverMap.get(textGridName);
		
		if(server != null) {
			server.stop();
			serverMap.remove(textGridName);
		}
		
		buttonPane.remove((JButton)pae.getActionEvent().getSource());
		
		revalidate();
		contentPane.repaint();
	}
	
	private void unlock() {
		contentPane.setEnabled(true);
		buttonPane.removeAll();
		
		revalidate();
		contentPane.repaint();
	}
	
	private void update() {
		contentPane.repaint();
	}
	
	@RunOnEDT
	public void onRecordChanged(EditorEvent ee) {
		update();
	}
	
	@RunOnEDT
	public void onTextGridChanged(EditorEvent ee) {
		if(ee.getEventData() != null && ee.getEventData() instanceof String) {
			final String name = ee.getEventData().toString();
		
			// load TextGrid
			final Session session = parent.getEditor().getSession();
			try {
				contentPane.removeAll();
				textGridMessage.setVisible(false);
				contentPane.add(buttonPane, BorderLayout.NORTH);
				
				final TextGrid tg = tgManager.openTextGrid(session.getCorpus(), session.getName(), name);
				currentTextGridName = name;
				setTextGrid(tg);
			} catch (IOException e) {
				ToastFactory.makeToast(e.getLocalizedMessage()).start(this);
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
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
	
	public void onCreateRecordsFromTextGrid() {
		final TextGridImportWizard wizard = new TextGridImportWizard(parent.getEditor());
		Dimension size = new Dimension(600, 600);
		Dimension screenSize =
			Toolkit.getDefaultToolkit().getScreenSize();
		
		int posX = screenSize.width / 2 - size.width / 2;
		int posY = screenSize.height / 2 - size.height / 2;
		
		wizard.setBounds(posX, posY, size.width, size.height);
		wizard.setVisible(true);
	}
	
	public void onAddExistingTextGrid() {
		final OpenDialogProperties props = new OpenDialogProperties();
		props.setParentWindow(CommonModuleFrame.getCurrentFrame());
		props.setCanChooseDirectories(false);
		props.setCanChooseFiles(true);
		props.setAllowMultipleSelection(false);
		final FileFilter filter = new FileFilter("TextGrid files", "TextGrid");
		props.setFileFilter(filter);
		
		props.setListener( (e) -> {
			if(e.getDialogResult() == NativeDialogEvent.OK_OPTION) {
				final String tgPath = (String)e.getDialogData();
				final Session session = parent.getEditor().getSession();
				
				final File tgFile = new File(tgPath);
				
				int dotIdx = tgFile.getName().lastIndexOf('.');
				final String tgName = tgFile.getName().substring(0, dotIdx);
				
				final File newFile = new File(tgManager.textGridPath(session.getCorpus(), session.getName(), tgName));
				
				// attempt to copy file
				try {
					if(!newFile.getParentFile().exists() && !newFile.getParentFile().mkdirs()) {
						throw new IOException("Unable to create TextGrid folder");
					}
					FileUtil.copyFile(tgFile, newFile);
					// select new TextGrid
//					showTextGrid(tgName);
					onTextGridChanged(new EditorEvent(TextGridView.TEXT_GRID_CHANGED_EVENT, this, tgName));
				} catch (IOException ex) {
					LOGGER.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
				}
			}
		});
		NativeDialogs.showOpenDialog(props);
	}
	
	public void onShowTextGridFolder() {
		final Session session = parent.getEditor().getSession();
		try {
			final File textGridFolder = new File(tgManager.textGridFolder(session.getCorpus(), session.getName()));
			if(!textGridFolder.exists() && !textGridFolder.mkdirs()) {
				throw new IOException("Unable to create TextGrid folder");
			}
			OpenFileLauncher.openURL(textGridFolder.toURI().toURL());
		} catch (IOException e) {
			ToastFactory.makeToast(e.getLocalizedMessage()).start(parent.getToolbar());
		}
	}
	
	public void onToggleTextGrid() {
		showTextGrid = !showTextGrid;
		PrefHelper.getUserPreferences().putBoolean(SHOW_TEXTGRID_PROP, showTextGrid);
		TextGridView.this.setVisible(showTextGrid);
		if(showTextGrid) update();
	}
	
	public void onToggleTextGridLabels() {
		showTierLabels = !showTierLabels;
		PrefHelper.getUserPreferences().putBoolean(SHOW_TIER_LABELS_PROP, showTierLabels);
		tgPainter.setPaintTierLabels(showTierLabels);
		tgPainter.setRepaintBuffer(true);
		update();
	}
	
	public void onShowHideTiers() {
		final JDialog dialog = new JDialog(CommonModuleFrame.getCurrentFrame(), "Show/Hide TextGrid Tiers");
		dialog.setModal(true);
		
		dialog.getContentPane().setLayout(new BorderLayout());
		dialog.getContentPane().add(new DialogHeader("Show/Hide TextGrid Tiers", 
				currentTextGridName), BorderLayout.NORTH);
		
		final ShowHideTierTable table = new ShowHideTierTable(this);
		dialog.getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
		
		final JButton okBtn = new JButton("Ok");
		okBtn.addActionListener((e) -> dialog.setVisible(false));
		dialog.getContentPane().add(ButtonBarBuilder.buildOkBar(okBtn), BorderLayout.SOUTH);
		
		dialog.pack();
		dialog.setSize(600, 500);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
		
		saveHiddenTiers();
		
		tgPainter.setRepaintBuffer(true);
		revalidate();
		repaint();
	}
	
	@RunOnEDT
	public void onToggleTier(String tierName) {
		final boolean isHidden = getTextGridPainter().isHidden(tierName);
		getTextGridPainter().setHidden(tierName, !isHidden);
		getTextGridPainter().setRepaintBuffer(true);
		revalidate();
	}
	
	private void updateHiddenTiers() {
		if(tg == null) return;
		getTextGridPainter().clearHiddenTiers();
		final String propName = parent.getEditor().getSession().getCorpus() + "." + 
				parent.getEditor().getSession().getName() + "." + currentTextGridName + ".hiddenTiers";
		
		final String hiddenTiers = 
				PrefHelper.getUserPreferences().get(propName, "");
		final List<String> tierNames = Arrays.asList(hiddenTiers.split(","));
		for(int i = 1; i <= tg.numberOfTiers(); i++) {
			final Function tier = tg.tier(i);
			if(tierNames.contains(tier.getName())) {
				getTextGridPainter().setHidden(tier.getName(), true);
			} else {
				getTextGridPainter().setHidden(tier.getName(), false);
			}
		}
	}
	
	private void saveHiddenTiers() {
		if(tg == null) return;
		final String propName = parent.getEditor().getSession().getCorpus() + "." + 
				parent.getEditor().getSession().getName() + "." + currentTextGridName + ".hiddenTiers";
		
		final StringBuffer buffer = new StringBuffer();
		for(int i = 1; i <= tg.numberOfTiers(); i++) {
			final Function tier = tg.tier(i);
			if(getTextGridPainter().isHidden(tier.getName())) {
				if(buffer.length() > 0) buffer.append(',');
				buffer.append(tier.getName());
			}
		}
		final String hiddenTiers = buffer.toString();
		
		PrefHelper.getUserPreferences().put(propName, hiddenTiers);
	}
	
	public void onPlayInterval() {
		parent.getWavDisplay().play();
	}
	
	@Override
	public void onRefresh() {
		update();
	}
	
	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if(!visible && textGridMessage.isVisible()) {
			textGridMessage.setVisible(false);
		}
	}
	
	@Override
	public void addMenuItems(JMenu menu, boolean isContextMenu) {
		JMenu praatMenu = null;
		for(int i = 0; i < menu.getItemCount(); i++) {
			if(menu.getItem(i) != null && menu.getItem(i).getText() != null 
					&& menu.getItem(i).getText().equals("TextGrid")) {
				praatMenu = (JMenu)menu.getItem(i);
			}
		}
		if(praatMenu == null) {
			praatMenu = new JMenu("TextGrid");
			praatMenu.setIcon(IconManager.getInstance().getIcon("apps/praat", IconSize.SMALL));
			menu.add(praatMenu);
		}
		
		final Session session = parent.getEditor().getSession();
		final TextGridManager manager = new TextGridManager(parent.getEditor().getProject());
		
		final PhonUIAction toggleAct = new PhonUIAction(this, "onToggleTextGrid");
		toggleAct.putValue(PhonUIAction.NAME, "Show TextGrid");
		toggleAct.putValue(PhonUIAction.SELECTED_KEY, TextGridView.this.isVisible());
		if(isContextMenu)
			toggleAct.putValue(PhonUIAction.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.SHIFT_MASK));
		final JCheckBoxMenuItem toggleItem = new JCheckBoxMenuItem(toggleAct);
		praatMenu.add(toggleItem);
		
		final ImageIcon lockIcon = IconManager.getInstance().getIcon("emblems/emblem-readonly", IconSize.SMALL);
		final ImageIcon defaultTgIcon = IconManager.getInstance().getIcon("emblems/emblem-generic", IconSize.SMALL);
		
		final JMenu textGridMenu = new JMenu("Select TextGrid");
		textGridMenu.addMenuListener(new MenuListener() {
			
			@Override
			public void menuSelected(MenuEvent e) {
				textGridMenu.removeAll();
				final String defaultTgName = manager.defaultTextGridName(session.getCorpus(), session.getName());
				for(String textGridName:manager.textGridNamesForSession(session.getCorpus(), session.getName())) {
					boolean isDefault = textGridName.equals(defaultTgName);
					boolean isCurrent = (currentTextGridName != null && textGridName.equals(currentTextGridName));
					ImageIcon icn = null;
					String menuTxt = textGridName;
					if(isDefault) {
						icn = defaultTgIcon;
					}
					if(serverMap.containsKey(textGridName)) {
						icn = lockIcon;
					}
					
					final PhonUIAction showTgAct = new PhonUIAction(TextGridView.this, "showTextGrid", textGridName);
					showTgAct.putValue(PhonUIAction.NAME, menuTxt);					
					showTgAct.putValue(PhonUIAction.SHORT_DESCRIPTION,
							tgManager.textGridPath(session.getCorpus(), session.getName(), textGridName));
					showTgAct.putValue(PhonUIAction.SELECTED_KEY, isCurrent);
					if(icn != null) {
						showTgAct.putValue(PhonUIAction.SMALL_ICON, icn);
					}
					textGridMenu.add(new JCheckBoxMenuItem(showTgAct));
				}
				textGridMenu.addSeparator();
				final PhonUIAction showTextGridFolderAct = new PhonUIAction(TextGridView.this, "onShowTextGridFolder");
				showTextGridFolderAct.putValue(PhonUIAction.NAME, "Show TextGrid folder");
				showTextGridFolderAct.putValue(PhonUIAction.SHORT_DESCRIPTION, tgManager.textGridFolder(session.getCorpus(), session.getName()));
				textGridMenu.add(showTextGridFolderAct);
				
				final PhonUIAction addExistingTextGridAct = new PhonUIAction(TextGridView.this, "onAddExistingTextGrid");
				addExistingTextGridAct.putValue(PhonUIAction.NAME, "Add existing TextGrid...");
				addExistingTextGridAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Copy existing TextGrid to TextGrid folder");
				textGridMenu.add(addExistingTextGridAct);
			}
			
			@Override
			public void menuDeselected(MenuEvent e) {
			}
			
			@Override
			public void menuCanceled(MenuEvent e) {
			}
			
		});
		praatMenu.add(textGridMenu);
		
		final PhonUIAction toggleLabelsAct = new PhonUIAction(this, "onToggleTextGridLabels");
		toggleLabelsAct.putValue(PhonUIAction.NAME, "Show Tier Labels");
		toggleLabelsAct.putValue(PhonUIAction.SELECTED_KEY, TextGridView.this.showTierLabels);
		final JCheckBoxMenuItem toggleLabelsItem = new JCheckBoxMenuItem(toggleLabelsAct);
		praatMenu.add(toggleLabelsItem);
		
		final JMenu tgTiersMenu = new JMenu("Toggle TextGrid tiers");
		tgTiersMenu.addMenuListener(new MenuListener() {
			
			@Override
			public void menuSelected(MenuEvent e) {
				tgTiersMenu.removeAll();
				if(tg == null) return;
				
				for(int i = 1; i <= tg.numberOfTiers(); i++) {
					final Function tier = tg.tier(i);
					final boolean isHidden = getTextGridPainter().isHidden(tier.getName());
					
					final PhonUIAction toggleTierAction = new PhonUIAction(TextGridView.this, "onToggleTier", tier.getName());
					toggleTierAction.putValue(PhonUIAction.NAME, tier.getName());
					toggleTierAction.putValue(PhonUIAction.SELECTED_KEY, !isHidden);
					toggleTierAction.putValue(PhonUIAction.SHORT_DESCRIPTION, "Toggle tier " + tier.getName());
					
					tgTiersMenu.add(new JCheckBoxMenuItem(toggleTierAction));
				}
				
				tgTiersMenu.addSeparator();
				
				final PhonUIAction showHideTiersAct = new PhonUIAction(TextGridView.this, "onShowHideTiers");
				showHideTiersAct.putValue(PhonUIAction.NAME, "Show/hide TextGrid tiers...");
				tgTiersMenu.add(showHideTiersAct);
			}
			
			@Override
			public void menuDeselected(MenuEvent e) {
			}
			
			@Override
			public void menuCanceled(MenuEvent e) {
			}
		});
		praatMenu.add(tgTiersMenu);
		
		praatMenu.addSeparator();
		final PhonUIAction genTextGridAct = new PhonUIAction(TextGridView.this, "onGenerateTextGrid");
		genTextGridAct.putValue(PhonUIAction.NAME, "Generate TextGrid...");
		genTextGridAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Generate TextGrid for session...");
		praatMenu.add(genTextGridAct);
		
		PhonUIAction createRecordsAct = new PhonUIAction(TextGridView.this, "onCreateRecordsFromTextGrid");
		createRecordsAct.putValue(PhonUIAction.NAME, "Create records from TextGrid...");
		createRecordsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Create records from an existing TextGrid");
		praatMenu.add(createRecordsAct);
		
		// sendpraat menu items
		praatMenu.addSeparator();
		PhonUIAction openTextGridAct = new PhonUIAction(this, "openTextGrid", Boolean.TRUE);
		openTextGridAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Display TextGrid in an open instance of Praat with full audio. High memory usage.");
		openTextGridAct.putValue(PhonUIAction.NAME, "Open TextGrid in Praat - full audio");
		praatMenu.add(openTextGridAct);
		
		PhonUIAction openTextGridAct2 = new PhonUIAction(this, "openTextGrid", Boolean.FALSE);
		openTextGridAct2.putValue(PhonUIAction.SHORT_DESCRIPTION, "Display TextGrid in an open instance of Praat with segment only. Low memory usage.");
		openTextGridAct2.putValue(PhonUIAction.NAME, "Open TextGrid in Praat - segment only");
		praatMenu.add(openTextGridAct2);
		
		final PhonUIAction sendPraatAct = new PhonUIAction(this, "onSendPraat");
		sendPraatAct.putValue(PhonUIAction.NAME, "Open TextGrid in Praat - custom script...");
		sendPraatAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Open TextGrid and audio in Praat and execute Praat script...");
		praatMenu.add(sendPraatAct);
	}
	
//	@Override
//	public void addMenuItems(JMenu menu) {
//		JMenu praatMenu = null;
//		for(int i = 0; i < menu.getItemCount(); i++) {
//			if(menu.getItem(i) != null && menu.getItem(i).getText() != null 
//					&& menu.getItem(i).getText().equals("TextGrid")) {
//				praatMenu = (JMenu)menu.getItem(i);
//			}
//		}
//		if(praatMenu == null) {
//			praatMenu = new JMenu("TextGrid");
//			praatMenu.setIcon(IconManager.getInstance().getIcon("apps/praat", IconSize.SMALL));
//			menu.addSeparator();
//			menu.add(praatMenu);
//		}
//		
//		final Session session = parent.getEditor().getSession();
//		final TextGridManager manager = new TextGridManager(parent.getEditor().getProject());
//		
//		
//		
//		
//		
//		
//		
//		final JMenu textGridMenu = new JMenu("TextGrids");
//		textGridMenu.addMenuListener(new MenuListener() {
//			
//			@Override
//			public void menuSelected(MenuEvent e) {
//				
//				
//				if(textGridMenu.getItemCount() > 0) {
//					textGridMenu.addSeparator();
//				} else {
//					// add an item to merge previous textgrids
//					if(hasOldTextGridFiles()) {
//						final PhonUIAction mergeAct = new PhonUIAction(TextGridView.this, "mergeOldTextGrids");
//						mergeAct.putValue(PhonUIAction.NAME, "Merge TextGrids");
//						mergeAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Merge older TextGrid files.");
//						textGridMenu.add(mergeAct);
//					}
//				}
//				
//			}
//			
//			@Override
//			public void menuDeselected(MenuEvent e) {
//				
//			}
//			
//			@Override
//			public void menuCanceled(MenuEvent e) {
//				
//			}
//		});
//		praatMenu.add(textGridMenu);
//		
//		
//		praatMenu.addSeparator();
//		
//		
//	}

	private class TextGridMouseListener extends MouseInputAdapter {

		@Override
		public void mousePressed(MouseEvent e) {
			if(!contentPane.isEnabled()) return;
			if(tg == null) return;
			
			if(e.getButton() != MouseEvent.BUTTON1
					&& e.getButton() != MouseEvent.BUTTON3) return;
			
			int tierNum = tierForPoint(e.getPoint());
			String tierName = (tierNum >= 0 && tierNum < getVisibleTiers().size() ? 
					getVisibleTiers().get(tierNum) : null);
			long tierIdx = TextGridUtils.tierNumberFromName(tg, tierName);
			
			if(tierIdx > 0 && tierIdx <= tg.numberOfTiers()) {
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
			// dispatch event to parent listeners
			TextGridView.this.dispatchEvent(e);
		}
		
		@Override
		public void mouseReleased(MouseEvent me) {
			// dispatch event to parent container
			TextGridView.this.dispatchEvent(me);
		}
		
		private int tierForPoint(Point p) {
			int retVal = -1;
			
			if(tg != null) {
				retVal = (int)(p.getY() / 50);
			}
			
			return retVal;
		}
		
	}
	
	public List<String> getVisibleTiers() {
		final List<String> visibleTiers = new ArrayList<>();
		
		for(long i = 1; i <= tg.numberOfTiers(); i++) {
			final Function tier = tg.tier(i);
			if(!tgPainter.isHidden(tier.getName())) {
				visibleTiers.add(tier.getName());
			}
		}
		
		return visibleTiers;
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
				retVal.height = 50 * getVisibleTiers().size();
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
			final int width = getWidth();
			final int height = getHeight();
			
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
			if(isEnabled() && wavDisplay.hasSelection()) {
				double x1 = wavDisplay.modelToView(wavDisplay.getSelectionStart());
				double x2 = wavDisplay.modelToView(wavDisplay.getSelectionStart()+wavDisplay.getSelectionLength());
				
				Rectangle2D selRect = new Rectangle2D.Double(x1, 0, x2-x1, 
								height);
				
				g2.setColor(parent.getWavDisplay().getSelectionColor());
				g2.fill(selRect);
			}
			
			if(!isEnabled()) {
				Color disabledColor = new Color(200, 200, 200, 120);
				g2.setColor(disabledColor);
				
				g2.fillRect(0, 0, width, height);
			}
		}
		
	}
	
	private class TextGridTcpHandler implements PraatScriptTcpHandler {
		
		private String textGridName;
		
		public TextGridTcpHandler(String textGridName) {
			this.textGridName = textGridName;
		}
		
		@Override
		public void praatScriptFinished(String data) {
			serverMap.remove(textGridName);
			unlock();
				
			// grab new TextGrid from default praat save location
			final File tgFile = new File(
					PraatDir.getPath() + File.separator + "praat_backToCaller.Data");
			if(tgFile.exists() && tgFile.isFile()) {
				
				try {
					TextGrid tg = Daata.readFromFile(TextGrid.class, MelderFile.fromPath(tgFile.getAbsolutePath()));
					final SessionEditor model = parent.getEditor();
					tgManager.saveTextGrid(model.getSession().getCorpus(), model.getSession().getName(), tg, textGridName);
					
					if(currentTextGridName.equals(textGridName)) {
						tgPainter.setRepaintBuffer(true);
						setTextGrid(tg);
					}
				} catch (PraatException | IOException e) {
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
					ToastFactory.makeToast("Unable to update TextGrid!").start(TextGridView.this);
				}
				
			}
			
			update();
		}
	}
	
}
