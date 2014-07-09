package ca.phon.plugins.praat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.MenuElement;

import org.jdesktop.swingx.VerticalLayout;

import ca.hedlund.jpraat.binding.Praat;
import ca.hedlund.jpraat.binding.sys.SendPraat;
import ca.phon.app.session.editor.DelegateEditorAction;
import ca.phon.app.session.editor.EditorAction;
import ca.phon.app.session.editor.EditorEvent;
import ca.phon.app.session.editor.EditorEventType;
import ca.phon.app.session.editor.RunOnEDT;
import ca.phon.app.session.editor.SessionEditor;
import ca.phon.app.session.editor.view.waveform.WaveformEditorView;
import ca.phon.app.session.editor.view.waveform.WaveformTier;
import ca.phon.app.session.editor.view.waveform.WaveformViewCalculator;
import ca.phon.media.exceptions.PhonMediaException;
import ca.phon.media.util.MediaLocator;
import ca.phon.media.wavdisplay.WavDisplay;
import ca.phon.plugins.praat.export.TextGridExportWizard;
import ca.phon.plugins.praat.script.PraatScript;
import ca.phon.plugins.praat.script.PraatScriptContext;
import ca.phon.session.MediaSegment;
import ca.phon.session.RangeRecordFilter;
import ca.phon.session.Record;
import ca.phon.session.RecordFilter;
import ca.phon.session.SystemTierType;
import ca.phon.session.Tier;
import ca.phon.textgrid.TextGrid;
import ca.phon.textgrid.TextGridInterval;
import ca.phon.textgrid.TextGridTier;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.ui.toast.Toast;
import ca.phon.ui.toast.ToastFactory;
import ca.phon.util.icons.IconManager;
import ca.phon.util.icons.IconSize;

/**
 * Display a TextGrid as a vertical list of tiers.
 */
public class TextGridViewer extends JPanel implements WaveformTier {
	
	public static final String TEXT_GRID_CHANGED_EVENT = 
			TextGridViewer.class.getName() + ".textGridChangedEvent";
	
	private static final Logger LOGGER = Logger
			.getLogger(TextGridViewer.class.getName());
	
	private static final long serialVersionUID = -4777676504641976886L;
	
	private final static String OPEN_TEXTGRID_TEMPLATE = "ca/phon/plugins/praat/OpenTextGrid.vm";
	
	private TextGrid tg;
	
	private WaveformViewCalculator calculator;
	
	// parent panel
	private WaveformEditorView parent;
	
	private JLayeredPane layeredPane;
	
	private JPanel contentPane;
	
//	private JPanel widgetPane;
	
	/*
	 * Play button shown when an interval is selected
	 */
//	private JButton playIntervalButton;
	
	public TextGridViewer(WaveformEditorView parent) {
		super();
		setVisible(false);
		setFocusable(true);
//		addFocusListener(focusListener);
		
		this.parent = parent;
		this.calculator = parent.getCalculator();
		
		layeredPane = new JLayeredPane();
		layeredPane.setLayout(null);
		
		contentPane = new JPanel(new VerticalLayout(0));
		layeredPane.add(contentPane, 0);
		
//		widgetPane = new JPanel(null);
//		widgetPane.setOpaque(false);
//		widgetPane.setVisible(false);
//		layeredPane.add(widgetPane, 1);
		
		setLayout(new BorderLayout());
		add(layeredPane, BorderLayout.CENTER);
		
//		final ImageIcon icon = IconManager.getInstance().getIcon("actions/media-playback-start", IconSize.SMALL);
//		final PhonUIAction action = new PhonUIAction(this, "onPlayInterval");
//		action.putValue(PhonUIAction.SHORT_DESCRIPTION, "Play selected interval...");
//		action.putValue(PhonUIAction.SMALL_ICON, icon);
//		playIntervalButton = new JButton(action);
//		
//		playIntervalButton.setBounds(0, 0, 16, 16);
//		widgetPane.add(playIntervalButton);
		
		this.parent.addComponentListener(resizeListener);
		
		
		// setup toolbar buttons
		setupToolbar();
		
		setupEditorActions();
		
		update();
	}
	
	private void setupEditorActions() {
		// setup editor actions
		final EditorAction recordChangedAct = new DelegateEditorAction(this, "onRecordChanged");
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.RECORD_CHANGED_EVT, recordChangedAct);
		
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
	
	private void setupTextGrid() {
		contentPane.removeAll();
		
		if(tg != null) {
			final Record currentRecord = parent.getEditor().currentRecord();
			final Tier<MediaSegment> segTier = currentRecord.getSegment();
			final MediaSegment seg = (segTier.numberOfGroups() == 1 ? segTier.getGroup(0) : null);
			if(seg == null ||
					(seg.getStartValue() / 1000.0f != tg.getMin())
					|| (seg.getEndValue() / 1000.0f != tg.getMax())) {
				final JLabel errLabel = new JLabel("<html><b>TextGrid dimensions do not match segment</b></html>");
				errLabel.setBackground(Color.red);
				errLabel.setOpaque(true);
				
				final PhonUIAction generateTextGridAct = new PhonUIAction
						(this, "onGenerateTextGrid");
				generateTextGridAct.putValue(PhonUIAction.NAME, "Generate TextGrid");
				generateTextGridAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Generate TextGrid for record.");
				
				final JButton generateTextGridBtn = new JButton(generateTextGridAct);
				contentPane.add(generateTextGridBtn);
				
				contentPane.add(errLabel);
				contentPane.add(generateTextGridBtn);
			} else {
				for(int i = 0; i < tg.getNumberOfTiers(); i++) {
					final TextGridTier tier = tg.getTier(i);
					final TextGridTierComponent tierComp = new TextGridTierComponent(tier, calculator);
					tierComp.setFont(getFont());
					tierComp.addPropertyChangeListener(TextGridTierComponent.SELECTED_INTERVAL_PROP, selectionListener);
					contentPane.add(tierComp);
				}
			}
		} else {
			final PhonUIAction generateTextGridAct = new PhonUIAction
					(this, "onGenerateTextGrid");
			generateTextGridAct.putValue(PhonUIAction.NAME, "Generate TextGrid");
			generateTextGridAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Generate TextGrid for record.");
			
			final JButton generateTextGridBtn = new JButton(generateTextGridAct);
			contentPane.add(generateTextGridBtn);
		}
		
		final Dimension prefSize = contentPane.getPreferredSize();
		layeredPane.setPreferredSize(prefSize);
		contentPane.setBounds(0, 0, parent.getWidth(), prefSize.height);
//		widgetPane.setBounds(0, 0, parent.getWidth(), prefSize.height);
	}
	
	public void onGenerateTextGrid() {
		final SessionEditor editor = parent.getEditor();
		final int currentRecord = editor.getCurrentRecordIndex();
		
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
	 * Convert a time value (in seconds) to an x-value.
	 * 
	 * @param time
	 * @return x-value for the given time
	 */
	private int locationForTime(float time) {
		final Rectangle2D contentRect = calculator.getSegmentRect();
		final float length = (tg.getMax() - tg.getMin());
		final float sPerPixel = length / (float)contentRect.getWidth();
		
		return (int)Math.round(contentRect.getX()) + (int)Math.round((time - tg.getMin()) / sPerPixel);
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
	
	public void openTextGrid() {
		File mediaFile =
				getAudioFile();
		if(mediaFile == null) return;

		final SessionEditor model = parent.getEditor();
		final Tier<MediaSegment> segmentTier = model.currentRecord().getSegment();
		
		if(segmentTier.numberOfGroups() == 0) return;

		final MediaSegment media = segmentTier.getGroup(0);
		final TextGridManager tgManager = TextGridManager.getInstance(model.getProject());
		String tgPath = tgManager.textGridPath(model.currentRecord().getUuid().toString());
		
		final PraatScriptContext map = new PraatScriptContext();
		map.put("recordId", model.currentRecord().getUuid().toString());
		map.put("soundFile", mediaFile.getAbsolutePath());
		map.put("tgFile", tgPath);
		map.put("interval", media);
		
		try {
			final PraatScript ps = new PraatScript(loadTextGridTemplate());
			final String script = ps.generateScript(map);
			final String err = SendPraat.sendpraat(null, "Praat", 0, script);
			if(err != null && err.length() > 0) {
				final Toast toast = ToastFactory.makeToast("Praat error: " + err);
				toast.start(this);
			}
		} catch (IOException e) {
			ToastFactory.makeToast(e.getLocalizedMessage()).start();
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	private void update() {
		final SessionEditor model = parent.getEditor();
		final TextGridManager tgManager = TextGridManager.getInstance(model.getProject());
		tgManager.addTextGridListener(tgListener);
		final TextGrid tg = tgManager.loadTextGrid(model.currentRecord().getUuid().toString());
		setTextGrid(tg);
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
		final boolean enabled = !TextGridViewer.this.isVisible();
		TextGridViewer.this.setVisible(enabled);
	}
	
	private final TextGridListener tgListener = new TextGridListener() {
		
		@Override
		public void textGridEvent(TextGridEvent event) {
			if(parent == null) return;
			// check the event and see if it matches our current record
			final SessionEditor model = parent.getEditor();
			if(model == null || model.getSession() == null || model.currentRecord() == null) return;
			
			final String currentCorpus = model.getSession().getCorpus();
			final String currentSession = model.getSession().getName();
			final String recordID = model.currentRecord().getUuid().toString();
			
			if( event.getCorpus().equals(currentCorpus) &&
					event.getSession().equals(currentSession) &&
					event.getRecordID().equals(recordID)) {
				update();
				invalidate();
				parent.revalidate();
			}
		}
	};
	
	private TextGridTierComponent selectedComponent = null;
	
	private final PropertyChangeListener selectionListener = new PropertyChangeListener() {
		
		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			final TextGridTierComponent tierComp = 
					(TextGridTierComponent)evt.getSource();
			if(selectedComponent != null) {
				selectedComponent.setSelectionPainted(false);
			}
			selectedComponent = tierComp;
			if(selectedComponent != null) {
				selectedComponent.setSelectionPainted(true);
				
				final TextGridInterval interval = selectedComponent.getSelectedInterval();
				if(interval != null) {
					final int start = Math.round(interval.getStart() * 1000.0f);
					final int end = Math.round(interval.getEnd() * 1000.0f);
					
					final WavDisplay wavDisplay = parent.getWavDisplay();
					wavDisplay.set_selectionStart(start - (int)wavDisplay.get_dipslayOffset());
					wavDisplay.set_selectionEnd(end - (int)wavDisplay.get_dipslayOffset());
					wavDisplay.repaint();
					
					final int btnx = locationForTime(interval.getEnd());
//					playIntervalButton.setLocation(btnx, selectedComponent.getLocation().y);
					
//					layeredPane.moveToFront(widgetPane);
					requestFocus();
				}
			}
		}
	};
	
	public void onPlayInterval() {
		try {
			parent.getWavDisplay().play();
		} catch (PhonMediaException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
//	private final FocusListener focusListener = new FocusListener() {
//		
//		@Override
//		public void focusLost(FocusEvent e) {
//			widgetPane.setVisible(false);
//		}
//		
//		@Override
//		public void focusGained(FocusEvent e) {
//			widgetPane.setVisible(true);
//		}
//	};
	
	private final ComponentListener resizeListener = new ComponentListener() {
		
		@Override
		public void componentShown(ComponentEvent e) {
		}
		
		@Override
		public void componentResized(ComponentEvent e) {
			final Dimension prefSize = contentPane.getPreferredSize();
			final int width = e.getComponent().getWidth();
			layeredPane.setPreferredSize(prefSize);
			contentPane.setBounds(0, 0, width, prefSize.height);
//			widgetPane.setBounds(0, 0, width, prefSize.height);
		}
		
		@Override
		public void componentMoved(ComponentEvent e) {
		}
		
		@Override
		public void componentHidden(ComponentEvent e) {
		}
	};

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
		toggleAct.putValue(PhonUIAction.NAME, "Toggle TextGrid");
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
}
