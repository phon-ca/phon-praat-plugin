package ca.phon.plugins.praat;

import java.awt.BorderLayout;
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
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;

import org.jdesktop.swingx.VerticalLayout;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;

import ca.phon.app.session.editor.DelegateEditorAction;
import ca.phon.app.session.editor.EditorAction;
import ca.phon.app.session.editor.EditorEvent;
import ca.phon.app.session.editor.EditorEventType;
import ca.phon.app.session.editor.SessionEditor;
import ca.phon.app.session.editor.media.WaveformEditorView;
import ca.phon.app.session.editor.media.WaveformViewCalculator;
import ca.phon.app.session.editor.media.WaveformTier;
import ca.phon.jsendpraat.SendPraat;
import ca.phon.media.exceptions.PhonMediaException;
import ca.phon.media.util.MediaLocator;
import ca.phon.media.wavdisplay.WavDisplay;
import ca.phon.plugins.praat.db.PraatDbManager;
import ca.phon.plugins.praat.script.PraatScript;
import ca.phon.plugins.praat.script.PraatScriptContext;
import ca.phon.plugins.praat.script.PraatScriptTcpHandler;
import ca.phon.plugins.praat.script.PraatScriptTcpServer;
import ca.phon.script.PhonScriptContext;
import ca.phon.session.MediaSegment;
import ca.phon.session.Tier;
import ca.phon.textgrid.TextGrid;
import ca.phon.textgrid.TextGridInterval;
import ca.phon.textgrid.TextGridTier;
import ca.phon.ui.PhonLoggerConsole;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.util.icons.IconManager;
import ca.phon.util.icons.IconSize;
import ca.phon.worker.PhonWorker;

/**
 * Display a TextGrid as a vertical list of tiers.
 */
public class TextGridViewer extends JPanel implements WaveformTier {
	
	private static final Logger LOGGER = Logger
			.getLogger(TextGridViewer.class.getName());
	
	private static final long serialVersionUID = -4777676504641976886L;
	
	private final static String OPEN_TEXTGRID_TEMPLATE = "ca/phon/plugins/praat/OpenTextGrid.vm";
	private final static String FORMANTS_TEMPLATE = "ca/phon/plugins/praat/FormantListing.vm";
	
	private TextGrid tg;
	
	private WaveformViewCalculator calculator;
	
	// parent panel
	private WaveformEditorView parent;
	
	// toolbar buttons
	private JCheckBox toggleViewerButton;
	
	private JButton openTextGridButton;
	
	// temporary 'formants' button
	private JButton formantsButton;
	
	private JLayeredPane layeredPane;
	
	private JPanel contentPane;
	
	private JPanel widgetPane;
	
	/*
	 * Play button shown when an interval is selected
	 */
	private JButton playIntervalButton;
	
	public TextGridViewer(WaveformEditorView parent) {
		super();
		setVisible(false);
		setFocusable(true);
		addFocusListener(focusListener);
		
		this.parent = parent;
		this.calculator = parent.getCalculator();
		
		layeredPane = new JLayeredPane();
		layeredPane.setLayout(null);
		
		contentPane = new JPanel(new VerticalLayout(0));
		layeredPane.add(contentPane, 0);
		
		widgetPane = new JPanel(null);
		widgetPane.setOpaque(false);
		widgetPane.setVisible(false);
		layeredPane.add(widgetPane, 1);
		
		setLayout(new BorderLayout());
		add(layeredPane, BorderLayout.CENTER);
		
		final ImageIcon icon = IconManager.getInstance().getIcon("actions/media-playback-start", IconSize.SMALL);
		final PhonUIAction action = new PhonUIAction(this, "onPlayInterval");
		action.putValue(PhonUIAction.SHORT_DESCRIPTION, "Play selected interval...");
		action.putValue(PhonUIAction.SMALL_ICON, icon);
		playIntervalButton = new JButton(action);
//		playIntervalButton.setBorderPainted(false);
		
		playIntervalButton.setBounds(0, 0, 16, 16);
		widgetPane.add(playIntervalButton);
		
		this.parent.addComponentListener(resizeListener);
		
		// setup editor actions
		final EditorAction recordChangedAct = new DelegateEditorAction(this, "onRecordChanged");
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.RECORD_CHANGED_EVT, recordChangedAct);
		
		// setup toolbar buttons
		setupToolbar();
		
		update();
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
			for(int i = 0; i < tg.getNumberOfTiers(); i++) {
				final TextGridTier tier = tg.getTier(i);
				final TextGridTierComponent tierComp = new TextGridTierComponent(tier, calculator);
				tierComp.setFont(getFont());
				tierComp.addPropertyChangeListener(TextGridTierComponent.SELECTED_INTERVAL_PROP, selectionListener);
				contentPane.add(tierComp);
			}
			
			final Dimension prefSize = contentPane.getPreferredSize();
			layeredPane.setPreferredSize(prefSize);
			final Point p = layeredPane.getLocation();
			contentPane.setBounds(0, 0, parent.getWidth(), prefSize.height);
			widgetPane.setBounds(0, 0, parent.getWidth(), prefSize.height);
		}
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
		final JToolBar toolbar = parent.getToolbar();
		
		toolbar.addSeparator();
		
		// toggle textgrid visibility
		toggleViewerButton = new JCheckBox("TextGrid");
		toggleViewerButton.setSelected(false);
		toggleViewerButton.addActionListener(toggleViewerAction);
//		toggleViewerButton.putClientProperty( "JButton.buttonType", "square" );
//		toggleViewerButton.putClientProperty( "JComponent.sizeVariant", "small" );
		
		final ImageIcon praatIcon = IconManager.getInstance().getIcon("apps/praat", IconSize.SMALL);
		final PhonUIAction openTextGridAct = new PhonUIAction(this, "openTextGrid");
		openTextGridAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Open TextGrid in Praat");
		openTextGridAct.putValue(PhonUIAction.NAME, "Open TextGrid");
		openTextGridAct.putValue(PhonUIAction.SMALL_ICON, praatIcon);
		openTextGridButton = new JButton(openTextGridAct);
		openTextGridButton.setVisible(false);
		
		final PhonUIAction formantsAct = new PhonUIAction(this, "showFormants");
		formantsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Display formants for selected interval");
		formantsAct.putValue(PhonUIAction.NAME, "Formants");
		formantsButton = new JButton(formantsAct);
		formantsButton.setVisible(false);
		formantsButton.setEnabled(false);
		
		toolbar.add(toggleViewerButton);
		toolbar.add(openTextGridButton);
		
		toolbar.add(formantsButton);
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
		
		
		final PraatScript ps = new PraatScript();
		String script;
		try {
			script = ps.generateScript(map);
			
			String errVal = SendPraat.sendPraat(script);
			if(errVal != null) {
				// try to open praat
				SendPraat.openPraat();
				// wait
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
				
				// try again
				SendPraat.sendPraat(script);
			}
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	public void showFormants() {
		File mediaFile =
				getAudioFile();
		if(mediaFile == null) return;

		if(selectedComponent == null) return;
		
		final TextGridInterval interval = selectedComponent.getSelectedInterval();

		final PraatScriptTcpServer server = new PraatScriptTcpServer();
		server.setHandler(new PraatScriptTcpHandler() {
			
			@Override
			public void praatScriptFinished(String data) {
				if(data == null) return;
				
				final PraatDbManager manager = new PraatDbManager(parent.getEditor().getProject());
				
				if(!manager.databaseExists()) {
					manager.createDatabase();
				}
				final ODatabaseDocumentTx docDb = manager.openDatabase();
				final ODocument doc = new ODocument("praatdata");
				doc.fromJSON(data);
				docDb.save(doc);
				
				
				
				docDb.close();
				
				
			}
			
		});
		final PraatScriptContext map = new PraatScriptContext();
		map.put("soundFile", mediaFile.getAbsolutePath());
		map.put("interval", interval);
		map.put("recordId", parent.getEditor().currentRecord().getUuid().toString());
		map.put("tierName", selectedComponent.getTier().getTierName());
		map.put("intervalIdx", 0);
		map.put("timeStep", 0.0f);
		map.put("maxFormants", 5);
		map.put("maxFormant", 5500);
		map.put("windowLength", 0.025f);
		map.put("preEmphasis", 50);
		map.put("phonsock", "localhost:" + server.getPort());
		
		final PraatScript ps = new PraatScript(FORMANTS_TEMPLATE);
		String script;
		try {
			script = ps.generateScript(map);
			
			server.startServer();
			String errVal = SendPraat.sendPraat(script);
			if(errVal != null) {
				// try to open praat
				SendPraat.openPraat();
				// wait
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {}
				
				// try again
				String val = SendPraat.sendPraat(script);
//				String val = SendPraat.sendPraatInBatch(script);
				if(val != null) {
					LOGGER.info(val);
				}
			}
		} catch (IOException e) {
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
	
	public void onRecordChanged(EditorEvent ee) {
		update();
	}
	
	private final ActionListener toggleViewerAction = new ActionListener() {
		
		@Override
		public void actionPerformed(ActionEvent arg0) {
			final boolean enabled = toggleViewerButton.isSelected();
			TextGridViewer.this.setVisible(enabled);
			openTextGridButton.setVisible(enabled);
			formantsButton.setVisible(enabled);
		}
	};
	
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
			final TextGridTierComponent comp = selectedComponent;
			selectedComponent = tierComp;
			if(selectedComponent != null) {
				formantsButton.setEnabled(true);

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
					playIntervalButton.setLocation(btnx, selectedComponent.getLocation().y);
					
					layeredPane.moveToFront(widgetPane);
					requestFocus();
				}
			} else {
				formantsButton.setEnabled(false);
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
	
	private final FocusListener focusListener = new FocusListener() {
		
		@Override
		public void focusLost(FocusEvent e) {
			widgetPane.setVisible(false);
		}
		
		@Override
		public void focusGained(FocusEvent e) {
			widgetPane.setVisible(true);
		}
	};
	
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
			widgetPane.setBounds(0, 0, width, prefSize.height);
		}
		
		@Override
		public void componentMoved(ComponentEvent e) {
		}
		
		@Override
		public void componentHidden(ComponentEvent e) {
		}
	};
}
