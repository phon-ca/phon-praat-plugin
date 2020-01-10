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
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
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

import javax.swing.AbstractAction;
import javax.swing.Action;
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
import javax.swing.SwingConstants;
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

import com.sun.jna.Pointer;

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
import ca.phon.app.log.LogUtil;
import ca.phon.app.session.editor.DelegateEditorAction;
import ca.phon.app.session.editor.EditorAction;
import ca.phon.app.session.editor.EditorEvent;
import ca.phon.app.session.editor.EditorEventType;
import ca.phon.app.session.editor.RunOnEDT;
import ca.phon.app.session.editor.SessionEditor;
import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisEditorView;
import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisTier;
import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisViewColors;
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
import ca.phon.ui.ButtonPopup;
import ca.phon.ui.CommonModuleFrame;
import ca.phon.ui.DropDownButton;
import ca.phon.ui.HidablePanel;
import ca.phon.ui.action.PhonActionEvent;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.fonts.FontPreferences;
import ca.phon.ui.layout.ButtonBarBuilder;
import ca.phon.ui.menu.MenuBuilder;
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
public class TextGridSpeechAnalysisTier extends SpeechAnalysisTier {

	/*
	 * Editor event
	 * data - String name of textGrid
	 */
	public static final String TEXT_GRID_CHANGED_EVENT =
			TextGridSpeechAnalysisTier.class.getName() + ".textGridChangedEvent";

	private static final Logger LOGGER = Logger
			.getLogger(TextGridSpeechAnalysisTier.class.getName());

	private static final long serialVersionUID = -4777676504641976886L;

	private final static String OPEN_TEXTGRID_TEMPLATE = "ca/phon/plugins/praat/OpenTextGrid.vm";

	private TextGridManager tgManager;

	private TextGrid tg;

	private File currentTextGridFile = null;

	// parent panel
	private SpeechAnalysisEditorView parent;

	private TextGridView textGridView;

	private JPanel buttonPane;

	public final static String SHOW_TEXTGRID_PROP = TextGridSpeechAnalysisTier.class.getName() + ".showTextGrid";
	private boolean showTextGrid =
			PrefHelper.getBoolean(SHOW_TEXTGRID_PROP, false);

	public final static String SHOW_TIER_LABELS_PROP = TextGridSpeechAnalysisTier.class.getName() + ".showTierLabels";
	private boolean showTierLabels =
			PrefHelper.getBoolean(SHOW_TIER_LABELS_PROP, true);

	public final static String SELECTED_TEXTGRID_PROP_PREFIX =
			"TextGridSpeechAnalysisTier.";
	public final static String SELECTED_TEXTGRID_PROP_SUXFFIX =
			".selectedTextGrid";

	private final HidablePanel textGridMessage = new HidablePanel("TextGridSpeechAnalysisTier.message");

	public TextGridSpeechAnalysisTier(SpeechAnalysisEditorView parent) {
		super(parent);
		
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

		textGridView = new TextGridView(getTimeModel());
		textGridView.addTextGridViewListener( (tg, tuple) -> {
			// update selection
			try {
				IntervalTier intervalTier = tg.checkSpecifiedTierIsIntervalTier(tuple.getObj1());
				TextInterval textInterval = intervalTier.interval(tuple.getObj2());
				
				getParentView().setSelection((float)textInterval.getXmin(), (float)textInterval.getXmax());
			} catch (PraatException e) {
				LogUtil.severe(e);
			}
		});
		
		add(textGridView, BorderLayout.CENTER);
		
		loadTextGrid();
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

	private void cleanup() {
		if(this.tg != null) {
			try {
				this.tg.close();
			} catch (Exception e) {
				LogUtil.severe(e);
			}
		}
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
		
		final EditorAction editorClosingAct = new DelegateEditorAction(this, "onEditorClosing");
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.EDITOR_CLOSING, editorClosingAct);
	}

	private void installKeyStrokes(SpeechAnalysisEditorView p) {
		final InputMap inputMap = p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		final ActionMap actionMap = p.getActionMap();

		final String toggleTextGridId = "onToggleTextGrid";
		final PhonUIAction toggleTextGridAct = new PhonUIAction(this, toggleTextGridId);
		actionMap.put(toggleTextGridId, toggleTextGridAct);
		final KeyStroke toggleTextGridKs = KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.SHIFT_DOWN_MASK);
		inputMap.put(toggleTextGridKs, toggleTextGridId);

		p.setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, inputMap);
		p.setActionMap(actionMap);
	}

	public void setTextGrid(TextGrid tg) {
		if(this.tg != null
				&& this.tg != tg) {
			try {
				// delete old textgrid from memory
				this.tg.forget();
			} catch (PraatException e) {
				LogUtil.severe(e);
			}
		}
		this.tg = tg;
		textGridView.setTextGrid(this.tg);
		
		updateHiddenTiers();
	}

	public TextGrid getTextGrid() {
		return this.tg;
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		textGridView.setEnabled(enabled);
	}

	public void onGenerateTextGrid() {
		final SessionEditor editor = parent.getEditor();

		final TextGridExportWizard wizard = new TextGridExportWizard(editor.getProject(), editor.getSession());
		wizard.showWizard();
	}

	public SpeechAnalysisEditorView getParentView() {
		return this.parent;
	}

	/**
	 * Adds extra buttons to the segment panel toolbar
	 */
	private void setupToolbar() {
		JPopupMenu spectrumMenu = new JPopupMenu();
		Action menuAct = new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
			}
		};
		menuAct.putValue(Action.NAME, "TextGrid");
		menuAct.putValue(Action.SHORT_DESCRIPTION, "Show TextGrid menu");
		menuAct.putValue(Action.SMALL_ICON, IconManager.getInstance().getIcon("apps/praat", IconSize.SMALL));
		menuAct.putValue(DropDownButton.ARROW_ICON_GAP, 2);
		menuAct.putValue(DropDownButton.ARROW_ICON_POSITION, SwingConstants.BOTTOM);
		menuAct.putValue(DropDownButton.BUTTON_POPUP, spectrumMenu);
		
		DropDownButton menuBtn = new DropDownButton(menuAct);
		menuBtn.setOnlyPopup(true);
		menuBtn.getButtonPopup().addPropertyChangeListener(ButtonPopup.POPUP_VISIBLE, (e) -> {
			if((boolean)e.getNewValue()) {
				spectrumMenu.removeAll();
				addMenuItems(new MenuBuilder(spectrumMenu), true);
			}
		});
		parent.getToolbar().addSeparator();
		parent.getToolbar().add(menuBtn);
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
		if(!textGridView.isEnabled()) return;

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
		textGridView.setEnabled(false);

		final JButton forceUnlockBtn = new JButton();
		final PhonUIAction forceUnlockAct = new PhonUIAction(this, "onForceUnlock", textGridFile);
		forceUnlockAct.putValue(PhonUIAction.NAME, "Unlock TextGrid");
		forceUnlockAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "TextGrid is open in Praat.  Use 'File' -> 'Send back to calling program' in Praat or click to unlock.");
		forceUnlockAct.putValue(PhonUIAction.SMALL_ICON, IconManager.getInstance().getIcon("emblems/emblem-readonly", IconSize.SMALL));
		forceUnlockBtn.setAction(forceUnlockAct);

		buttonPane.add(forceUnlockBtn);

		update();
	}

	public void onForceUnlock(PhonActionEvent pae) {
		textGridView.setEnabled(true);

		final File textGridFile = (File)pae.getData();

		// stop server
		final PraatScriptTcpServer server = serverMap.get(textGridFile);

		if(server != null) {
			server.stop();
			serverMap.remove(textGridFile);
		}

		buttonPane.remove((JButton)pae.getActionEvent().getSource());

		update();
	}

	private void unlock() {
		textGridView.setEnabled(true);
		buttonPane.removeAll();

		revalidate();
		textGridView.repaint();
	}

	private void update() {
		revalidate();
		textGridView.repaint();
	}

	@RunOnEDT
	public void onRecordChanged(EditorEvent ee) {
		update();
	}

	@RunOnEDT
	public void onTextGridChanged(EditorEvent ee) {
		if(ee.getEventData() != null && ee.getEventData() instanceof File) {
			textGridMessage.setVisible(false);
			final File file = (File)ee.getEventData();

			// load TextGrid
			try {
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
	
	@RunOnEDT
	public void onEditorClosing(EditorEvent ee) {
		cleanup();
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
					onTextGridChanged(new EditorEvent(TextGridSpeechAnalysisTier.TEXT_GRID_CHANGED_EVENT, this, newFile));
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
		TextGridSpeechAnalysisTier.this.setVisible(showTextGrid);
		if(showTextGrid) update();
	}

	public void onToggleTextGridLabels() {
		showTierLabels = !showTierLabels;
		PrefHelper.getUserPreferences().putBoolean(SHOW_TIER_LABELS_PROP, showTierLabels);
		textGridView.setShowLabels(showTierLabels);
		
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
		final boolean isVisible = textGridView.isTierVisible(tierName);
		textGridView.setTierVisible(tierName, !isVisible);
		
		saveHiddenTiers();
		revalidate();
	}

	private void updateHiddenTiers() {
		if(tg == null) return;
		final String tgName = FilenameUtils.getBaseName(currentTextGridFile.getAbsolutePath());
		final String propName = parent.getEditor().getSession().getCorpus() + "." +
				parent.getEditor().getSession().getName() + "." + tgName + ".hiddenTiers";

		final String hiddenTiers =
				PrefHelper.getUserPreferences().get(propName, "");
		final List<String> tierNames = Arrays.asList(hiddenTiers.split(","));
		for(int i = 1; i <= tg.numberOfTiers(); i++) {
			final Function tier = tg.tier(i);
			textGridView.setTierVisible(tier.getName(), !tierNames.contains(tier.getName()));
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
			if(!textGridView.isTierVisible(tier.getName())) {
				if(buffer.length() > 0) buffer.append(',');
				buffer.append(tier.getName());
			}
		}
		final String hiddenTiers = buffer.toString();

		PrefHelper.getUserPreferences().put(propName, hiddenTiers);
	}
	
	public boolean isTextGridTierVisible(String tierName) {
		return textGridView.isTierVisible(tierName);
	}
	
	public void setTextGridTierVisible(String tierName, boolean visible) {
		textGridView.setTierVisible(tierName, visible);
	}

	public void onPlayInterval() {
		parent.playPause();
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
	
	private void addMenuItems(MenuBuilder builder, boolean isContextMenu) {
		final Session session = parent.getEditor().getSession();
		final TextGridManager manager = new TextGridManager(parent.getEditor().getProject());

		final PhonUIAction toggleAct = new PhonUIAction(this, "onToggleTextGrid");
		toggleAct.putValue(PhonUIAction.NAME, "Show TextGrid");
		toggleAct.putValue(PhonUIAction.SELECTED_KEY, TextGridSpeechAnalysisTier.this.isVisible());
		if(isContextMenu)
			toggleAct.putValue(PhonUIAction.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.SHIFT_DOWN_MASK));
		final JCheckBoxMenuItem toggleItem = new JCheckBoxMenuItem(toggleAct);
		builder.addItem(".", toggleItem);

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

					final PhonUIAction showTgAct = new PhonUIAction(TextGridSpeechAnalysisTier.this, "showTextGrid", textGridFile);
					showTgAct.putValue(PhonUIAction.NAME, menuTxt);
					showTgAct.putValue(PhonUIAction.SHORT_DESCRIPTION, textGridFile.getAbsolutePath());
					showTgAct.putValue(PhonUIAction.SELECTED_KEY, isCurrent);
					if(icn != null) {
						showTgAct.putValue(PhonUIAction.SMALL_ICON, icn);
					}
					textGridMenu.add(new JCheckBoxMenuItem(showTgAct));
				}
				textGridMenu.addSeparator();

				final PhonUIAction addExistingTextGridAct = new PhonUIAction(TextGridSpeechAnalysisTier.this, "onAddExistingTextGrid");
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
		builder.addItem(".", textGridMenu);

		final PhonUIAction toggleLabelsAct = new PhonUIAction(this, "onToggleTextGridLabels");
		toggleLabelsAct.putValue(PhonUIAction.NAME, "Show Tier Labels");
		toggleLabelsAct.putValue(PhonUIAction.SELECTED_KEY, TextGridSpeechAnalysisTier.this.showTierLabels);
		final JCheckBoxMenuItem toggleLabelsItem = new JCheckBoxMenuItem(toggleLabelsAct);
		builder.addItem(".", toggleLabelsItem);

		final PhonUIAction tierMgtAct = new PhonUIAction(TextGridSpeechAnalysisTier.this, "onTierManagement");
		tierMgtAct.putValue(PhonUIAction.NAME, "TextGrid Tier Management...");
		builder.addItem(".", new JMenuItem(tierMgtAct));

		builder.addSeparator(".", "s1");
		final PhonUIAction genTextGridAct = new PhonUIAction(TextGridSpeechAnalysisTier.this, "onGenerateTextGrid");
		genTextGridAct.putValue(PhonUIAction.NAME, "Generate TextGrid...");
		genTextGridAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Generate TextGrid for session...");
		builder.addItem(".", genTextGridAct);

		PhonUIAction createRecordsAct = new PhonUIAction(TextGridSpeechAnalysisTier.this, "onCreateRecordsFromTextGrid");
		createRecordsAct.putValue(PhonUIAction.NAME, "Create records from TextGrid...");
		createRecordsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Create records from an existing TextGrid");
		builder.addItem(".", createRecordsAct);

		// sendpraat menu items
		builder.addSeparator(".", "s2");
		PhonUIAction openTextGridAct = new PhonUIAction(this, "openTextGrid", Boolean.TRUE);
		openTextGridAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Display TextGrid in an open instance of Praat with full audio. High memory usage.");
		openTextGridAct.putValue(PhonUIAction.NAME, "Open TextGrid in Praat - full audio");
		builder.addItem(".", openTextGridAct);

		PhonUIAction openTextGridAct2 = new PhonUIAction(this, "openTextGrid", Boolean.FALSE);
		openTextGridAct2.putValue(PhonUIAction.SHORT_DESCRIPTION, "Display TextGrid in an open instance of Praat with segment only. Low memory usage.");
		openTextGridAct2.putValue(PhonUIAction.NAME, "Open TextGrid in Praat - segment only");
		builder.addItem(".", openTextGridAct2);

		final PhonUIAction sendPraatAct = new PhonUIAction(this, "onSendPraat");
		sendPraatAct.putValue(PhonUIAction.NAME, "Open TextGrid in Praat - custom script...");
		sendPraatAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Open TextGrid and audio in Praat and execute Praat script...");
		builder.addItem(".", sendPraatAct);
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
		
		addMenuItems(new MenuBuilder(praatMenu), isContextMenu);
	}

	private class TextGridMouseListener extends MouseInputAdapter {

		@Override
		public void mousePressed(MouseEvent e) {
			if(!textGridView.isEnabled()) return;
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
							intervalTier.timeToLowIndex(getTimeModel().timeAtX(e.getX()));

					if(intervalIdx > 0 && intervalIdx <= intervalTier.numberOfIntervals()) {
						final TextInterval interval = intervalTier.interval(intervalIdx);

						if(interval != null) {
							// set selection to interval
							getParentView().setSelection((float)interval.getXmin(), (float)interval.getXmax());
							requestFocus();
						}
					}
				} catch (PraatException pe) {
					// do nothing
				}
			}
			// dispatch event to parent listeners
			TextGridSpeechAnalysisTier.this.dispatchEvent(e);
		}

		@Override
		public void mouseReleased(MouseEvent me) {
			// dispatch event to parent container
			TextGridSpeechAnalysisTier.this.dispatchEvent(me);
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
				if(textGridView.isTierVisible(tier.getName())) {
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
			
			textGridView.repaint();
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
	
//	public void onMapTier(TierLabel tierLabel) {
//		final TextGridTierMapper mapper = new TextGridTierMapper(
//				getParentView().getEditor().getSession(),
//				getTextGrid());
//		final JTree tree = new JTree(mapper.createTreeModel());
//		final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)tree.getModel().getRoot();
//		final TreePath rootPath = new TreePath(rootNode);
//		for(int i = 0; i < rootNode.getChildCount(); i++) {
//			final TreePath treePath = rootPath.pathByAddingChild(rootNode.getChildAt(i));
//			tree.expandPath(treePath);
//		}
//
//		tree.setVisibleRowCount(10);
//		tree.expandPath(new TreePath(tree.getModel().getRoot()));
//		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
//		final JScrollPane scroller = new JScrollPane(tree);
//
//		final Point p = new Point(0, tierLabel.tierButton.getHeight());
//		SwingUtilities.convertPointToScreen(p, tierLabel.tierButton);
//
//		final JFrame popup = new JFrame("Map TextGrid Tier");
//		popup.setUndecorated(true);
//		popup.addWindowFocusListener(new WindowFocusListener() {
//
//			@Override
//			public void windowLostFocus(WindowEvent e) {
//				destroyPopup(popup);
//			}
//
//			@Override
//			public void windowGainedFocus(WindowEvent e) {
//			}
//
//		});
//
//		final PhonUIAction cancelAct = new PhonUIAction(this, "destroyPopup", popup);
//		cancelAct.putValue(PhonUIAction.NAME, "Cancel");
//		final JButton cancelBtn = new JButton(cancelAct);
//
//		final PhonUIAction okAct = new PhonUIAction(this, "mapTier", new Tuple<String, JTree>(tierLabel.tierName, tree));
//		okAct.putValue(PhonUIAction.NAME, "Map to Selected Phon Tier and Dimension");
//		final JButton okBtn = new JButton(okAct);
//		okBtn.addActionListener( (e) -> {
//			final TreePath selectedPath = tree.getSelectionPath();
//			if(selectedPath != null) {
//				final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectedPath.getLastPathComponent();
//				if(treeNode.isLeaf()) {
//					destroyPopup(popup);
//				}
//			}
//		} );
//
//		final JComponent btnBar = ButtonBarBuilder.buildOkCancelBar(okBtn, cancelBtn);
//
//		popup.setLayout(new BorderLayout());
//		popup.add(scroller, BorderLayout.CENTER);
//		popup.add(btnBar, BorderLayout.SOUTH);
//
//		popup.pack();
//		popup.setLocation(p.x, p.y);
//		popup.setVisible(true);
//
//		popup.getRootPane().setDefaultButton(okBtn);
//	}
	
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
						setTextGrid(tg);
					}
				} catch (PraatException | IOException e) {
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
					ToastFactory.makeToast("Unable to update TextGrid!").start(TextGridSpeechAnalysisTier.this);
				}

			}

			update();
		}
	}

}