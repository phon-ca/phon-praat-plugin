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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.event.MouseInputAdapter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.apache.commons.io.FilenameUtils;
import org.jdesktop.swingx.HorizontalLayout;
import org.jdesktop.swingx.VerticalLayout;

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
import ca.phon.opgraph.app.components.DoubleClickableTextField;
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
import ca.phon.session.TierDescription;
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
import ca.phon.util.PrefHelper;
import ca.phon.util.Tuple;
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

	private File currentTextGridFile = null;

	// parent panel
	private SpeechAnalysisEditorView parent;

	private JLayeredPane layeredPane;
	
	private TextGridContentPanel textGridContentPane;

	private TextGridPainter tgPainter = new TextGridPainter();

	private JPanel buttonPane;

	public final static String SHOW_TEXTGRID_PROP = TextGridView.class.getName() + ".showTextGrid";
	private boolean showTextGrid =
			PrefHelper.getBoolean(SHOW_TEXTGRID_PROP, false);

	public final static String SHOW_TIER_LABELS_PROP = TextGridView.class.getName() + ".showTierLabels";
	private boolean showTierLabels =
			PrefHelper.getBoolean(SHOW_TIER_LABELS_PROP, true);

	public final static String SELECTED_TEXTGRID_PROP_PREFIX =
			"TextGridView.";
	public final static String SELECTED_TEXTGRID_PROP_SUXFFIX =
			".selectedTextGrid";

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
		setLayout(new BorderLayout());
		buttonPane = new JPanel(new VerticalLayout());
		add(buttonPane, BorderLayout.NORTH);
		
		textGridContentPane = new TextGridContentPanel() ;
		layeredPane = new JLayeredPane() {
			@Override
			public Dimension getPreferredSize() {
				return textGridContentPane.getPreferredSize();
			}
		};
		add(layeredPane, BorderLayout.CENTER);
		
		layeredPane.add(textGridContentPane, JLayeredPane.DEFAULT_LAYER);
		layeredPane.addComponentListener(new ComponentListener() {
			
			@Override
			public void componentShown(ComponentEvent e) {
			}
			
			@Override
			public void componentResized(ComponentEvent e) {
				textGridContentPane.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
			}
			
			@Override
			public void componentMoved(ComponentEvent e) {
			}
			
			@Override
			public void componentHidden(ComponentEvent e) {
			}
			
		});
		
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.SELECTION_LENGTH_PROP, tierUpdater);
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.WINDOW_START_PROT, tierUpdater);
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.WINDOW_LENGTH_PROP, tierUpdater);

		loadTextGrid();
		updateTierLabels();
	}

	public File getCurrentTextGridFile() {
		return this.currentTextGridFile;
	}

	public String getSelectedTextGridPropertyName() {
		return SELECTED_TEXTGRID_PROP_PREFIX
				+ parent.getEditor().getSession().getCorpus() + "." + parent.getEditor().getSession().getName()
				+ "." + FilenameUtils.getBaseName(parent.getEditor().getSession().getMediaLocation())
				+ SELECTED_TEXTGRID_PROP_SUXFFIX;
	}

	private void loadTextGrid() {
		tgManager = new TextGridManager(parent.getEditor().getProject());

		// load default TextGrid
		final Optional<File> defaultTextGridFile = 
				tgManager.defaultTextGridFile(parent.getEditor().getSession());
		if(defaultTextGridFile.isPresent()) {
			try {
				final TextGrid tg = TextGridManager.loadTextGrid(defaultTextGridFile.get());
				currentTextGridFile = defaultTextGridFile.get();
				setTextGrid(tg);
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
				ToastFactory.makeToast(e.getLocalizedMessage()).start(this);
			}
			if(textGridMessage.isVisible()) {
				textGridMessage.setVisible(false);
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
				generateAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Generate TextGrid tiers from Phon tiers");
				generateAct.putValue(PhonUIAction.LARGE_ICON_KEY, IconManager.getInstance().getIcon("actions/folder_import", IconSize.SMALL));

				textGridMessage.setDefaultAction(generateAct);
				textGridMessage.addAction(generateAct);

				textGridMessage.setBottomLabelText("<html>Click here to generate TextGrid tiers from Phon tiers.</html>");
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

		final EditorAction mediaChangedAct = new DelegateEditorAction(this, "onMediaChanged");
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.SESSION_MEDIA_CHANGED, mediaChangedAct);
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
		updateTierLabels();
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
		textGridContentPane.revalidate();
		textGridContentPane.repaint();
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		textGridContentPane.setEnabled(enabled);
	}

	/**
	 * Merge TextGrid files from Phon 2.0.
	 *
	 * @deprecated as of Phon 2.2
	 */
	@Deprecated
	public void mergeOldTextGrids() {
		final Session session = parent.getEditor().getSession();
		try {
			final TextGrid mergedTextGrid = tgManager.mergeTextGrids(session);
			tgManager.saveTextGrid(session.getCorpus(), session.getName(),
					mergedTextGrid, "merged");

			textGridContentPane.removeAll();
			textGridContentPane.add(buttonPane, BorderLayout.NORTH);
			final Optional<File> defaultTgFile = tgManager.defaultTextGridFile(session.getCorpus(), session.getName());
			if(defaultTgFile.isPresent())
				currentTextGridFile = defaultTgFile.get();
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

	public SpeechAnalysisEditorView getParentView() {
		return this.parent;
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
	 * @param textGridFile
	 */
	public void showTextGrid(File textGridFile) {
		final TextGridManager manager = new TextGridManager(parent.getEditor().getProject());
		try {
			final TextGrid textGrid = TextGridManager.loadTextGrid(textGridFile);
			if(textGrid != null) {
				currentTextGridFile = textGridFile;
				setTextGrid(textGrid);

				// lock textgrid if necessary
				if(serverMap.containsKey(textGridFile)) {
					lock(textGridFile);
				} else {
					unlock();
				}
			}
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			ToastFactory.makeToast(e.getLocalizedMessage()).start(this);
		}
	}

	private final Map<File, PraatScriptTcpServer> serverMap =
			Collections.synchronizedMap(new HashMap<File, PraatScriptTcpServer>());
	/**
	 * Send TextGrid to Praat.
	 */
	public void openTextGrid(boolean useFullAudio) {
		File mediaFile =
				getAudioFile();
		if(mediaFile == null) return;

		// don't open if contentpane is current disabled (locked)
		if(!textGridContentPane.isEnabled()) return;

		final SessionEditor model = parent.getEditor();
		final Tier<MediaSegment> segmentTier = model.currentRecord().getSegment();

		if(segmentTier.numberOfGroups() == 0) return;

		final MediaSegment media = segmentTier.getGroup(0);

		final Session session = parent.getEditor().getSession();

		final Optional<File> defaultTgFile = tgManager.defaultTextGridFile(session.getCorpus(), session.getName());
		final File tgFile = (currentTextGridFile != null ? currentTextGridFile : defaultTgFile.get() );
		if(tgFile == null) return;
		
		final String tgName = FilenameUtils.getBaseName(tgFile.getAbsolutePath());
		String tgPath = (tgFile != null ? tgFile.getAbsolutePath() : "");

		final PraatScriptContext map = new PraatScriptContext();
		final PraatScriptTcpServer server = new PraatScriptTcpServer();
		server.setHandler(new TextGridTcpHandler(tgFile));

		map.put("replyToPhon", Boolean.TRUE);
		map.put("socket", server.getPort());
		map.put("audioPath", mediaFile.getAbsolutePath());
		map.put("textGridPath", tgPath);
		map.put("textGridName", tgName);
		map.put("segment", media);
		map.put("useFullAudio", useFullAudio);

		try {
			server.startServer();
			lock(tgFile);
			serverMap.put(tgFile, server);
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

	private void lock(File textGridFile) {
		textGridContentPane.setEnabled(false);

		final JButton forceUnlockBtn = new JButton();
		final PhonUIAction forceUnlockAct = new PhonUIAction(this, "onForceUnlock", textGridFile);
		forceUnlockAct.putValue(PhonUIAction.NAME, "Unlock TextGrid");
		forceUnlockAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "TextGrid is open in Praat.  Use 'File' -> 'Send back to calling program' in Praat or click to unlock.");
		forceUnlockAct.putValue(PhonUIAction.SMALL_ICON, IconManager.getInstance().getIcon("emblems/emblem-readonly", IconSize.SMALL));
		forceUnlockBtn.setAction(forceUnlockAct);

		buttonPane.add(forceUnlockBtn);

		revalidate();
		textGridContentPane.repaint();
	}

	public void onForceUnlock(PhonActionEvent pae) {
		textGridContentPane.setEnabled(true);

		final File textGridFile = (File)pae.getData();

		// stop server
		final PraatScriptTcpServer server = serverMap.get(textGridFile);

		if(server != null) {
			server.stop();
			serverMap.remove(textGridFile);
		}

		buttonPane.remove((JButton)pae.getActionEvent().getSource());

		revalidate();
		textGridContentPane.repaint();
	}

	private void unlock() {
		textGridContentPane.setEnabled(true);
		buttonPane.removeAll();

		revalidate();
		textGridContentPane.repaint();
	}

	private void update() {
		textGridContentPane.repaint();
		updateTierLabels();
	}
	
	public void removeTierLabels() {
		final Component[] compList = layeredPane.getComponentsInLayer(JLayeredPane.PALETTE_LAYER);
		Arrays.stream(compList).forEach( layeredPane::remove );
	}
	
	public void updateTierLabels() {
		removeTierLabels();
		if(showTierLabels) {
			int idx = 0;
			for(String tierName:getVisibleTiers()) {
				final TierLabel tierLabel = new TierLabel(tierName);
				tierLabel.setBounds(0, idx * 50, tierLabel.getPreferredSize().width, tierLabel.getPreferredSize().height);
				layeredPane.add(tierLabel, JLayeredPane.PALETTE_LAYER);
				++idx;
			}
		}
	}

	@RunOnEDT
	public void onRecordChanged(EditorEvent ee) {
		update();
	}

	@RunOnEDT
	public void onTextGridChanged(EditorEvent ee) {
		if(ee.getEventData() != null && ee.getEventData() instanceof File) {
			final File file = (File)ee.getEventData();

			// load TextGrid
			try {
				textGridContentPane.removeAll();
				textGridMessage.setVisible(false);
				textGridContentPane.add(buttonPane, BorderLayout.NORTH);

				final TextGrid tg = TextGridManager.loadTextGrid(file);
				currentTextGridFile = file;
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

	@RunOnEDT
	public void onMediaChanged(EditorEvent ee) {
		loadTextGrid();
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
					onTextGridChanged(new EditorEvent(TextGridView.TEXT_GRID_CHANGED_EVENT, this, newFile));
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
			if(Desktop.isDesktopSupported()) {
				Desktop.getDesktop().browseFileDirectory(textGridFolder);
			}
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
		tgPainter.setRepaintBuffer(true);
		update();
	}

	public void onTierManagement() {
		final JDialog dialog = new JDialog(CommonModuleFrame.getCurrentFrame(), "TextGrid Tier Management");
		dialog.setModal(false);

		dialog.getContentPane().setLayout(new BorderLayout());
		dialog.getContentPane().add(new DialogHeader("TextGrid Tier Management",
				currentTextGridFile.getAbsolutePath()), BorderLayout.NORTH);

		final TextGridTierViewPanel tierViewPanel = new TextGridTierViewPanel(this);
		dialog.getContentPane().add(tierViewPanel, BorderLayout.CENTER);

		final JButton okBtn = new JButton("Ok");
		okBtn.addActionListener((e) -> dialog.setVisible(false));
		dialog.getContentPane().add(ButtonBarBuilder.buildOkBar(okBtn), BorderLayout.SOUTH);

		dialog.pack();
		dialog.setSize(600, 500);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	@RunOnEDT
	public void onToggleTier(String tierName) {
		final boolean isHidden = getTextGridPainter().isHidden(tierName);
		getTextGridPainter().setHidden(tierName, !isHidden);
		getTextGridPainter().setRepaintBuffer(true);
		updateTierLabels();
		saveHiddenTiers();
		revalidate();
	}

	private void updateHiddenTiers() {
		if(tg == null) return;
		getTextGridPainter().clearHiddenTiers();
		final String tgName = FilenameUtils.getBaseName(currentTextGridFile.getAbsolutePath());
		final String propName = parent.getEditor().getSession().getCorpus() + "." +
				parent.getEditor().getSession().getName() + "." + tgName + ".hiddenTiers";

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

	public void saveHiddenTiers() {
		if(tg == null) return;
		final String tgName = FilenameUtils.getBaseName(currentTextGridFile.getAbsolutePath());
		final String propName = parent.getEditor().getSession().getCorpus() + "." +
				parent.getEditor().getSession().getName() + "." + tgName + ".hiddenTiers";

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
				boolean projectHeaderAdded = false;
				for(File textGridFile:manager.textGridFilesForSession(session)) {
					final String textGridName = FilenameUtils.getBaseName(textGridFile.getAbsolutePath());
					boolean isCurrent = (currentTextGridFile != null && textGridFile.equals(currentTextGridFile));
					ImageIcon icn = null;
					String menuTxt = textGridName;

					if(serverMap.containsKey(textGridName)) {
						icn = lockIcon;
					}

					// add folder headers
					final File mediaFile =
							MediaLocator.findMediaFile(session.getMediaLocation(), getParentView().getEditor().getProject(), session.getCorpus());
					if(mediaFile != null) {
						final String mediaFolder = mediaFile.getParent();
						if(textGridFile.getParentFile().equals(new File(mediaFolder))) {
							final PhonUIAction showMediaFolderAct = new PhonUIAction(Desktop.getDesktop(), "browseFileDirectory",
									new File(mediaFolder));
							showMediaFolderAct.putValue(PhonUIAction.NAME, "-- Media Folder --");
							showMediaFolderAct.putValue(PhonUIAction.SHORT_DESCRIPTION, mediaFolder);
							final JMenuItem showMediaFolderItem = new JMenuItem(showMediaFolderAct);
							showMediaFolderItem.setFont(showMediaFolderItem.getFont().deriveFont(Font.BOLD));
							textGridMenu.add(showMediaFolderItem);
						}
					}

					final String projectFolder = tgManager.textGridFolder(session.getCorpus(), session.getName());
					if(textGridFile.getParentFile().equals(new File(projectFolder)) && !projectHeaderAdded) {
						final PhonUIAction showMediaFolderAct = new PhonUIAction(Desktop.getDesktop(), "browseFileDirectory",
								new File(projectFolder));
						showMediaFolderAct.putValue(PhonUIAction.NAME, "-- Project Folder --");
						showMediaFolderAct.putValue(PhonUIAction.SHORT_DESCRIPTION, projectFolder);
						final JMenuItem showMediaFolderItem = new JMenuItem(showMediaFolderAct);
						showMediaFolderItem.setFont(showMediaFolderItem.getFont().deriveFont(Font.BOLD));
						textGridMenu.add(showMediaFolderItem);
						projectHeaderAdded = true;
					}

					final PhonUIAction showTgAct = new PhonUIAction(TextGridView.this, "showTextGrid", textGridFile);
					showTgAct.putValue(PhonUIAction.NAME, menuTxt);
					showTgAct.putValue(PhonUIAction.SHORT_DESCRIPTION, textGridFile.getAbsolutePath());
					showTgAct.putValue(PhonUIAction.SELECTED_KEY, isCurrent);
					if(icn != null) {
						showTgAct.putValue(PhonUIAction.SMALL_ICON, icn);
					}
					textGridMenu.add(new JCheckBoxMenuItem(showTgAct));
				}
				textGridMenu.addSeparator();

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

		final PhonUIAction tierMgtAct = new PhonUIAction(TextGridView.this, "onTierManagement");
		tierMgtAct.putValue(PhonUIAction.NAME, "TextGrid Tier Management...");
		praatMenu.add(new JMenuItem(tierMgtAct));

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

	private class TextGridMouseListener extends MouseInputAdapter {

		@Override
		public void mousePressed(MouseEvent e) {
			if(!textGridContentPane.isEnabled()) return;
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
		if(tg != null) {
			for(long i = 1; i <= tg.numberOfTiers(); i++) {
				final Function tier = tg.tier(i);
				if(!tgPainter.isHidden(tier.getName())) {
					visibleTiers.add(tier.getName());
				}
			}
		}

		return visibleTiers;
	}
	
	public void destroyPopup(JFrame popup) {
		popup.setVisible(false);
		popup.dispose();
	}
	
	
	
	public void mapTier(Tuple<String, JTree> tuple) {
		final JTree tree = tuple.getObj2();
		final TreePath path = tree.getSelectionPath();
		if(path != null) {
			final DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode)path.getLastPathComponent();
			if(selectedNode.isLeaf()) {
				final String tierName = ((DefaultMutableTreeNode)selectedNode.getParent()).getUserObject().toString();
				final String segmentation = ((DefaultMutableTreeNode)selectedNode).getUserObject().toString();

				final String newName = tierName + ": " + segmentation;
				renameTier(tuple.getObj1(), newName);
			}
		}
	}
	
	private boolean renameTier(String tierName, String newName) {
		newName = newName.trim();
		if(newName.length() == 0) {
			return false;
		}
		
		Function tier = null;
		final TextGrid textGrid = getTextGrid();
		for(int i = 1; i <= textGrid.numberOfTiers(); i++) {
			final Function currentTier = textGrid.tier(i);
			if(currentTier.getName().equals(tierName)) {
				tier = currentTier;
			} else {
				if(currentTier.getName().equals(newName)) {
					// bail, name already used
					return false;
				}
			}
		}
		
		if(tier != null) {
			tier.setName(newName);
			saveTextGrid();
			
			updateTierLabels();
			repaint();
			
			return true;
		}
		return false;
	}
	
	public void saveTextGrid() {
		try {
			TextGridManager.saveTextGrid(getTextGrid(), getCurrentTextGridFile());
		} catch (IOException e) {
			Toolkit.getDefaultToolkit().beep();
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	public void onMapTier(TierLabel tierLabel) {
		final TextGridTierMapper mapper = new TextGridTierMapper(
				getParentView().getEditor().getSession(),
				getTextGrid());
		final JTree tree = new JTree(mapper.createTreeModel());
		final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)tree.getModel().getRoot();
		final TreePath rootPath = new TreePath(rootNode);
		for(int i = 0; i < rootNode.getChildCount(); i++) {
			final TreePath treePath = rootPath.pathByAddingChild(rootNode.getChildAt(i));
			tree.expandPath(treePath);
		}

		tree.setVisibleRowCount(10);
		tree.expandPath(new TreePath(tree.getModel().getRoot()));
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		final JScrollPane scroller = new JScrollPane(tree);

		final Point p = new Point(0, tierLabel.tierButton.getHeight());
		SwingUtilities.convertPointToScreen(p, tierLabel.tierButton);

		final JFrame popup = new JFrame("Map TextGrid Tier");
		popup.setUndecorated(true);
		popup.addWindowFocusListener(new WindowFocusListener() {

			@Override
			public void windowLostFocus(WindowEvent e) {
				destroyPopup(popup);
			}

			@Override
			public void windowGainedFocus(WindowEvent e) {
			}

		});

		final PhonUIAction cancelAct = new PhonUIAction(this, "destroyPopup", popup);
		cancelAct.putValue(PhonUIAction.NAME, "Cancel");
		final JButton cancelBtn = new JButton(cancelAct);

		final PhonUIAction okAct = new PhonUIAction(this, "mapTier", new Tuple<String, JTree>(tierLabel.tierName, tree));
		okAct.putValue(PhonUIAction.NAME, "Map to Selected Phon Tier and Dimension");
		final JButton okBtn = new JButton(okAct);
		okBtn.addActionListener( (e) -> {
			final TreePath selectedPath = tree.getSelectionPath();
			if(selectedPath != null) {
				final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectedPath.getLastPathComponent();
				if(treeNode.isLeaf()) {
					destroyPopup(popup);
				}
			}
		} );

		final JComponent btnBar = ButtonBarBuilder.buildOkCancelBar(okBtn, cancelBtn);

		popup.setLayout(new BorderLayout());
		popup.add(scroller, BorderLayout.CENTER);
		popup.add(btnBar, BorderLayout.SOUTH);

		popup.pack();
		popup.setLocation(p.x, p.y);
		popup.setVisible(true);

		popup.getRootPane().setDefaultButton(okBtn);
	}
	
	private class TierLabel extends JPanel {
		
		private JTextField tierNameLabel;
		private DoubleClickableTextField tierNameEditSupport;
		
		private JLabel tierButton;
		
		private final String mappedTierRegex = "([- _\\w]+)\\s?:\\s?((Tier|Group|Word|Syllable|Phone))";
		
		private String tierName;
		
		public TierLabel(String tierName) {
			super();
			
			this.tierName = tierName;
			
			init();
			
			setOpaque(true);
		}
		
		private boolean isMappedTier() {
			boolean retVal = false;
			
			final String tierName = getTierName();
			final Pattern pattern = Pattern.compile(mappedTierRegex);
			final Matcher matcher = pattern.matcher(tierName);
			
			if(matcher.matches()) {
				final String name = matcher.group(1);
				final String domain = matcher.group(2);

				boolean isUserTier = false;
				for(TierDescription td:getParentView().getEditor().getSession().getUserTiers()) {
					if(td.getName().equals(name)) {
						isUserTier = true;
						break;
					}
				}
				retVal = SystemTierType.isSystemTier(name) || isUserTier;
				
				if(domain.equals("Syllable") || domain.equals("Phone")) {
					final SystemTierType systemTier = SystemTierType.tierFromString(name);
					retVal = (systemTier != null &&  (systemTier == SystemTierType.IPATarget || systemTier == SystemTierType.IPAActual) );
				}
			}
			
			return retVal;
		}
		
		public void showTierMenu() {
			final JPopupMenu popupMenu = new JPopupMenu();
			
			// add 'Map to tier' options
			final PhonUIAction mapAct = new PhonUIAction(TextGridView.this, "onMapTier", this);
			mapAct.putValue(PhonUIAction.NAME, "Map to Phon tier...");
			mapAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Map interval values to groups/words in a Phon tier.");
			popupMenu.add(new JMenuItem(mapAct));
			popupMenu.addSeparator();

			final PhonUIAction renameAct = new PhonUIAction(tierNameEditSupport, "setEditing", Boolean.TRUE);
			renameAct.putValue(PhonUIAction.NAME, "Rename tier");
			renameAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Rename tier");
			popupMenu.add(new JMenuItem(renameAct));
			
			final PhonUIAction hideTierAct = new PhonUIAction(TextGridView.this, "onToggleTier", getTierName());
			hideTierAct.putValue(PhonUIAction.NAME, "Hide tier");
			hideTierAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Hide tier, to view again use the 'TextGrid Tier Management' dialog found in the TextGrid menu.");
			popupMenu.add(new JMenuItem(hideTierAct));
			
			popupMenu.addSeparator();
			
			final PhonUIAction tierManagementAct = new PhonUIAction(TextGridView.this, "onTierManagement");
			tierManagementAct.putValue(PhonUIAction.NAME, "TextGrid Tier Management...");
			popupMenu.add(new JMenuItem(tierManagementAct));
			
			popupMenu.show(tierButton, 0, tierButton.getHeight());
		}
		
		
		
		public String getTierName() {
			return this.tierName;
		}
		
		private void init() {
			setLayout(new HorizontalLayout(0));
			
			setBorder(BorderFactory.createLineBorder(Color.darkGray));
			
			// setup map tier button
			final Icon mapIcn = IconManager.getInstance().getIcon("emblems/arrowhead-right", IconSize.XSMALL);
			tierButton = new JLabel(mapIcn);
			tierButton.setToolTipText("Show menu");
			tierButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			tierButton.addMouseListener(new MouseInputAdapter() {
				
				@Override
				public void mouseClicked(MouseEvent me) {
					showTierMenu();
				}
				
			});
			
			if(isMappedTier())
				setBackground(new Color(50, 255, 120, 120));
			else
				setBackground(new Color(255, 255, 0, 120));
			
			tierNameLabel = new JTextField(tierName) {
				@Override
				public Dimension getPreferredSize() {
					final Dimension retVal = super.getPreferredSize();
					retVal.width += 10;
					return retVal;
				}
			};
			tierNameLabel.setHorizontalAlignment(JTextField.CENTER);
			tierNameLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
			tierNameLabel.setOpaque(false);
			
			add(tierNameLabel);
			add(tierButton);
					
			final AtomicReference<InputMap> inputMapRef = 
					new AtomicReference<InputMap>(getParentView().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT));
			final AtomicReference<String> tierNameRef = new AtomicReference<String>("");
			tierNameEditSupport = new DoubleClickableTextField(tierNameLabel);
			tierNameEditSupport.addPropertyChangeListener(DoubleClickableTextField.TEXT_PROPERTY, (e) -> {
				if(!renameTier(tierNameRef.get(), tierNameLabel.getText())) {
					Toolkit.getDefaultToolkit().beep();
					tierNameLabel.setText(tierNameRef.get());
				}
			});
			tierNameEditSupport.addPropertyChangeListener(DoubleClickableTextField.EDITING_PROPERTY, (e) -> {
				if(tierNameEditSupport.isEditing()) {
					getParentView().getWavDisplay().setSelectionStart(0f);
					getParentView().getWavDisplay().setSelectionLength(0f);
					tierNameRef.set(tierNameLabel.getText());
					getParentView().setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, new InputMap());
				} else {
					getParentView().setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, inputMapRef.get());
					getParentView().requestFocusInWindow();
				}
			});
		}
		
	}

	private class TierLabelButton extends JLabel {
		
		private boolean pressed = false;
		
		public TierLabelButton() {
			super();
			init();
		}
		
		public TierLabelButton(Icon icn) {
			super(icn);
			init();
		}
		
		private void init() {
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			
			addMouseListener(new MouseInputAdapter() {
				
				@Override
				public void mouseEntered(MouseEvent me) {
					
				}
				
				@Override
				public void mouseExited(MouseEvent me) {
					
				}
				
				@Override
				public void mousePressed(MouseEvent me) {
					pressed = true;
					repaint();
				}
				
				@Override
				public void mouseReleased(MouseEvent me) {
					pressed = false;
					repaint();
				}
				
			});
		}
		
		@Override
		public void paintComponent(Graphics g) {
			// paint background
			final Color bgColor = getBackground();
			g.setColor(bgColor);
			g.fillRect(0, 0, getWidth(), getHeight());
			
			if(pressed) {
				
			} else {
				
			}
			
			super.paintComponent(g);
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

		private File textGridFile;

		public TextGridTcpHandler(File textGridFile) {
			this.textGridFile = textGridFile;
		}

		@Override
		public void praatScriptFinished(String data) {
			serverMap.remove(textGridFile);
			unlock();

			// grab new TextGrid from default praat save location
			final File tgFile = new File(
					PraatDir.getPath() + File.separator + "praat_backToCaller.Data");
			if(tgFile.exists() && tgFile.isFile()) {

				try {
					TextGrid tg = Daata.readFromFile(TextGrid.class, MelderFile.fromPath(tgFile.getAbsolutePath()));
					final SessionEditor model = parent.getEditor();
					TextGridManager.saveTextGrid(tg, textGridFile);

					if(currentTextGridFile.equals(textGridFile)) {
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
