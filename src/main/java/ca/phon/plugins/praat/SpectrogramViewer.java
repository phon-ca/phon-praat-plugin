package ca.phon.plugins.praat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
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
import java.text.NumberFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import ca.hedlund.jpraat.binding.fon.Formant;
import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.Intensity;
import ca.hedlund.jpraat.binding.fon.LongSound;
import ca.hedlund.jpraat.binding.fon.Pitch;
import ca.hedlund.jpraat.binding.fon.Sound;
import ca.hedlund.jpraat.binding.fon.Spectrogram;
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
import ca.phon.media.wavdisplay.WavDisplay.MouseTimeListener;
import ca.phon.plugins.praat.painters.FormantPainter;
import ca.phon.plugins.praat.painters.IntensityPainter;
import ca.phon.plugins.praat.painters.PitchSpecklePainter;
import ca.phon.plugins.praat.painters.SpectrogramPainter;
import ca.phon.session.MediaSegment;
import ca.phon.session.Record;
import ca.phon.session.SystemTierType;
import ca.phon.session.Tier;
import ca.phon.ui.CommonModuleFrame;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.layout.ButtonBarBuilder;
import ca.phon.util.PrefHelper;
import ca.phon.util.icons.IconManager;
import ca.phon.util.icons.IconSize;
import ca.phon.worker.PhonTask;
import ca.phon.worker.PhonTaskListener;
import ca.phon.worker.PhonTask.TaskStatus;

import com.sun.jna.WString;

/**
 * Adds a spectrogram tier to the waveform editor view.
 */
public class SpectrogramViewer extends JPanel implements WaveformTier {
	
	private static final Logger LOGGER = Logger
			.getLogger(SpectrogramViewer.class.getName());
	
	private static final long serialVersionUID = -6963658315933818319L;

	private final WaveformEditorView parent;
	
	public final static String SHOW_SPECTROGRAM_PROP = SpectrogramViewer.class.getName() + ".showSpectrogram";
	private boolean showSpectrogram =
			PrefHelper.getBoolean(SHOW_SPECTROGRAM_PROP, false);

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

	private Intensity intensity;
	
	private IntensitySettings intensitySettings = new IntensitySettings();
	
	private IntensityPainter intensityPainter = new IntensityPainter();
	
	public final static String SHOW_INTENSITY_PROP = SpectrogramViewer.class.getName() + ".showIntensity";
	private boolean showIntensity =
			PrefHelper.getBoolean(SHOW_INTENSITY_PROP, false);
	
	public SpectrogramViewer(WaveformEditorView p) {
		super();
		setVisible(showSpectrogram);
		setBackground(Color.white);
		this.parent = p;
		parent.getWavDisplay().addPropertyChangeListener(WavDisplay._SELECTION_PROP_, new PropertyChangeListener() {
			
			@Override
			public void propertyChange(PropertyChangeEvent arg0) {
				repaint();
			}
			
		});
		setLayout(null);
		setPreferredSize(new Dimension(Integer.MAX_VALUE, (int)(spectrogramSettings.getMaxFrequency() / 20)));
		
		final MouseTimeListener mtl = p.getWavDisplay().createMouseTimeListener();
		addMouseListener(mtl);
		addMouseMotionListener(mtl);
		
		setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		
		setupEditorEvents();
		setupToolbar();
	}
	
	@Override
	public void addNotify() {
		super.addNotify();
		update();
	}

	private void setupEditorEvents() {
		final EditorAction recordChangedAct = new DelegateEditorAction(this, "onRecordChanged");
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.RECORD_CHANGED_EVT, recordChangedAct);
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.RECORD_REFRESH_EVT, recordChangedAct);
		
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
		showSpectrogram = !showSpectrogram;
		PrefHelper.getUserPreferences().putBoolean(SHOW_SPECTROGRAM_PROP, showSpectrogram);
		SpectrogramViewer.this.setVisible(showSpectrogram);
		if(showSpectrogram) update();
	}
	
	public void onEditSettings() {
		final AtomicBoolean wasCanceled = new AtomicBoolean(false);
		final PhonTask onEDT = new PhonTask() {
			
			@Override
			public void performTask() {
				setStatus(TaskStatus.RUNNING);
				
				final JDialog dialog = new JDialog(CommonModuleFrame.getCurrentFrame());
				dialog.setModal(true);
				
				dialog.setLayout(new BorderLayout());
				
				final DialogHeader header = new DialogHeader("Spectrogram settings", "Edit spectrogram settings");
				dialog.add(header, BorderLayout.NORTH);
				
				final SpectrogramSettingsPanel settingsPanel = new SpectrogramSettingsPanel();
				settingsPanel.loadSettings(spectrogramSettings);
				settingsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
				dialog.add(settingsPanel, BorderLayout.CENTER);
				
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
				
				dialog.add(ButtonBarBuilder.buildOkCancelBar(okButton, cancelButton), 
						BorderLayout.SOUTH);
				
				dialog.pack();
				dialog.setLocationRelativeTo(parent);
				dialog.setVisible(true);
				
				if(!wasCanceled.get()) {
					spectrogramSettings = settingsPanel.getSettings();
				}
				
				setStatus(TaskStatus.FINISHED);
			}
		};
		onEDT.addTaskListener(new PhonTaskListener() {
			
			@Override
			public void statusChanged(PhonTask task, TaskStatus oldStatus,
					TaskStatus newStatus) {
				if(newStatus != TaskStatus.RUNNING) {
					if(!wasCanceled.get()) {
						update();
					}
				}
			}
			
			@Override
			public void propertyChanged(PhonTask task, String property,
					Object oldValue, Object newValue) {
			}
		});
		if(SwingUtilities.isEventDispatchThread())
			onEDT.run();
		else
			SwingUtilities.invokeLater(onEDT);
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
		
		dialog.add(ButtonBarBuilder.buildOkCancelBar(okButton, cancelButton), 
				BorderLayout.SOUTH);
		
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
		
		dialog.add(ButtonBarBuilder.buildOkCancelBar(okButton, cancelButton), 
				BorderLayout.SOUTH);
		
		dialog.pack();
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
		
		// ... wait, it's modal
		
		if(!wasCanceled.get()) {
			pitchSettings = settingsPanel.getSettings();
			update();
		}
	}
	
	public void onToggleIntensity() {
		showIntensity = !showIntensity;
		PrefHelper.getUserPreferences().putBoolean(SHOW_INTENSITY_PROP, showIntensity);
		update();
	}
	
	public void onEditIntensitySettings() {
		final JDialog dialog = new JDialog(CommonModuleFrame.getCurrentFrame());
		dialog.setModal(true);
		
		dialog.setLayout(new BorderLayout());
		
		final DialogHeader header = new DialogHeader("Intensity settings", "Edit intensity settings");
		dialog.add(header, BorderLayout.NORTH);
		
		final IntensitySettingsPanel settingsPanel = new IntensitySettingsPanel();
		settingsPanel.loadSettings(intensitySettings);
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
		
		dialog.add(ButtonBarBuilder.buildOkCancelBar(okButton, cancelButton), 
				BorderLayout.SOUTH);
		
		dialog.pack();
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
		
		// ... wait, it's modal
		
		if(!wasCanceled.get()) {
			intensitySettings = settingsPanel.getSettings();
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
		
		double selStart = 
				(parent.getWavDisplay().get_selectionStart() >= 0 ? 
						parent.getWavDisplay().get_dipslayOffset() + parent.getWavDisplay().get_selectionStart()
						: segment.getStartValue()) / 1000.0;
		double selEnd = 
				(parent.getWavDisplay().get_selectionEnd() >= 0 ? 
						parent.getWavDisplay().get_dipslayOffset() +parent.getWavDisplay().get_selectionEnd() 
						: segment.getEndValue()) / 1000.0;
		
		if(selEnd < selStart) {
			double temp = selStart;
			selStart = selEnd;
			selEnd = temp;
		}
		
		final NumberFormat format = NumberFormat.getNumberInstance();
		format.setMaximumFractionDigits(6);
		
		final LongSound ls = LongSound.open(MelderFile.fromPath(wavFile.getAbsolutePath()));
		final Sound sound = ls.extractPart(formantStart, formantEnd, 1);
		
		Pitch pitch = null;
		if(pitchSettings.isAutoCorrelate()) {
			pitch = sound.to_Pitch_ac(pitchSettings.getTimeStep(), pitchSettings.getRangeStart(), 3.0, 
					pitchSettings.getMaxCandidates(), (pitchSettings.isVeryAccurate() ? 1 : 0), pitchSettings.getSilenceThreshold(), 
					pitchSettings.getVoicingThreshold(), pitchSettings.getOctaveCost(), 
					pitchSettings.getOctaveJumpCost(), pitchSettings.getVoicedUnvoicedCost(), pitchSettings.getRangeEnd());
		} else {
			pitch = sound.to_Pitch_cc(pitchSettings.getTimeStep(), pitchSettings.getRangeStart(), 3.0, 
					pitchSettings.getMaxCandidates(), (pitchSettings.isVeryAccurate() ? 1 : 0), pitchSettings.getSilenceThreshold(), 
					pitchSettings.getVoicingThreshold(), pitchSettings.getOctaveCost(), 
					pitchSettings.getOctaveJumpCost(), pitchSettings.getVoicedUnvoicedCost(), pitchSettings.getRangeEnd());
		}
		
		if(pitch == null) return;
		
		final BufferWindow bw = BufferWindow.getInstance();
		bw.showWindow();
		final BufferPanel bufferPanel = bw.createBuffer("Pitch (" + 
				format.format(selStart) + "-" + format.format(selEnd) + ")");
		final LogBuffer buffer = bufferPanel.getLogBuffer();
		
		final AtomicReference<Long> ixminPtr = new AtomicReference<Long>();
		final AtomicReference<Long> ixmaxPtr = new AtomicReference<Long>();
		
		pitch.getWindowSamples(selStart, selEnd, ixminPtr, ixmaxPtr);
		
		final int xmin = ixminPtr.get().intValue();
		final int xmax = ixmaxPtr.get().intValue();
		
		// print header
		try {
			final PrintWriter out = 
					new PrintWriter(new OutputStreamWriter(buffer.getStdOutStream(), "UTF-8"));
			out.flush();
			out.print(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.SHOW_BUSY);
			out.flush();
			final StringBuilder sb = new StringBuilder();
			final char qc = '\"';
			final char sc = ',';
			sb.append(qc).append("Time(s)").append(qc);
			sb.append(sc).append(qc).append("F0(");
			final WString unitText = pitch.getUnitText(Pitch.LEVEL_FREQUENCY, 
					pitchSettings.getUnits(), Function.UNIT_TEXT_SHORT);
			sb.append(unitText.toString()).append(')').append(qc);
			out.println(sb.toString());
			sb.setLength(0);
			
			for(int i = xmin; i <= xmax; i++) {
				double t = pitch.indexToX(i);
				double f0 = pitch.getValueAtSample(i, Pitch.LEVEL_FREQUENCY, pitchSettings.getUnits().ordinal());
				f0 = pitch.convertToNonlogarithmic(f0, Pitch.LEVEL_FREQUENCY, pitchSettings.getUnits().ordinal());
				sb.append(qc).append(format.format(t)).append(qc);
				sb.append(sc).append(qc).append(format.format(f0)).append(qc);
				out.println(sb.toString());
				sb.setLength(0);
			}
			
			out.flush();
			out.print(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.STOP_BUSY);
			out.flush();
			out.print(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.SHOW_TABLE_CODE);
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
		
		double selStart = 
				(parent.getWavDisplay().get_selectionStart() >= 0 ? 
						parent.getWavDisplay().get_dipslayOffset() + parent.getWavDisplay().get_selectionStart()
						: segment.getStartValue()) / 1000.0;
		double selEnd = 
				(parent.getWavDisplay().get_selectionEnd() >= 0 ? 
						parent.getWavDisplay().get_dipslayOffset() +parent.getWavDisplay().get_selectionEnd() 
						: segment.getEndValue()) / 1000.0;
		if(selEnd < selStart) {
			double temp = selStart;
			selStart = selEnd;
			selEnd = temp;
		}
		
		final NumberFormat format = NumberFormat.getNumberInstance();
		format.setMaximumFractionDigits(6);
		
		final LongSound ls = LongSound.open(MelderFile.fromPath(wavFile.getAbsolutePath()));
		final Sound sound = ls.extractPart(formantStart, formantEnd, 1);
		final Formant formants = sound.to_Formant_burg(formantSettings.getTimeStep(), 
				formantSettings.getNumFormants(), formantSettings.getMaxFrequency(), 
				formantSettings.getWindowLength(), formantSettings.getPreEmphasis());
		
		final Table formantTable = formants.downto_Table(false, true, 6, 
				formantSettings.isIncludeIntensity(), 6, formantSettings.isIncludeNumFormants(), 6, formantSettings.isIncludeBandwidths());
		
		final BufferWindow bw = BufferWindow.getInstance();
		bw.showWindow();
		final BufferPanel bufferPanel = bw.createBuffer("Formants (" + 
				format.format(selStart) + "-" + format.format(selEnd) + ")");
		final LogBuffer buffer = bufferPanel.getLogBuffer();
		
		try {
			final PrintWriter out = 
					new PrintWriter(new OutputStreamWriter(buffer.getStdOutStream(), "UTF-8"));
			out.flush();
			out.print(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.SHOW_BUSY);
			out.flush();
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
						sb.append(format.format(formantTable.getNumericValue_Assert(row, col)));
						sb.append(qc);
					}
					out.println(sb.toString());
				}
			}
			
			out.flush();
			out.print(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.STOP_BUSY);
			out.flush();
			out.print(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.SHOW_TABLE_CODE);
			out.flush();
			out.close();
		} catch (UnsupportedEncodingException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		
	}
	
	public void listIntensity() {
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
		
		final double start = segment.getStartValue() / 1000.0;
		final double end = segment.getEndValue() / 1000.0;
		
		double selStart = 
				(parent.getWavDisplay().get_selectionStart() >= 0 ? 
						parent.getWavDisplay().get_dipslayOffset() + parent.getWavDisplay().get_selectionStart()
						: segment.getStartValue()) / 1000.0;
		double selEnd = 
				(parent.getWavDisplay().get_selectionEnd() >= 0 ? 
						parent.getWavDisplay().get_dipslayOffset() +parent.getWavDisplay().get_selectionEnd() 
						: segment.getEndValue()) / 1000.0;
		if(selEnd < selStart) {
			double temp = selStart;
			selStart = selEnd;
			selEnd = temp;
		}
		
		final NumberFormat format = NumberFormat.getNumberInstance();
		format.setMaximumFractionDigits(6);
		
		final LongSound ls = LongSound.open(MelderFile.fromPath(wavFile.getAbsolutePath()));
		final Sound sound = ls.extractPart(start, end, 1);
		
		final Intensity intensity = sound.to_Intensity(intensitySettings.getViewRangeMin(), 
				0.0, intensitySettings.getSubtractMean() ? 1 : 0);
		
		final AtomicReference<Long> ixminRef = new AtomicReference<Long>();
		final AtomicReference<Long> ixmaxRef = new AtomicReference<Long>();
		
		intensity.getWindowSamples(selStart, selEnd, ixminRef, ixmaxRef);
		
		final int ixmin = ixminRef.get().intValue();
		final int ixmax = ixmaxRef.get().intValue();
		
		final BufferWindow bw = BufferWindow.getInstance();
		bw.showWindow();
		final BufferPanel bufferPanel = bw.createBuffer("Intensity (" + 
				format.format(selStart) + "-" + format.format(selEnd) + ")");
		final LogBuffer buffer = bufferPanel.getLogBuffer();
		
		try {
			final PrintWriter out = 
					new PrintWriter(new OutputStreamWriter(buffer.getStdOutStream(), "UTF-8"));
			out.flush();
			out.print(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.SHOW_BUSY);
			out.flush();
			final char qc = '\"';
			final char sc = ',';
			
			final StringBuilder sb = new StringBuilder();
			sb.append(qc).append("Time(s)").append(qc).append(sc);
			sb.append(qc).append("Intensity(dB)").append(qc);
			out.println(sb.toString());
			sb.setLength(0);
			
			for(int i = ixmin; i < ixmax; i++) {
				final double time = intensity.indexToX(i);
				final double val = intensity.getValueAtSample(i, 1, Intensity.UNITS_DB);
				
				sb.append(qc).append(format.format(time)).append(qc).append(sc);
				sb.append(qc).append(format.format(val)).append(qc);
				out.println(sb.toString());
				sb.setLength(0);
			}
			
			out.flush();
			out.print(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.STOP_BUSY);
			out.flush();
			out.print(LogBuffer.ESCAPE_CODE_PREFIX + BufferPanel.SHOW_TABLE_CODE);
			out.flush();
			out.close();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	private MediaSegment getSegment() {
		final Tier<MediaSegment> segTier = parent.getEditor().currentRecord().getSegment();
		return (segTier.numberOfGroups() == 1 ? segTier.getGroup(0) : null);
	}
	
	private Spectrogram loadSpectrogram() {
		final MediaSegment segment = getSegment();
		if(segment == null || segment.getEndValue() - segment.getStartValue() <= 0.0f) {
			return null;
		}
		final File audioFile = parent.getAudioFile();
		if(audioFile == null) return null;
		
		final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
		final Sound part = longSound.extractPart((double)segment.getStartValue()/1000.0,
				(double)segment.getEndValue()/1000.0, 1);
		
		final Spectrogram spectrogram = part.toSpectrogram(
				spectrogramSettings.getWindowLength(), spectrogramSettings.getMaxFrequency(),
				spectrogramSettings.getTimeStep(), spectrogramSettings.getFrequencyStep(), 
				spectrogramSettings.getWindowShape(), 8.0, 8.0);
		return spectrogram;
	}
	
	private Pitch loadPitch() {
		final MediaSegment segment = getSegment();
		if(segment == null || segment.getEndValue() - segment.getStartValue() <= 0.0f) {
			return null;
		}
		final File audioFile = parent.getAudioFile();
		if(audioFile == null) return null;
		
		final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
		final Sound part = longSound.extractPart((double)segment.getStartValue()/1000.0, (double)segment.getEndValue()/1000.0, 1);
		
		Pitch pitch = null;
		if(pitchSettings.isAutoCorrelate()) {
			pitch = part.to_Pitch_ac(pitchSettings.getTimeStep(), pitchSettings.getRangeStart(), 3.0, 
				pitchSettings.getMaxCandidates(), (pitchSettings.isVeryAccurate() ? 1 : 0), pitchSettings.getSilenceThreshold(), 
				pitchSettings.getVoicingThreshold(), pitchSettings.getOctaveCost(), 
				pitchSettings.getOctaveJumpCost(), pitchSettings.getVoicedUnvoicedCost(), pitchSettings.getRangeEnd());
		} else {
			pitch = part.to_Pitch_cc(pitchSettings.getTimeStep(), pitchSettings.getRangeStart(), 3.0, 
				pitchSettings.getMaxCandidates(), (pitchSettings.isVeryAccurate() ? 1 : 0), pitchSettings.getSilenceThreshold(), 
				pitchSettings.getVoicingThreshold(), pitchSettings.getOctaveCost(), 
				pitchSettings.getOctaveJumpCost(), pitchSettings.getVoicedUnvoicedCost(), pitchSettings.getRangeEnd());
		}

		return pitch;
	}
	
	private Formant loadFormants() {
		final MediaSegment segment = getSegment();
		if(segment == null || segment.getEndValue() - segment.getStartValue() <= 0.0f) {
			return null;
		}
		final File audioFile = parent.getAudioFile();
		if(audioFile == null) return null;
		
		final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
		final Sound part = longSound.extractPart((double)segment.getStartValue()/1000.0, (double)segment.getEndValue()/1000.0, 1);
		
		final Formant formants = 
				part.to_Formant_burg(formantSettings.getTimeStep(), formantSettings.getNumFormants(), 
						formantSettings.getMaxFrequency(), formantSettings.getWindowLength(), formantSettings.getPreEmphasis());
		return formants;
	}
	
	private Intensity loadIntensity() {
		final MediaSegment segment = getSegment();
		if(segment == null || segment.getEndValue() - segment.getStartValue() <= 0.0f) {
			return null;
		}
		final File audioFile = parent.getAudioFile();
		if(audioFile == null) return null;
		
		final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
		final Sound part = longSound.extractPart((double)segment.getStartValue()/1000.0, (double)segment.getEndValue()/1000.0, 1);
		
		final double windowLen = (segment.getEndValue() / 1000.0 - segment.getStartValue() / 1000.0) / 100.0;
		final Intensity intensity =
				part.to_Intensity(pitchSettings.getRangeStart(),
						0.0,
						(intensitySettings.getSubtractMean() ? 1 : 0));
		return intensity;
	}
	
	@RunInBackground(newThread=true)
	public void onRecordChanged(EditorEvent ee) {
		if(!isVisible() || !parent.getEditor().getViewModel().isShowing(WaveformEditorView.VIEW_TITLE)) return;
		update();
	}
	
	@RunInBackground(newThread=true)
	public void onSegmentChanged(EditorEvent ee) {
		if(!isVisible() || !parent.getEditor().getViewModel().isShowing(WaveformEditorView.VIEW_TITLE)) return;
		if(ee.getEventData() != null && ee.getEventData().toString().equals(SystemTierType.Segment.getName()))
			update();
	}
	
	private final ReentrantLock updateLock = new ReentrantLock();
	public void update() {
		if(!isVisible()) return;
		updateLock.lock();
		
		try {
			spectrogram = loadSpectrogram();
		} catch (IllegalArgumentException e) {
			spectrogram = null;
			
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		spectrogramPainter.setSettings(spectrogramSettings);
		spectrogramPainter.setValue(spectrogram);
		
		if(showFormants) {
			try {
				formants = loadFormants();
			} catch (IllegalArgumentException e) {
				formants = null;
				
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
			formantPainter.setMaxFrequency(spectrogramSettings.getMaxFrequency());
			formantPainter.setSettings(formantSettings);
			formantPainter.setValue(formants);
		}
		
		if(showPitch) {
			try {
				pitch = loadPitch();
			} catch (IllegalArgumentException e) {
				pitch = null;
				
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
			pitchPainter.setSettings(pitchSettings);
			pitchPainter.setValue(pitch);
		}
		
		if(showIntensity) {
			try {
				intensity = loadIntensity();
			} catch (IllegalArgumentException e) {
				intensity = null;
				
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
			intensityPainter.setSettings(intensitySettings);
			intensityPainter.setValue(intensity);
		}
		updateLock.unlock();
		
		
		final Runnable onEdt = new Runnable() {
			
			@Override
			public void run() {
				if(spectrogram != null) {
					final Dimension newSize = new Dimension(
							parent.getWidth(), (int)spectrogram.getNy() * 2);
					setPreferredSize(newSize);
				}
				
				revalidate();
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
		
		if((int)contentRect.getWidth() == 0
				|| (int)contentRect.getHeight() == 0) {
			return;
		}
		
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
		
		if(showIntensity && intensity != null) {
			updateLock.lock();
			intensityPainter.paintInside(g2, contentRect);
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
		
		final Color sideColor = new Color(200,200,200,100);
		g2.setColor(sideColor);
		g2.fill(leftInsetRect);
		if(spectrogram != null) {
			updateLock.lock();
			spectrogramPainter.paintGarnish(g2, leftInsetRect, SwingConstants.LEFT);
			updateLock.unlock();
		}

		g2.setColor(sideColor);
		g2.fill(rightInsetRect);
		if(showPitch && pitch != null) {
			updateLock.lock();
			pitchPainter.paintGarnish(g2, rightInsetRect, SwingConstants.RIGHT);
			updateLock.unlock();
		}
		
		
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
		
		final PhonUIAction toggleAct = new PhonUIAction(this, "onToggleSpectrogram");
		toggleAct.setRunInBackground(true);
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
		
		final PhonUIAction toggleIntensityAct = new PhonUIAction(this, "onToggleIntensity");
		toggleIntensityAct.putValue(PhonUIAction.NAME, "Show intensity");
		toggleIntensityAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Show intensity");
		final JCheckBoxMenuItem toggleIntensityItem = new JCheckBoxMenuItem(toggleIntensityAct);
		toggleIntensityItem.setSelected(showIntensity);
		praatMenu.add(toggleIntensityItem);
		
		final PhonUIAction intensitySettingsAct = new PhonUIAction(this, "onEditIntensitySettings");
		intensitySettingsAct.putValue(PhonUIAction.NAME, "Intensity settings...");
		intensitySettingsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Edit intensity settings...");
		praatMenu.add(intensitySettingsAct);
		
		final PhonUIAction listIntensityAct = new PhonUIAction(this, "listIntensity");
		listIntensityAct.putValue(PhonUIAction.NAME, "Intensity listing");
		listIntensityAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "List intensity for segment/selection");
		praatMenu.add(listIntensityAct);
	}
	
}
