package ca.phon.plugins.praat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import ca.hedlund.jpraat.binding.fon.Formant;
import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.LongSound;
import ca.hedlund.jpraat.binding.fon.Pitch;
import ca.hedlund.jpraat.binding.fon.Sound;
import ca.hedlund.jpraat.binding.fon.Spectrogram;
import ca.hedlund.jpraat.binding.fon.kPitch_unit;
import ca.hedlund.jpraat.binding.stat.Table;
import ca.hedlund.jpraat.binding.sys.MelderFile;
import ca.phon.app.log.BufferPanel;
import ca.phon.app.log.BufferWindow;
import ca.phon.app.log.LogBuffer;
import ca.phon.app.session.editor.DelegateEditorAction;
import ca.phon.app.session.editor.EditorAction;
import ca.phon.app.session.editor.EditorEvent;
import ca.phon.app.session.editor.EditorEventType;
import ca.phon.app.session.editor.RunInBackground;
import ca.phon.app.session.editor.view.waveform.WaveformEditorView;
import ca.phon.app.session.editor.view.waveform.WaveformTier;
import ca.phon.app.session.editor.view.waveform.WaveformViewCalculator;
import ca.phon.media.wavdisplay.WavDisplay;
import ca.phon.plugins.praat.painters.FormantPainter;
import ca.phon.plugins.praat.painters.PitchSpecklePainter;
import ca.phon.plugins.praat.painters.SpectrogramPainter;
import ca.phon.session.MediaSegment;
import ca.phon.session.Record;
import ca.phon.session.SystemTierType;
import ca.phon.textgrid.TextGridInterval;
import ca.phon.ui.CommonModuleFrame;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.util.PrefHelper;
import ca.phon.util.icons.IconManager;
import ca.phon.util.icons.IconSize;

import com.jgoodies.forms.builder.ButtonBarBuilder;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

/**
 * Adds a spectrogram tier to the waveform editor view.
 */
public class SpectrogramViewer extends JPanel implements WaveformTier {
	
	private static final Logger LOGGER = Logger
			.getLogger(SpectrogramViewer.class.getName());
	
	private static final long serialVersionUID = -6963658315933818319L;

	private final WaveformEditorView parent;

	private SpectrogramSettings spectrogramSettings = new SpectrogramSettings();
	
	private final SpectrogramPainter spectrogramPainter = new SpectrogramPainter(spectrogramSettings);

	private FormantSettings formantSettings = new FormantSettings();
	
	private Spectrogram spectrogram;
	
	public final static String SHOW_FORMANTS_PROP = SpectrogramViewer.class.getName() + ".showFormants";
	private boolean showFormants = 
			PrefHelper.getBoolean(SHOW_FORMANTS_PROP, false);
	
	private Formant formants;
	
	private FormantPainter formantPainter = new FormantPainter();
	
	private Pitch pitch;
	
	private PitchSettings pitchSettings = new PitchSettings();
	
	private PitchSpecklePainter pitchPainter = new PitchSpecklePainter();
	
	public final static String SHOW_PITCH_PROP = SpectrogramViewer.class.getName() + ".showPitch";
	private boolean showPitch =
			PrefHelper.getBoolean(SHOW_PITCH_PROP, false);
	
	public SpectrogramViewer(WaveformEditorView p) {
		super();
		setVisible(false);
		setBackground(Color.white);
		this.parent = p;
		this.parent.addComponentListener(resizeListener);
		parent.getWavDisplay().addPropertyChangeListener(WavDisplay._SELECTION_PROP_, new PropertyChangeListener() {
			
			@Override
			public void propertyChange(PropertyChangeEvent arg0) {
				repaint();
			}
			
		});
		parent.addComponentListener(new ComponentListener() {
			
			@Override
			public void componentShown(ComponentEvent e) {
				
			}
			
			@Override
			public void componentResized(ComponentEvent e) {
				final Dimension newSize = new Dimension(parent.getWidth(), parent.getHeight() / 3);
				setPreferredSize(newSize);
			}
			
			@Override
			public void componentMoved(ComponentEvent e) {
				
			}
			
			@Override
			public void componentHidden(ComponentEvent e) {
				
			}
		});
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
		PraatMenuButton menuBtn = parent.getExtension(PraatMenuButton.class);
		if(menuBtn == null) {
			menuBtn = new PraatMenuButton(parent);
			parent.getToolbar().addSeparator();
			parent.getToolbar().add(menuBtn);
			parent.putExtension(PraatMenuButton.class, menuBtn);
		}
	}
	
	public void onToggleSpectrogram() {
		final boolean enabled = !SpectrogramViewer.this.isVisible();
		SpectrogramViewer.this.setVisible(enabled);
		if(enabled) update();
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
	
	public void onToggleFormants() {
		showFormants = !showFormants;
		PrefHelper.getUserPreferences().putBoolean(SHOW_FORMANTS_PROP, showFormants);
		update();
	}
	
	public void onEditFormantSettings() {
		final JDialog dialog = new JDialog(CommonModuleFrame.getCurrentFrame());
		dialog.setModal(true);
		
		dialog.setLayout(new BorderLayout());
		
		final DialogHeader header = new DialogHeader("Formant settings", "Edit formant settings");
		dialog.add(header, BorderLayout.NORTH);
		
		final FormantSettingsPanel settingsPanel = new FormantSettingsPanel();
		settingsPanel.loadSettings(formantSettings);
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
			formantSettings = settingsPanel.getSettings();
			update();
		}
	}
	
	public void onTogglePitch() {
		showPitch = !showPitch;
		PrefHelper.getUserPreferences().putBoolean(SHOW_PITCH_PROP, showPitch);
		update();
	}
	
	public void onEditPitchSettings() {
		final JDialog dialog = new JDialog(CommonModuleFrame.getCurrentFrame());
		dialog.setModal(true);
		
		dialog.setLayout(new BorderLayout());
		
		final DialogHeader header = new DialogHeader("Pitch settings", "Edit pitch settings");
		dialog.add(header, BorderLayout.NORTH);
		
		final PitchSettingsPanel settingsPanel = new PitchSettingsPanel();
		settingsPanel.loadSettings(pitchSettings);
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
			pitchSettings = settingsPanel.getSettings();
			update();
		}
	}
	
	public void listPitch() {
		// get selection from waveform view
		final File wavFile = parent.getAudioFile();
		if(wavFile == null || !wavFile.exists()) return;
		
		final Record r = parent.getEditor().currentRecord();
		final MediaSegment segment = 
				(r.getSegment().numberOfGroups() > 0 ? r.getSegment().getGroup(0) : null);
		if(segment == null ||
				(segment.getEndValue() - segment.getStartValue()) <= 0) {
			return;
		}
		
		final double formantStart = segment.getStartValue() / 1000.0;
		final double formantEnd = segment.getEndValue() / 1000.0;
		
		final double selStart = 
				(parent.getWavDisplay().get_selectionStart() >= 0 ? 
						parent.getWavDisplay().get_dipslayOffset() + parent.getWavDisplay().get_selectionStart()
						: segment.getStartValue()) / 1000.0;
		final double selEnd = 
				(parent.getWavDisplay().get_selectionEnd() >= 0 ? 
						parent.getWavDisplay().get_dipslayOffset() +parent.getWavDisplay().get_selectionEnd() 
						: segment.getEndValue()) / 1000.0;
		
		final LongSound ls = LongSound.open(MelderFile.fromPath(wavFile.getAbsolutePath()));
		final Sound sound = ls.extractPart(formantStart, formantEnd, 1);
		
		Pitch pitch = null;
		if(pitchSettings.isAutoCorrelate()) {
			pitch = sound.to_Pitch_ac(pitchSettings.getTimeStep(), pitchSettings.getRangeStart(), 3.0, 
					pitchSettings.getMaxCandidates(), (pitchSettings.isVeryAccurate() ? 1 : 0), pitchSettings.getSilenceThreshold(), 
					pitchSettings.getVoicingThreshold(), pitchSettings.getOctaveCost(), 
					pitchSettings.getOctaveJumpCost(), pitchSettings.getVoicedUnvoicedCost(), pitchSettings.getRangeEnd());
		} else {
			// TODO
		}
		
		if(pitch == null) return;
		
		final BufferWindow bw = BufferWindow.getInstance();
		bw.showWindow();
		final BufferPanel bufferPanel = bw.createBuffer("Formants (" + selStart + "-" + selEnd + ")");
		final LogBuffer buffer = bufferPanel.getLogBuffer();
		
		final Pointer ixminPtr = new Memory(Native.getNativeSize(Long.TYPE));
		final Pointer ixmaxPtr = new Memory(Native.getNativeSize(Long.TYPE));
		pitch.getWindowSamples(selStart, selEnd, ixminPtr, ixmaxPtr);
		
		final int xmin = (int)ixminPtr.getLong(0);
		final int xmax = (int)ixmaxPtr.getLong(0);
		
		// print header
		try {
			final PrintWriter out = 
					new PrintWriter(new OutputStreamWriter(buffer.getStdOutStream(), "UTF-8"));
			final StringBuilder sb = new StringBuilder();
			final char qc = '\"';
			final char sc = ',';
			sb.append(qc).append("Time_s").append(qc);
			sb.append(sc).append(qc).append("F0_");
			final WString unitText = pitch.getUnitText(Pitch.LEVEL_FREQUENCY, 
					pitchSettings.getUnits(), Function.UNIT_TEXT_SHORT);
			sb.append(unitText.toString()).append(qc);
			out.println(sb.toString());
			sb.setLength(0);
			
			for(int i = xmin; i <= xmax; i++) {
				double t = pitch.indexToX(i);
				double f0 = pitch.getValueAtSample(i, Pitch.LEVEL_FREQUENCY, pitchSettings.getUnits().ordinal());
				f0 = pitch.convertToNonlogarithmic(f0, Pitch.LEVEL_FREQUENCY, pitchSettings.getUnits().ordinal());
				sb.append(qc).append(t).append(qc);
				sb.append(sc).append(qc).append(f0).append(qc);
				out.println(sb.toString());
				sb.setLength(0);
			}
			
			out.flush();
			out.close();
		} catch(IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	public void listFormants() {
		// get selection from waveform view
		final File wavFile = parent.getAudioFile();
		if(wavFile == null || !wavFile.exists()) return;
		
		final Record r = parent.getEditor().currentRecord();
		final MediaSegment segment = 
				(r.getSegment().numberOfGroups() > 0 ? r.getSegment().getGroup(0) : null);
		if(segment == null ||
				(segment.getEndValue() - segment.getStartValue()) <= 0) {
			return;
		}
		
		final double formantStart = segment.getStartValue() / 1000.0;
		final double formantEnd = segment.getEndValue() / 1000.0;
		
		final double selStart = 
				(parent.getWavDisplay().get_selectionStart() >= 0 ? 
						parent.getWavDisplay().get_dipslayOffset() + parent.getWavDisplay().get_selectionStart()
						: segment.getStartValue()) / 1000.0;
		final double selEnd = 
				(parent.getWavDisplay().get_selectionEnd() >= 0 ? 
						parent.getWavDisplay().get_dipslayOffset() +parent.getWavDisplay().get_selectionEnd() 
						: segment.getEndValue()) / 1000.0;
		
		final LongSound ls = LongSound.open(MelderFile.fromPath(wavFile.getAbsolutePath()));
		final Sound sound = ls.extractPart(formantStart, formantEnd, 1);
		final Formant formants = sound.to_Formant_burg(formantSettings.getTimeStep(), 
				formantSettings.getNumFormants(), formantSettings.getMaxFrequency(), 
				formantSettings.getWindowLength(), formantSettings.getPreEmphasis());
		
		final Table formantTable = formants.downto_Table(false, true, 6, 
				formantSettings.isIncludeIntensity(), 6, formantSettings.isIncludeNumFormants(), 6, formantSettings.isIncludeBandwidths());
		
		final BufferWindow bw = BufferWindow.getInstance();
		bw.showWindow();
		final BufferPanel bufferPanel = bw.createBuffer("Formants (" + selStart + "-" + selEnd + ")");
		final LogBuffer buffer = bufferPanel.getLogBuffer();
		
		try {
			final PrintWriter out = 
					new PrintWriter(new OutputStreamWriter(buffer.getStdOutStream(), "UTF-8"));
			final char qc = '\"';
			final char sc = ',';
			
			final StringBuilder sb = new StringBuilder();
			for(int col = 1; col <= formantTable.getNcol(); col++) {
				if(col > 1) sb.append(sc);
				sb.append(qc);
				sb.append(formantTable.getColStr(col));
				sb.append(qc);
			}
			out.println(sb.toString());
			
			for(int row = 1; row <= formantTable.getNrow(); row++) {
				sb.setLength(0);
					
				final double time = formantTable.getNumericValue_Assert(row, 1);
				
				if(time > selEnd) break;
				
				if(time >= selStart) {
					for(int col = 1; col <= formantTable.getNcol(); col++) {
						if(col > 1) sb.append(sc);
						sb.append(qc);
						sb.append(formantTable.getNumericValue_Assert(row, col));
						sb.append(qc);
					}
					out.println(sb.toString());
				}
			}
			
			out.flush();
			out.close();
		} catch (UnsupportedEncodingException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
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
	
	private Pitch loadPitch() {
		final MediaSegment segment = parent.getEditor().currentRecord().getSegment().getGroup(0);
		// TODO check segment length
		final File audioFile = parent.getAudioFile();
		if(audioFile == null) return null;
		
		final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
		final Sound part = longSound.extractPart((double)segment.getStartValue()/1000.0, (double)segment.getEndValue()/1000.0, 1);
		final Pitch pitch = part.to_Pitch_ac(pitchSettings.getTimeStep(), pitchSettings.getRangeStart(), 3.0, 
				pitchSettings.getMaxCandidates(), (pitchSettings.isVeryAccurate() ? 1 : 0), pitchSettings.getSilenceThreshold(), 
				pitchSettings.getVoicingThreshold(), pitchSettings.getOctaveCost(), 
				pitchSettings.getOctaveJumpCost(), pitchSettings.getVoicedUnvoicedCost(), pitchSettings.getRangeEnd());

		return pitch;
	}
	
	private Formant loadFormants() {
		final MediaSegment segment = parent.getEditor().currentRecord().getSegment().getGroup(0);
		// TODO check segment length
		final File audioFile = parent.getAudioFile();
		if(audioFile == null) return null;
		
		final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
		final Sound part = longSound.extractPart((double)segment.getStartValue()/1000.0, (double)segment.getEndValue()/1000.0, 1);
		
		final Formant formants = 
				part.to_Formant_burg(formantSettings.getTimeStep(), formantSettings.getNumFormants(), 
						formantSettings.getMaxFrequency(), formantSettings.getWindowLength(), formantSettings.getPreEmphasis());
		return formants;
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
		spectrogram = loadSpectrogram();
		spectrogramPainter.setSettings(spectrogramSettings);
		spectrogramPainter.setValue(spectrogram);
		
		if(showFormants) {
			formants = loadFormants();
			formantPainter.setMaxFrequency(spectrogramSettings.getMaxFrequency());
			formantPainter.setSettings(formantSettings);
			formantPainter.setValue(formants);
		}
		
		if(showPitch) {
			pitch = loadPitch();
			pitchPainter.setSettings(pitchSettings);
			pitchPainter.setValue(pitch);
		}
		updateLock.unlock();
		
		final Runnable onEdt = new Runnable() {
			
			@Override
			public void run() {
				final Dimension newSize = new Dimension(parent.getWidth(), parent.getHeight() / 3);
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
		
		final Rectangle2D contentRect = new Rectangle2D.Double(
				calculator.getSegmentRect().getX(), 0, calculator.getSegmentRect().getWidth(), getHeight());
		if(spectrogram != null) {
			updateLock.lock();
			spectrogramPainter.paintInside(g2, contentRect);
			updateLock.unlock();
		}
		
		if(showFormants && formants != null) {
			updateLock.lock();
			formantPainter.paintInside(g2, contentRect);
			updateLock.unlock();
		}
		
		if(showPitch && pitch != null) {
			updateLock.lock();
			pitchPainter.paintInside(g2, contentRect);
			updateLock.unlock();
		}
		
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
		
		final PhonUIAction toggleAct = new PhonUIAction(this, "onToggleSpectrogram");
		toggleAct.putValue(PhonUIAction.NAME, "Show spectrogram");
		toggleAct.putValue(PhonUIAction.SELECTED_KEY, SpectrogramViewer.this.isVisible());
		praatMenu.add(new JCheckBoxMenuItem(toggleAct));
		
		final PhonUIAction settingsAct = new PhonUIAction(this, "onEditSettings");
		settingsAct.putValue(PhonUIAction.NAME, "Spectrogram settings...");
		settingsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Edit spectrogram settings...");
		praatMenu.add(settingsAct);
		
		// formants
		final PhonUIAction toggleFormants = new PhonUIAction(this, "onToggleFormants");
		toggleFormants.putValue(PhonUIAction.NAME, "Show Formants");
		toggleFormants.putValue(PhonUIAction.SELECTED_KEY, showFormants);
		praatMenu.add(new JCheckBoxMenuItem(toggleFormants));
		
		final PhonUIAction formantSettingsAct = new PhonUIAction(this, "onEditFormantSettings");
		formantSettingsAct.putValue(PhonUIAction.NAME, "Formant settings...");
		formantSettingsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Edit formant settings...");
		praatMenu.add(formantSettingsAct);
		
		final PhonUIAction listFormantsAct = new PhonUIAction(this, "listFormants");
		listFormantsAct.putValue(PhonUIAction.NAME, "Formant listing");
		listFormantsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "List formants for segment/selection");
		praatMenu.add(listFormantsAct);
		
		final PhonUIAction togglePitchAct = new PhonUIAction(this, "onTogglePitch");
		togglePitchAct.putValue(PhonUIAction.NAME, "Show pitch");
		togglePitchAct.putValue(PhonUIAction.SELECTED_KEY, showPitch);
		praatMenu.add(new JCheckBoxMenuItem(togglePitchAct));
		
		final PhonUIAction pitchSettingsAct = new PhonUIAction(this, "onEditPitchSettings");
		pitchSettingsAct.putValue(PhonUIAction.NAME, "Pitch settings...");
		pitchSettingsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Edit pitch settings...");
		praatMenu.add(pitchSettingsAct);
		
		final PhonUIAction listPitchAct = new PhonUIAction(this, "listPitch");
		listPitchAct.putValue(PhonUIAction.NAME, "Pitch listing");
		listPitchAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "List pitch for segment/selection");
		praatMenu.add(listPitchAct);
	}
	
}
