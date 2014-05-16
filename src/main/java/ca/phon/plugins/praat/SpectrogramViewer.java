package ca.phon.plugins.praat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Rectangle2D.Double;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import com.jgoodies.forms.builder.ButtonBarBuilder;

import ca.hedlund.jpraat.binding.fon.LongSound;
import ca.hedlund.jpraat.binding.fon.Sound;
import ca.hedlund.jpraat.binding.fon.Spectrogram;
import ca.hedlund.jpraat.binding.sys.MelderFile;
import ca.phon.app.session.editor.DelegateEditorAction;
import ca.phon.app.session.editor.EditorAction;
import ca.phon.app.session.editor.EditorEvent;
import ca.phon.app.session.editor.EditorEventType;
import ca.phon.app.session.editor.RunInBackground;
import ca.phon.app.session.editor.view.waveform.WaveformEditorView;
import ca.phon.app.session.editor.view.waveform.WaveformTier;
import ca.phon.app.session.editor.view.waveform.WaveformViewCalculator;
import ca.phon.media.wavdisplay.WavDisplay;
import ca.phon.session.MediaSegment;
import ca.phon.session.SystemTierType;
import ca.phon.ui.CommonModuleFrame;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.ui.decorations.DialogHeader;

/**
 * Adds a spectrogram tier to the waveform editor view.
 */
public class SpectrogramViewer extends JPanel implements WaveformTier {
	
	private static final long serialVersionUID = -6963658315933818319L;

	private final WaveformEditorView parent;

	private final SpectrogramDrawer spectrogramDrawer = new SpectrogramDrawer();
	
	private SpectrogramSettings spectrogramSettings = new SpectrogramSettings();
	
	public SpectrogramViewer(WaveformEditorView parent) {
		super();
		setVisible(false);
		setBackground(Color.white);
		this.parent = parent;
		this.parent.addComponentListener(resizeListener);
		parent.getWavDisplay().addPropertyChangeListener(WavDisplay._SELECTION_PROP_, new PropertyChangeListener() {
			
			@Override
			public void propertyChange(PropertyChangeEvent arg0) {
				repaint();
			}
			
		});
		
		spectrogramDrawer.setSpectrogramSettings(spectrogramSettings);
		spectrogramDrawer.setColorMap(ColorMap.getGreyscale(16));
		
		setLayout(null);
		
		update();
		setupEditorEvents();
		setupToolbar();
	}

	private void setupEditorEvents() {
		final EditorAction recordChangedAct = new DelegateEditorAction(this, "onRecordChanged");
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.RECORD_CHANGED_EVT, recordChangedAct);
		
		final EditorAction segmentChangedAct = new DelegateEditorAction(this, "onSegmentChanged");
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.TIER_CHANGE_EVT, segmentChangedAct);
	}

	private void setupToolbar() {
		final JToolBar toolbar = parent.getToolbar();
	
		final JCheckBox toggleSpectrogramBox = new JCheckBox("Spectrogram");
		toggleSpectrogramBox.setSelected(false);
		toggleSpectrogramBox.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				final boolean enabled = ((JCheckBox)e.getSource()).isSelected();
				SpectrogramViewer.this.setVisible(enabled);
				if(enabled) update();
			}
		});
		toolbar.addSeparator();
		toolbar.add(toggleSpectrogramBox);
		
		final PhonUIAction settingsAct = new PhonUIAction(this, "onEditSettings");
		settingsAct.putValue(PhonUIAction.NAME, "Spectrogram settings");
		settingsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Edit spectrogram settings...");
		toolbar.add(settingsAct);
	}
	
	public void onEditSettings() {
		final JDialog dialog = new JDialog(CommonModuleFrame.getCurrentFrame());
		dialog.setModal(true);
		
		dialog.setLayout(new BorderLayout());
		
		final DialogHeader header = new DialogHeader("Spectrogram settings", "Edit spectrogram settings");
		dialog.add(header, BorderLayout.NORTH);
		
		final SpectrogramSettingsPanel settingsPanel = new SpectrogramSettingsPanel();
		settingsPanel.loadSettings(spectrogramSettings);
		settingsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		dialog.add(settingsPanel, BorderLayout.CENTER);
		
		final AtomicBoolean wasCanceled = new AtomicBoolean(false);
		final JButton okButton = new JButton("Ok");
		okButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				wasCanceled.getAndSet(false);
				dialog.setVisible(false);
			}
			
		});
		final JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				wasCanceled.getAndSet(true);
				dialog.setVisible(false);
			}
		});
		
		dialog.getRootPane().setDefaultButton(okButton);
		okButton.requestFocusInWindow();
		
		final ButtonBarBuilder builder = new ButtonBarBuilder();
//		builder.setLeftToRight(false);
		builder.addButton(okButton).addButton(cancelButton);
		dialog.add(builder.build(), BorderLayout.SOUTH);
		
		dialog.pack();
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
		
		// ... wait, it's modal
		
		if(!wasCanceled.get()) {
			spectrogramSettings = settingsPanel.getSettings();
			update();
		}
	}
	
	private Spectrogram loadSpectrogram() {
		final MediaSegment segment = parent.getEditor().currentRecord().getSegment().getGroup(0);
		// TODO check segment length
		final File audioFile = parent.getAudioFile();
		if(audioFile == null) return null;
		
		final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
		final Sound part = longSound.extractPart((double)segment.getStartValue()/1000.0, (double)segment.getEndValue()/1000.0, 1);
		final Spectrogram spectrogram = part.toSpectrogram(
				spectrogramSettings.getWindowLength(), spectrogramSettings.getMaxFrequency(),
				spectrogramSettings.getTimeStep(), spectrogramSettings.getFrequencyStep(), 
				spectrogramSettings.getWindowShape(), 8.0, 8.0);
		return spectrogram;
	}
	
	@RunInBackground(newThread=true)
	public void onRecordChanged(EditorEvent ee) {
		update();
	}
	
	@RunInBackground(newThread=true)
	public void onSegmentChanged(EditorEvent ee) {
		if(ee.getEventData() != null && ee.getEventData().toString().equals(SystemTierType.Segment.getName()))
			update();
	}
	
	private final ReentrantLock updateLock = new ReentrantLock();
	public void update() {
		if(!isVisible()) return;
		updateLock.lock();
		final Spectrogram spectrogramData = loadSpectrogram();
		spectrogramDrawer.setData(spectrogramData);
		spectrogramDrawer.setSpectrogramSettings(spectrogramSettings);
		spectrogramDrawer.computeSpectrogram();
		updateLock.unlock();
		
		final Runnable onEdt = new Runnable() {
			
			@Override
			public void run() {
				final Dimension newSize = new Dimension(parent.getWidth(), spectrogramDrawer.getDataHeight());
				setPreferredSize(newSize);
				
				revalidate();
				repaint();
			}
			
		};
		if(SwingUtilities.isEventDispatchThread())
			onEdt.run();
		else
			SwingUtilities.invokeLater(onEdt);
	}

	@Override
	public JComponent getTierComponent() {
		return this;
	}
	
	@Override
	public void paintComponent(Graphics g) {
		final Graphics2D g2 = (Graphics2D)g;
		
		super.paintComponent(g2);
		
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		
		final WaveformViewCalculator calculator = parent.getCalculator();
		final int height = getHeight();
		final Rectangle2D leftInsetRect = calculator.getLeftInsetRect();
		leftInsetRect.setRect(
				leftInsetRect.getX(), 0.0, leftInsetRect.getWidth(), height);
		final Rectangle2D rightInsetRect = calculator.getRightInsetRect();
		rightInsetRect.setRect(
				rightInsetRect.getX(), 0.0, rightInsetRect.getWidth(), height);
		
		updateLock.lock();
		final Rectangle2D spectrogramRect = new Rectangle2D.Double(
				calculator.getSegmentRect().getX(), 0, calculator.getSegmentRect().getWidth(), getHeight());
		spectrogramDrawer.paint(g2, spectrogramRect);
		updateLock.unlock();
		
		final WavDisplay wavDisplay = parent.getWavDisplay();
		if(wavDisplay.get_selectionStart() >= 0
				&& wavDisplay.get_selectionEnd() >= 0) {
			Color selColor = new Color(50, 125, 200, 100);
			g2.setColor(selColor);
			
			double msPerPixel = (wavDisplay.get_timeBar().getEndMs() - wavDisplay.get_timeBar().getStartMs()) / 
					(double)(getWidth() - 2 * WavDisplay._TIME_INSETS_);
			
			// convert time values to x positions
			double startXPos = wavDisplay.get_selectionStart() / msPerPixel;
			double endXPos = wavDisplay.get_selectionEnd() / msPerPixel;
			double xPos = 
				Math.min(startXPos, endXPos) + WavDisplay._TIME_INSETS_;
			double rectLen = 
				Math.abs(endXPos - startXPos);
			
			Rectangle2D selRect =
				new Rectangle2D.Double(xPos, 0,
						rectLen, getHeight());
			g2.fill(selRect);
		}
		
		g2.setColor(new Color(200, 200, 200, 100));
		g2.fill(leftInsetRect);
		g2.fill(rightInsetRect);
	}
	
	private final ComponentListener resizeListener = new ComponentListener() {
		
		@Override
		public void componentShown(ComponentEvent e) {
		}
		
		@Override
		public void componentResized(ComponentEvent e) {
//			if(spectrogramPanel == null) return;
//			final WaveformViewCalculator calculator = parent.getCalculator();
//			
//			final Rectangle2D segRect = calculator.getSegmentRect();
//			
//			int x = (segRect.getX() != Float.POSITIVE_INFINITY ? (int)segRect.getX() : 0);
//			int y = 0;
//			
//			float hscale = 1.0f;
//			float vscale = 1.0f;
//			
//			if(segRect.getWidth() > 0) {
//				hscale = (float)(segRect.getWidth() / (float)spectrogramPanel.getDataWidth());
//				spectrogramPanel.setZoom(hscale, vscale);
//			}
//			
//			spectrogramPanel.setBounds(
//					x, y,
//					spectrogramPanel.getPreferredSize().width, spectrogramPanel.getPreferredSize().height);
//			setPreferredSize(
//					new Dimension(parent.getWidth(), spectrogramPanel.getPreferredSize().height));
//			setSize(new Dimension(parent.getWidth(), spectrogramPanel.getPreferredSize().height));
//			revalidate();repaint();
		}
		
		@Override
		public void componentMoved(ComponentEvent e) {
		}
		
		@Override
		public void componentHidden(ComponentEvent e) {
		}
	};

}
