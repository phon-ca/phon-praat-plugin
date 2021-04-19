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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import java.util.regex.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;

import ca.phon.app.session.editor.view.record_data.RecordDataEditorView;
import ca.phon.media.MediaLocator;
import org.apache.commons.io.*;
import org.apache.commons.io.monitor.*;

import ca.hedlund.jpraat.binding.fon.*;
import ca.hedlund.jpraat.binding.sys.*;
import ca.hedlund.jpraat.exceptions.*;
import ca.phon.app.log.*;
import ca.phon.app.session.editor.*;
import ca.phon.app.session.editor.view.speech_analysis.*;
import ca.phon.plugins.praat.export.*;
import ca.phon.plugins.praat.importer.*;
import ca.phon.plugins.praat.script.*;
import ca.phon.session.*;
import ca.phon.ui.*;
import ca.phon.ui.action.*;
import ca.phon.ui.decorations.*;
import ca.phon.ui.fonts.*;
import ca.phon.ui.layout.*;
import ca.phon.ui.menu.*;
import ca.phon.ui.nativedialogs.*;
import ca.phon.ui.nativedialogs.FileFilter;
import ca.phon.ui.toast.*;
import ca.phon.util.*;
import ca.phon.util.icons.*;
import ca.phon.worker.*;
import org.jdesktop.swingx.HorizontalLayout;

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

	/**
	 * Font size
	 */
	private DropDownButton fontSizeButton;
	private JPopupMenu fontSizeMenu;

	public final static String FONT_SIZE_DELTA_PROP = TextGridSpeechAnalysisTier.class.getName() + ".fontSizeDelta";
	public final static float DEFAULT_FONT_SIZE_DELTA = 0.0f;
	public float fontSizeDelta = PrefHelper.getFloat(FONT_SIZE_DELTA_PROP, DEFAULT_FONT_SIZE_DELTA);

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

	private final ErrorBanner textGridMessage = new ErrorBanner();
	
	private ErrorBanner forceUnlockBtn;
	
	/**
	 * Static reference to locked TextGrid
	 * Only one TextGrid may be locked at a time!
	 * 
	 * @since Phon 3.1
	 */
	private static final AtomicReference<Tuple<File, FileAlterationMonitor>> lockedTextGridRef = new AtomicReference<>();

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
		
		forceUnlockBtn = new ErrorBanner();
		forceUnlockBtn.setToolTipText("Click to unlock TextGrid");
		forceUnlockBtn.getTopLabel().setIcon(IconManager.getInstance().getIcon("emblems/emblem-readonly", IconSize.SMALL));
		forceUnlockBtn.setTopLabelText("TextGrid Locked");
		forceUnlockBtn.setBottomLabelText("<html>TextGrid open in Praat: Select menu <i>File</i> &gt; <i>Send back to calling program</i> (in Praat), or click this message to unlock</html>");
		
		textGridView = new TextGridView(getTimeModel());

		Font font = FontPreferences.getTierFont();
		float fontSize = getFontSizeDelta() < 0
				? Math.max(2, font.getSize() + getFontSizeDelta())
				: Math.min(34, font.getSize() + getFontSizeDelta());
		font = font.deriveFont(fontSize);
		textGridView.setFont(font);
		textGridView.addTextGridViewListener( new TextGridViewListener() {
			
			@Override
			public void tierLabelClicked(TextGrid textGrid, Long tierIdx, MouseEvent me) {
				String tierName = textGrid.tier(tierIdx).getName();
				showTierMenu(tierName, me);
			}
			
			@Override
			public void intervalSelected(TextGrid textGrid, Tuple<Long, Long> intervalIndex) {
				try {
					IntervalTier intervalTier = tg.checkSpecifiedTierIsIntervalTier(intervalIndex.getObj1());
					TextInterval textInterval = intervalTier.interval(intervalIndex.getObj2());
					
					getParentView().setSelection((float)textInterval.getXmin(), (float)textInterval.getXmax());
				} catch (PraatException e) {
					LogUtil.severe(e);
				}
			}
			
		} );

		textGridView.addMouseListener(getParentView().getContextMenuAdapter());

		this.addPropertyChangeListener("fontSizeDelta", e -> {
			PrefHelper.getUserPreferences().putFloat(FONT_SIZE_DELTA_PROP, getFontSizeDelta());
			update();
		});
		
		add(textGridView, BorderLayout.CENTER);
		
		loadTextGrid();
	}

	public float getFontSizeDelta() {
		return this.fontSizeDelta;
	}

	public void setFontSizeDelta(float fontSizeDelta) {
		float oldVal = this.fontSizeDelta;
		this.fontSizeDelta = fontSizeDelta;
		firePropertyChange("fontSizeDelta", oldVal, fontSizeDelta);
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
				this.tg = null;
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
			textGridMessage.clearActions();

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
			textGridMessage.setVisible(true);
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

		final EditorAction tierViewChangedAct = new DelegateEditorAction(this, "onTierViewChanged");
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.TIER_VIEW_CHANGED_EVT, tierViewChangedAct);

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
		@SuppressWarnings("resource")
		final TextGrid oldTextGrid = (this.tg != null && this.tg != tg ? this.tg : null);
		
		this.tg = tg;
		textGridView.setTextGrid(this.tg);
		
		updateHiddenTiers();
		updateTierFonts();
		updateTierLabelBackgrounds();
		
		if(oldTextGrid != null) {
			PhonWorker.getInstance().invokeLater(new Runnable() {
				@Override
				public void run() {
					try {
						oldTextGrid.forget();
					} catch (Exception e) {
						LogUtil.severe(e);
					}
				}
			});
		}
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

		fontSizeMenu = new JPopupMenu();
		fontSizeMenu.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				fontSizeMenu.removeAll();

				// setup font scaler
				final JLabel smallLbl = new JLabel("A");
				smallLbl.setFont(getFont().deriveFont(FontPreferences.getDefaultFontSize()));
				smallLbl.setHorizontalAlignment(SwingConstants.CENTER);
				JLabel largeLbl = new JLabel("A");
				largeLbl.setFont(getFont().deriveFont(FontPreferences.getDefaultFontSize()*2));
				largeLbl.setHorizontalAlignment(SwingConstants.CENTER);

				final JSlider scaleSlider = new JSlider(-8, 24);
				scaleSlider.setValue((int)getFontSizeDelta());
				scaleSlider.setMajorTickSpacing(8);
				scaleSlider.setMinorTickSpacing(2);
				scaleSlider.setSnapToTicks(true);
				scaleSlider.setPaintTicks(true);
				scaleSlider.addChangeListener( changeEvent -> {
					int sliderVal = scaleSlider.getValue();
					setFontSizeDelta(sliderVal);
				});

				JComponent fontComp = new JPanel(new HorizontalLayout());
				fontComp.add(smallLbl);
				fontComp.add(scaleSlider);
				fontComp.add(largeLbl);

				fontSizeMenu.add(fontComp);

				fontSizeMenu.addSeparator();

				final PhonUIAction useDefaultFontSizeAct = new PhonUIAction(TextGridSpeechAnalysisTier.this, "setFontSizeDelta", 0.0f);
				useDefaultFontSizeAct.putValue(PhonUIAction.NAME, "Use default font size");
				useDefaultFontSizeAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Reset font size");
				fontSizeMenu.add(useDefaultFontSizeAct);
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {

			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {

			}
		});

		final PhonUIAction fontSizeAct = new PhonUIAction(this, null);
		fontSizeAct.putValue(PhonUIAction.NAME, "Font size");
		fontSizeAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Show font size menu");
		fontSizeAct.putValue(PhonUIAction.SMALL_ICON, IconManager.getInstance().getIcon("apps/preferences-desktop-font", IconSize.SMALL));
		fontSizeAct.putValue(DropDownButton.BUTTON_POPUP, fontSizeMenu);
		fontSizeAct.putValue(DropDownButton.ARROW_ICON_POSITION, SwingConstants.BOTTOM);
		fontSizeAct.putValue(DropDownButton.ARROW_ICON_GAP, 2);

		fontSizeButton = new DropDownButton(fontSizeAct);
		fontSizeButton.setOnlyPopup(true);

		parent.getToolbar().add(fontSizeButton);
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
		try {
			final TextGrid textGrid = TextGridManager.loadTextGrid(textGridFile);
			if(textGrid != null) {
				currentTextGridFile = textGridFile;
				setTextGrid(textGrid);

				// lock textgrid if necessary
				if(lockedTextGridRef.get() != null && lockedTextGridRef.get().getObj1().equals(textGridFile)) {
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

//	private final Map<File, PraatScriptTcpServer> serverMap =
//			Collections.synchronizedMap(new HashMap<File, PraatScriptTcpServer>());
	/**
	 * Send TextGrid to Praat.
	 */
	public void openTextGrid(boolean useFullAudio) {
		File mediaFile =
				getAudioFile();
		if(mediaFile == null) return;

		// don't open if contentpane is current disabled (locked)
		if(!textGridView.isEnabled() || lockedTextGridRef.get() != null) return;

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
		File praatDir = new File(PraatDir.getPath());
		FileAlterationObserver observer = new FileAlterationObserver(praatDir);
		observer.addListener(new PraatTextGridResponseListener());
		FileAlterationMonitor monitor = new FileAlterationMonitor(100, observer);
		
		map.put("replyToPhon", Boolean.TRUE);
		map.put("audioPath", mediaFile.getAbsolutePath());
		map.put("textGridPath", tgPath);
		map.put("textGridName", tgName);
		map.put("segment", media);
		map.put("useFullAudio", useFullAudio);

		try {
			lockedTextGridRef.set(new Tuple<>(tgFile, monitor));
			lock(tgFile);
			monitor.start();

			final PraatScript ps = new PraatScript(loadTextGridTemplate());
			final String script = ps.generateScript(map);
			final String err = SendPraat.sendpraat(null, "Praat", 0, script);
			if(err != null && err.length() > 0) {
				throw new IOException(err);
			}
		} catch (Exception e) {
			unlock();
			Toolkit.getDefaultToolkit().beep();
			
			getParentView().getEditor().showMessageDialog("Unable to open TextGrid", e.getLocalizedMessage(), MessageDialogProperties.okOptions);
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	private void lock(File textGridFile) {
		textGridView.setEnabled(false);
		
		final PhonUIAction forceUnlockAct = new PhonUIAction(this, "onForceUnlock", textGridFile);
		forceUnlockAct.putValue(PhonUIAction.NAME, "TextGrid locked");
		forceUnlockAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "TextGrid open in Praat: Select menu 'File' > 'Send back to calling program' (in Praat), or click this message to unlock");
		forceUnlockAct.putValue(PhonUIAction.SMALL_ICON, IconManager.getInstance().getIcon("actions/unlock", IconSize.SMALL));
		forceUnlockAct.putValue(PhonUIAction.LARGE_ICON_KEY, IconManager.getInstance().getIcon("actions/unlock", IconSize.SMALL));
		forceUnlockBtn.setDefaultAction(forceUnlockAct);
		
		getParentView().getErrorPane().add(forceUnlockBtn);
		getParentView().revalidate();
		
		update();
	}

	public void onForceUnlock(PhonActionEvent pae) {
		Tuple<File, FileAlterationMonitor> lockInfo = lockedTextGridRef.get();
		if(lockInfo != null) {
			final File textGridFile = (File)pae.getData();
			// issue warning if TextGrid files don't match
			if(!lockInfo.getObj1().equals(textGridFile)) {
				LogUtil.warning("LockedText grid " + lockInfo.getObj1() + " does not match requested unlock of " + textGridFile);
			}
			
			unlock();
			update();
		}
	}

	private void unlock() {
		textGridView.setEnabled(true);
		getParentView().getErrorPane().remove(forceUnlockBtn);
		getParentView().revalidate();
		
		Tuple<File, FileAlterationMonitor> lockInfo = lockedTextGridRef.get();
		if(lockInfo != null) {
			try {
				lockInfo.getObj2().stop();
			} catch (Exception e) {
				LogUtil.severe(e);
			}
			lockedTextGridRef.set(null);
		}

		revalidate();
		textGridView.repaint();
	}

	private void update() {
		updateTierFonts();
		updateTierLabelBackgrounds();
		textGridView.repaint();
		
		revalidate();
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
	public void onTierViewChanged(EditorEvent ee) {
		update();
	}

	@RunOnEDT
	public void onMediaChanged(EditorEvent ee) {
		loadTextGrid();
	}
	
	@RunOnEDT
	public void onEditorClosing(EditorEvent ee) {
		cleanup();
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

	@Override
	public boolean shouldShow() {
		return PrefHelper.getUserPreferences().getBoolean(SHOW_TEXTGRID_PROP, super.shouldShow());
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
		if(!textGridView.isEnabled()) {
			Toolkit.getDefaultToolkit().beep();
			return;
		}
		
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
	
	public void showTierMenu(String tierName, MouseEvent me) {
		final JPopupMenu popupMenu = new JPopupMenu();
		
		// add 'Map to tier' options
//		final PhonUIAction mapAct = new PhonUIAction(TextGridView.this, "onMapTier", this);
//		mapAct.putValue(PhonUIAction.NAME, "Map to Phon tier...");
//		mapAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Map interval values to groups/words in a Phon tier.");
//		popupMenu.add(new JMenuItem(mapAct));
//		popupMenu.addSeparator();

//		final PhonUIAction renameAct = new PhonUIAction(tierNameEditSupport, "setEditing", Boolean.TRUE);
//		renameAct.putValue(PhonUIAction.NAME, "Rename tier");
//		renameAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Rename tier");
//		popupMenu.add(new JMenuItem(renameAct));
		
		final PhonUIAction hideTierAct = new PhonUIAction(this, "onToggleTier", tierName);
		hideTierAct.putValue(PhonUIAction.NAME, "Hide tier");
		hideTierAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Hide tier, to view again use the 'TextGrid Tier Management' dialog found in the TextGrid menu.");
		popupMenu.add(new JMenuItem(hideTierAct));
		
		popupMenu.addSeparator();
		
		final PhonUIAction tierManagementAct = new PhonUIAction(this, "onTierManagement");
		tierManagementAct.putValue(PhonUIAction.NAME, "TextGrid Tier Management...");
		popupMenu.add(new JMenuItem(tierManagementAct));
		
		popupMenu.show(me.getComponent(), me.getX(), me.getY());
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
	
	private final String mappedTierRegex = "([- _\\w]+)\\s?:\\s?((Tier|Group|Word|Syllable|Phone))";
	private boolean isMappedTier(String tierName) {
		boolean retVal = false;
		
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

	public void updateTierFonts() {
		textGridView.clearTierFonts();

		TextGrid tg = getTextGrid();
		if(tg == null) return;

		Font defaultFont = FontPreferences.getTierFont();
		defaultFont = getFontSizeDelta() < 0
				? defaultFont.deriveFont(Math.max(FontPreferences.MIN_FONT_SIZE, defaultFont.getSize() + getFontSizeDelta()))
				: defaultFont.deriveFont(Math.min(FontPreferences.MAX_FONT_SIZE, defaultFont.getSize() + getFontSizeDelta()));

		for(long i = 1; i <= tg.numberOfTiers(); i++) {
			Function tier = tg.tier(i);
			if(isMappedTier(tier.getName())) {
				String phonTierName = tier.getName().split(":")[0];
				Optional<TierViewItem> tvi = getParentView().getEditor().getSession().getTierView()
						.stream()
						.filter( tv -> tv.getTierName().equals(phonTierName) )
						.findFirst();
				Font tierFont = defaultFont;
				if(tvi.isPresent()) {
					if(!"default".equals(tvi.get().getTierFont())) {
						tierFont = Font.decode(tvi.get().getTierFont());
						tierFont = getFontSizeDelta() < 0
								? tierFont.deriveFont(Math.max(FontPreferences.MIN_FONT_SIZE, tierFont.getSize() + getFontSizeDelta()))
								: tierFont.deriveFont(Math.min(FontPreferences.MAX_FONT_SIZE, tierFont.getSize() + getFontSizeDelta()));
					}
				}

				textGridView.setTierFont(tier.getName(), tierFont);
			} else {
				textGridView.setTierFont(tier.getName(), defaultFont);
			}
		}
	}
	
	public void updateTierLabelBackgrounds() {
		TextGrid tg = getTextGrid();
		if(tg == null) return;
		
		for(long i = 1; i <= tg.numberOfTiers(); i++) {
			Function tier = tg.tier(i);
			if(isMappedTier(tier.getName())) {
				textGridView.setLabelBackground(tier.getName(), new Color(50, 255, 120, 120));
			} else {
				textGridView.setLabelBackground(tier.getName(), TextGridView.DEFAULT_TIER_LABEL_COLOR);
			}
		}
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

					if(lockedTextGridRef.get() != null && lockedTextGridRef.get().getObj1().equals(currentTextGridFile)) {
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
	
	private class PraatTextGridResponseListener extends FileAlterationListenerAdaptor {

		@Override
		public void onFileChange(File file) {
			File praatDataFile = new File(PraatDir.getPath(), "praat_backToCaller.Data");
			Tuple<File, FileAlterationMonitor> lockInfo = lockedTextGridRef.get();
			if(praatDataFile.equals(file) && lockInfo != null) {
				// we have our return message
				try {
					TextGrid tg = Daata.readFromFile(TextGrid.class, MelderFile.fromPath(praatDataFile.getAbsolutePath()));
					TextGridManager.saveTextGrid(tg, lockInfo.getObj1());

					if(currentTextGridFile.equals(lockInfo.getObj1())) {
						setTextGrid(tg);
					}
				} catch (PraatException | IOException e) {
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
					ToastFactory.makeToast("Unable to update TextGrid!").start(TextGridSpeechAnalysisTier.this);
				}
				unlock();
				update();
				
				if(Desktop.isDesktopSupported())
					Desktop.getDesktop().requestForeground(true);
				getParentView().getEditor().requestFocus();
			}
		}
		
	}

}
