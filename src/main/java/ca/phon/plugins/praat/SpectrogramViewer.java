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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Line2D;
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
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;

import ca.hedlund.jpraat.binding.fon.Formant;
import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.Intensity;
import ca.hedlund.jpraat.binding.fon.LongSound;
import ca.hedlund.jpraat.binding.fon.Pitch;
import ca.hedlund.jpraat.binding.fon.Sound;
import ca.hedlund.jpraat.binding.fon.Spectrogram;
import ca.hedlund.jpraat.binding.jna.Str32;
import ca.hedlund.jpraat.binding.stat.Table;
import ca.hedlund.jpraat.binding.sys.MelderFile;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.app.log.BufferPanel;
import ca.phon.app.log.BufferWindow;
import ca.phon.app.log.LogBuffer;
import ca.phon.app.session.editor.DelegateEditorAction;
import ca.phon.app.session.editor.EditorAction;
import ca.phon.app.session.editor.EditorEvent;
import ca.phon.app.session.editor.EditorEventType;
import ca.phon.app.session.editor.RunInBackground;
import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisDivider;
import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisEditorView;
import ca.phon.app.session.editor.view.speech_analysis.SpeechAnalysisTier;
import ca.phon.media.sampled.PCMSegmentView;
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
import ca.phon.ui.toast.ToastFactory;
import ca.phon.util.PrefHelper;
import ca.phon.util.icons.IconManager;
import ca.phon.util.icons.IconSize;
import ca.phon.worker.PhonTask;
import ca.phon.worker.PhonTask.TaskStatus;
import ca.phon.worker.PhonTaskListener;

import com.sun.jna.WString;

/**
 * Adds a spectrogram tier to the waveform editor view.
 */
public class SpectrogramViewer extends JPanel implements SpeechAnalysisTier {
	
	private static final Logger LOGGER = Logger
			.getLogger(SpectrogramViewer.class.getName());
	
	private static final long serialVersionUID = -6963658315933818319L;

	private final SpeechAnalysisEditorView parent;
	
	private SpectrogramPanel contentPane;
	
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
	
	private Point currentPoint = null;
	
	private final static String DISPLAY_HEIGHT = "SpectrogramViewer.displayHeight";
	private JComponent sizer;
	private int displayHeight = 
			PrefHelper.getInt(DISPLAY_HEIGHT, -1);
	
	public SpectrogramViewer(SpeechAnalysisEditorView p) {
		super();
		setVisible(showSpectrogram);
		this.parent = p;
		setBackground(parent.getWavDisplay().getExcludedColor());
		
		final PropertyChangeListener displayListener = new PropertyChangeListener() {
			
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				repaint();
			}
		};
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.WINDOW_START_PROT, displayListener);
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.WINDOW_LENGTH_PROP, displayListener);
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.CURSOR_LOCATION_PROP, displayListener);
		parent.getWavDisplay().addPropertyChangeListener(PCMSegmentView.SELECTION_LENGTH_PROP, displayListener);
		
		setLayout(new BorderLayout());
		contentPane = new SpectrogramPanel();
		if(displayHeight < 0) {
			displayHeight = (int)Math.ceil(spectrogramSettings.getMaxFrequency()  / 40);
		}
		Dimension prefSize = contentPane.getPreferredSize();
		prefSize.height = displayHeight;
		contentPane.setPreferredSize(prefSize);
		
		add(contentPane, BorderLayout.CENTER);
		sizer = new SpeechAnalysisDivider();
		sizer.addMouseMotionListener(new MouseAdapter() {

			boolean changingSize = false;
			@Override
			public void mouseDragged(MouseEvent e) {
				final Dimension currentSize = contentPane.getSize();
				
				final Dimension prefSize = contentPane.getPreferredSize();
				prefSize.height = currentSize.height + e.getY();
				if(prefSize.height < 0) prefSize.height = 0;
				
				displayHeight = prefSize.height;
				
				PrefHelper.getUserPreferences().putInt(DISPLAY_HEIGHT, displayHeight);
				
				contentPane.setPreferredSize(prefSize);
				contentPane.revalidate();
			}

			@Override
			public void mousePressed(MouseEvent e) {
			}

			@Override
			public void mouseReleased(MouseEvent e) {
			}
			
		});
		add(sizer, BorderLayout.SOUTH);
		
//		final MouseTimeListener mtl = p.getWavDisplay().createMouseTimeListener();
		addMouseListener(selectionListener);
		addMouseMotionListener(selectionListener);
		addMouseListener(pointListener);
		
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
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.SESSION_MEDIA_CHANGED, recordChangedAct);
		
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
					spectrogramPainter.setRepaintBuffer(true);
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
			formantPainter.setRepaintBuffer(true);
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
			pitchPainter.setRepaintBuffer(true);
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
			intensityPainter.setRepaintBuffer(true);
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
				(parent.getWavDisplay().hasSelection() ? 
						parent.getWavDisplay().getSelectionStart()
						: segment.getStartValue() / 1000.0);
		double selEnd = 
				(parent.getWavDisplay().hasSelection() ? 
						selStart + parent.getWavDisplay().getSelectionLength()
						: segment.getEndValue() / 1000.0);
		
		if(selEnd < selStart) {
			double temp = selStart;
			selStart = selEnd;
			selEnd = temp;
		}
		
		final NumberFormat format = NumberFormat.getNumberInstance();
		format.setMaximumFractionDigits(6);
		
		Sound sound = null;
		try {
			final LongSound ls = LongSound.open(MelderFile.fromPath(wavFile.getAbsolutePath()));
			sound = ls.extractPart(formantStart, formantEnd, 1);
		} catch (PraatException pe) {
			LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
			return;
		}
		
		Pitch pitch = null;
		if(pitchSettings.isAutoCorrelate()) {
			try {
				pitch = sound.to_Pitch_ac(pitchSettings.getTimeStep(), pitchSettings.getRangeStart(), 3.0, 
						pitchSettings.getMaxCandidates(), (pitchSettings.isVeryAccurate() ? 1 : 0), pitchSettings.getSilenceThreshold(), 
						pitchSettings.getVoicingThreshold(), pitchSettings.getOctaveCost(), 
						pitchSettings.getOctaveJumpCost(), pitchSettings.getVoicedUnvoicedCost(), pitchSettings.getRangeEnd());
			} catch (PraatException pe) {
				ToastFactory.makeToast(pe.getLocalizedMessage()).start(this);
				LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
			}
		} else {
			try {
				pitch = sound.to_Pitch_cc(pitchSettings.getTimeStep(), pitchSettings.getRangeStart(), 3.0, 
						pitchSettings.getMaxCandidates(), (pitchSettings.isVeryAccurate() ? 1 : 0), pitchSettings.getSilenceThreshold(), 
						pitchSettings.getVoicingThreshold(), pitchSettings.getOctaveCost(), 
						pitchSettings.getOctaveJumpCost(), pitchSettings.getVoicedUnvoicedCost(), pitchSettings.getRangeEnd());
			} catch (PraatException pe) {
				ToastFactory.makeToast(pe.getLocalizedMessage()).start(this);
				LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
			}
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
			final Str32 unitText = pitch.getUnitText(Pitch.LEVEL_FREQUENCY, 
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
				(parent.getWavDisplay().hasSelection() ? 
						parent.getWavDisplay().getSelectionStart()
						: segment.getStartValue() / 1000.0);
		double selEnd = 
				(parent.getWavDisplay().hasSelection() ? 
						selStart + parent.getWavDisplay().getSelectionLength()
						: segment.getEndValue() / 1000.0);
		if(selEnd < selStart) {
			double temp = selStart;
			selStart = selEnd;
			selEnd = temp;
		}
		
		final NumberFormat format = NumberFormat.getNumberInstance();
		format.setMaximumFractionDigits(6);
		
		Table formantTable = null;
		try {
			final LongSound ls = LongSound.open(MelderFile.fromPath(wavFile.getAbsolutePath()));
			final Sound sound = ls.extractPart(formantStart, formantEnd, 1);
			final Formant formants = sound.to_Formant_burg(formantSettings.getTimeStep(), 
					formantSettings.getNumFormants(), formantSettings.getMaxFrequency(), 
					formantSettings.getWindowLength(), formantSettings.getPreEmphasis());
			
			formantTable = formants.downto_Table(false, true, 6, 
					formantSettings.isIncludeIntensity(), 6, formantSettings.isIncludeNumFormants(), 6, formantSettings.isIncludeBandwidths());
		} catch (PraatException pe) {
			LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
			return;
		}
		
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
					
				final double time = formantTable.getNumericValue(row, 1);
				
				if(time > selEnd) break;
				
				if(time >= selStart) {
					for(int col = 1; col <= formantTable.getNcol(); col++) {
						if(col > 1) sb.append(sc);
						sb.append(qc);
						sb.append(format.format(formantTable.getNumericValue(row, col)));
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
		} catch (UnsupportedEncodingException | PraatException e) {
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
				(parent.getWavDisplay().hasSelection() ? 
						parent.getWavDisplay().getSelectionStart()
						: segment.getStartValue() / 1000.0);
		double selEnd = 
				(parent.getWavDisplay().hasSelection() ? 
						selStart + parent.getWavDisplay().getSelectionLength()
						: segment.getEndValue() / 1000.0);
		if(selEnd < selStart) {
			double temp = selStart;
			selStart = selEnd;
			selEnd = temp;
		}
		
		final NumberFormat format = NumberFormat.getNumberInstance();
		format.setMaximumFractionDigits(6);
		
		Sound sound = null;
		try {
			final LongSound ls = LongSound.open(MelderFile.fromPath(wavFile.getAbsolutePath()));
			sound = ls.extractPart(start, end, 1);
		} catch (PraatException pe) {
			LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
			return;
		}
		
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
	
	/**
	 * @return
	 */
	private Spectrogram loadSpectrogram() {
		final MediaSegment segment = getSegment();
		if(segment == null || segment.getEndValue() - segment.getStartValue() <= 0.0f) {
			return null;
		}
		final File audioFile = parent.getAudioFile();
		if(audioFile == null) return null;
		
		Spectrogram spectrogram = null;
		try {
			final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
			final Sound part = longSound.extractPart((double)segment.getStartValue()/1000.0,
					(double)segment.getEndValue()/1000.0, 1);
		
			spectrogram = part.toSpectrogram(
				spectrogramSettings.getWindowLength(), spectrogramSettings.getMaxFrequency(),
				spectrogramSettings.getTimeStep(), spectrogramSettings.getFrequencyStep(), 
				spectrogramSettings.getWindowShape(), 8.0, 8.0);
		} catch (PraatException pe) {
			LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
		}
		return spectrogram;
	}
	
	private Pitch loadPitch() {
		final MediaSegment segment = getSegment();
		if(segment == null || segment.getEndValue() - segment.getStartValue() <= 0.0f) {
			return null;
		}
		final File audioFile = parent.getAudioFile();
		if(audioFile == null) return null;
		
		Pitch pitch = null;
		try {
			final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
			final Sound part = longSound.extractPart((double)segment.getStartValue()/1000.0, (double)segment.getEndValue()/1000.0, 1);
			
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
		} catch (PraatException pe) {
			LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
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
		
		Formant formants = null;
		try {
			final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
			final Sound part = longSound.extractPart((double)segment.getStartValue()/1000.0, (double)segment.getEndValue()/1000.0, 1);
			
			formants = 
					part.to_Formant_burg(formantSettings.getTimeStep(), formantSettings.getNumFormants(), 
							formantSettings.getMaxFrequency(), formantSettings.getWindowLength(), formantSettings.getPreEmphasis());
		} catch (PraatException pe) {
			LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
		}
		return formants;
	}
	
	private Intensity loadIntensity() {
		final MediaSegment segment = getSegment();
		if(segment == null || segment.getEndValue() - segment.getStartValue() <= 0.0f) {
			return null;
		}
		final File audioFile = parent.getAudioFile();
		if(audioFile == null) return null;
		
		Intensity intensity = null;
		try {
			final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
			final Sound part = longSound.extractPart((double)segment.getStartValue()/1000.0, (double)segment.getEndValue()/1000.0, 1);
			
			intensity =
					part.to_Intensity(pitchSettings.getRangeStart(),
							0.0,
							(intensitySettings.getSubtractMean() ? 1 : 0));
		} catch (PraatException pe) {
			LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
		}
		return intensity;
	}
	
	@RunInBackground(newThread=true)
	public void onRecordChanged(EditorEvent ee) {
		if(!isVisible() || !parent.getEditor().getViewModel().isShowing(SpeechAnalysisEditorView.VIEW_TITLE)) return;
		update();
	}
	
	@RunInBackground(newThread=true)
	public void onSegmentChanged(EditorEvent ee) {
		if(!isVisible() || !parent.getEditor().getViewModel().isShowing(SpeechAnalysisEditorView.VIEW_TITLE)) return;
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
		spectrogramPainter.setRepaintBuffer(true);
		
		if(showFormants) {
			try {
				formants = loadFormants();
			} catch (IllegalArgumentException e) {
				formants = null;
				
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
			formantPainter.setMaxFrequency(spectrogramSettings.getMaxFrequency());
			formantPainter.setSettings(formantSettings);
		}
		
		if(showPitch) {
			try {
				pitch = loadPitch();
			} catch (IllegalArgumentException e) {
				pitch = null;
				
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
			pitchPainter.setSettings(pitchSettings);
		}
		
		if(showIntensity) {
			try {
				intensity = loadIntensity();
			} catch (IllegalArgumentException e) {
				intensity = null;
				
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
			intensityPainter.setSettings(intensitySettings);
		}
		updateLock.unlock();
		
		
		final Runnable onEdt = new Runnable() {
			
			@Override
			public void run() {
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
	
	private class SpectrogramPanel extends JPanel {
		
		private static final long serialVersionUID = 7940163213370438304L;

		public SpectrogramPanel() {
			super();
		}
		
		@Override
		public void paintComponent(Graphics g) {
			final Graphics2D g2 = (Graphics2D)g;
			
			g2.setColor(parent.getWavDisplay().getBackground());
			g2.fillRect(0, 0, getWidth(), getHeight());
			g2.setColor(parent.getWavDisplay().getExcludedColor());
			g2.fillRect(0, 0, getWidth(), getHeight());
			
			g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			
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
			
			if(spectrogram != null) {
				updateLock.lock();
				spectrogramPainter.paint(spectrogram, g2, contentRect);
				updateLock.unlock();
			}
			
			if(showFormants && formants != null) {
				updateLock.lock();
				formantPainter.paint(formants, g2, contentRect);
				updateLock.unlock();
			}
			
			if(showPitch && pitch != null) {
				updateLock.lock();
				pitchPainter.paint(pitch, g2, contentRect);
				updateLock.unlock();
			}
			
			if(showIntensity && intensity != null) {
				updateLock.lock();
				intensityPainter.paint(intensity, g2, contentRect);
				updateLock.unlock();
			}
			
			final Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2}, 0);
			final NumberFormat nf = NumberFormat.getNumberInstance();
			nf.setMaximumFractionDigits(2);
			nf.setGroupingUsed(false);
			
			if(wavDisplay.getCursorPosition() >= 0 && contentRect.contains(wavDisplay.getCursorPosition(), contentRect.getY())) {
				final Line2D line = new Line2D.Double(wavDisplay.getCursorPosition(), contentRect.getY(), 
						wavDisplay.getCursorPosition(), contentRect.getY() + contentRect.getHeight());
				float time = wavDisplay.viewToModel(wavDisplay.getCursorPosition());
				
				g2.setStroke(dashed);
				g2.setColor(Color.WHITE);
				g2.draw(line);
				
				if(showPitch && pitch != null && !wavDisplay.hasSelection()) {
					// get pitch at current x
					double pitchVal = pitch.getValueAtX(time, Pitch.LEVEL_FREQUENCY,
							pitchSettings.getUnits().ordinal(), 1);
					if(!Double.isInfinite(pitchVal) && !Double.isNaN(pitchVal)) {
						final double hzPerPixel = 
								(pitchSettings.getRangeEnd() - pitchSettings.getRangeStart()) / getHeight();
						final double yPos = 
								getHeight() - ((pitchVal - pitchSettings.getRangeStart()) / hzPerPixel);
						pitchVal = pitch.convertStandardToSpecialUnit(pitchVal, Pitch.LEVEL_FREQUENCY,
								pitchSettings.getUnits().ordinal());
						final String pitchUnitStr = 
								pitch.getUnitText(Pitch.LEVEL_FREQUENCY, pitchSettings.getUnits().ordinal(), 
										Function.UNIT_TEXT_SHORT).toString();
						
						final String pitchStr = 
								nf.format(pitchVal) + " " + pitchUnitStr;
						
						g2.setColor(Color.blue);
						g2.drawString(pitchStr, (float)contentRect.getX() + (float)contentRect.getWidth(), (float)yPos);
					}
				}
				
				if(showIntensity && intensity != null && !wavDisplay.hasSelection()) {
					double intensityVal = intensity.getValueAtX(time, 1, Intensity.UNITS_DB, 1);
					
					if(!Double.isInfinite(intensityVal) && !Double.isNaN(intensityVal)) {
						final double dbPerPixel = 
								(intensitySettings.getViewRangeMax() - intensitySettings.getViewRangeMin()) / getHeight();
						final double yPos = 
								getHeight() - ((intensityVal - intensitySettings.getViewRangeMin()) / dbPerPixel);
						final String intensityUnitStr = "dB";
						final String intensityStr =
								nf.format(intensityVal) + " " + intensityUnitStr;
						final Rectangle2D intensityRect = 
								g2.getFontMetrics().getStringBounds(intensityStr, g2);
						final double intensityX = (contentRect.getX() + contentRect.getWidth()) -
								intensityRect.getWidth();
						
						g2.setColor(Color.yellow);
						g2.drawString(intensityStr, (float)intensityX, (float)yPos);
					}
				}
			}
			
			if(wavDisplay.hasSelection()) {
				double x1 = wavDisplay.modelToView(wavDisplay.getSelectionStart());
				double x2 = wavDisplay.modelToView(wavDisplay.getSelectionStart()+wavDisplay.getSelectionLength());
				final Line2D line =  new Line2D.Double(x1, contentRect.getY(), 
						x1, contentRect.getY() + contentRect.getHeight());
				
				g2.setStroke(dashed);
				g2.setColor(Color.white);
				g2.draw(line);
				
				line.setLine(x2, contentRect.getY(), x2, contentRect.getY() + contentRect.getHeight());
				g2.draw(line);
				
				Rectangle2D selRect = new Rectangle2D.Double(x1, contentRect.getY(), x2-x1, 
								contentRect.getHeight());
				
				g2.setColor(parent.getWavDisplay().getSelectionColor());
				g2.fill(selRect);
				
				if(showPitch && pitch != null) {
					// draw avg pitch
					double pitchVal = pitch.getMean(wavDisplay.getSelectionStart(), 
							wavDisplay.getSelectionStart()+wavDisplay.getSelectionLength(), Pitch.LEVEL_FREQUENCY,
							pitchSettings.getUnits().ordinal(), 1);
					if(!Double.isInfinite(pitchVal) && !Double.isNaN(pitchVal)) {
						final double hzPerPixel = 
								(pitchSettings.getRangeEnd() - pitchSettings.getRangeStart()) / getHeight();
						final double yPos = 
								getHeight() - ((pitchVal - pitchSettings.getRangeStart()) / hzPerPixel);
						pitchVal = pitch.convertStandardToSpecialUnit(pitchVal, Pitch.LEVEL_FREQUENCY,
								pitchSettings.getUnits().ordinal());
						final String pitchUnitStr = 
								pitch.getUnitText(Pitch.LEVEL_FREQUENCY, pitchSettings.getUnits().ordinal(), 
										Function.UNIT_TEXT_SHORT).toString();
						
						final String pitchStr = 
								nf.format(pitchVal) + " " + pitchUnitStr;
						g2.setColor(Color.blue);
						
						g2.drawString(pitchStr, (float)contentRect.getX()+(float)contentRect.getWidth(), (float)yPos);
					}
				}
				
				if(showIntensity && intensity != null) {
					double intensityVal = 0.0;
					try {
						intensityVal = intensity.getAverage(wavDisplay.getSelectionStart(), 
							wavDisplay.getSelectionStart()+wavDisplay.getSelectionLength(), intensitySettings.getAveraging());
					} catch (PraatException pe) {
						LOGGER.log(Level.WARNING, pe.getLocalizedMessage(), pe);
					}
					if(!Double.isInfinite(intensityVal) && !Double.isNaN(intensityVal)) {
						final double dbPerPixel = 
								(intensitySettings.getViewRangeMax() - intensitySettings.getViewRangeMin()) / getHeight();
						final double yPos = 
								getHeight() - ((intensityVal - intensitySettings.getViewRangeMin()) / dbPerPixel);
						final String intensityUnitStr = "dB";
						final String intensityStr =
								nf.format(intensityVal) + " " + intensityUnitStr;
						final Rectangle2D intensityRect = 
								g2.getFontMetrics().getStringBounds(intensityStr, g2);
						final double intensityX = (contentRect.getX()+contentRect.getWidth()) - intensityRect.getWidth();
						
						g2.setColor(Color.yellow);
						g2.drawString(intensityStr, (float)intensityX, (float)yPos);
					}
				}
			}
			
			if(currentPoint != null) {
				// draw dotted lines intersecting our point
				g2.setStroke(dashed);
				g2.setColor(Color.white);
				g2.drawLine((int)contentRect.getX(), currentPoint.y, 
						(int)contentRect.getX()+(int)contentRect.getWidth(), currentPoint.y );
				
				// draw frequency at point
				if(spectrogram != null) {
					// convert y to a frequency bin
					final int bin = (int)Math.round(
							((contentRect.getHeight()-currentPoint.y)/contentRect.getHeight()) * spectrogram.getNy() );
					final float freq = bin * (float)spectrogram.getDy();

					final String freqTxt = nf.format(freq) + " Hz";
							
					final FontMetrics fm = g2.getFontMetrics();
					final Rectangle2D bounds = fm.getStringBounds(freqTxt, g2);
					
					g2.setColor(Color.red);
					g2.drawString(freqTxt, 
							(float)(contentRect.getX() - bounds.getWidth()),
							(float)(currentPoint.y + (bounds.getHeight()/4)));
				
				}
			}
			
			final Rectangle2D leftInsetRect = new Rectangle2D.Double(
					contentRect.getX() - 100.0, contentRect.getY(),
					100.0, contentRect.getHeight());
			final Rectangle2D rightInsetRect = new Rectangle2D.Double(
					contentRect.getX()+contentRect.getWidth(), contentRect.getY(),
					100.0, contentRect.getHeight());
			if(spectrogram != null) {
				updateLock.lock();
				spectrogramPainter.paintGarnish(spectrogram, g2, leftInsetRect, SwingConstants.LEFT);
				updateLock.unlock();
			}
	
			if(showPitch && pitch != null) {
				updateLock.lock();
				pitchPainter.paintGarnish(pitch, g2, rightInsetRect, SwingConstants.RIGHT);
				updateLock.unlock();
			}
			
			if(showIntensity && intensity != null) {
				updateLock.lock();
				intensityPainter.paintGarnish(intensity, g2, rightInsetRect, SwingConstants.RIGHT);
				updateLock.unlock();
			}
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
	
	private final MouseInputAdapter pointListener = new MouseInputAdapter() {

		@Override
		public void mousePressed(MouseEvent me) {
			if(me.getButton() == MouseEvent.BUTTON1) {
				currentPoint = me.getPoint();
				repaint();
			}
		}
		
	};
	
	private final MouseInputAdapter selectionListener = new MouseInputAdapter() {
		
		private boolean isDraggingSelection = false;
		
		private int dragStartX = 0;
		
		@Override
		public void mouseExited(MouseEvent e) {
			// clear cursor position
			parent.getWavDisplay().setCursorPosition(-1);
		}
		
		@Override
		public void mouseMoved(MouseEvent e) {
			if(!isDraggingSelection) {
				parent.getWavDisplay().setCursorPosition(e.getX());
			}
		}
		
		@Override
		public void mousePressed(MouseEvent e) {
			parent.getWavDisplay().requestFocus();
			if(e.getButton() != MouseEvent.BUTTON1) return;
			dragStartX = e.getX();

			final int x = e.getX();
			final float time = parent.getWavDisplay().viewToModel(x);
			parent.getWavDisplay().setValuesAdusting(true);
			parent.getWavDisplay().setSelectionStart(time);
			parent.getWavDisplay().setValuesAdusting(false);
			parent.getWavDisplay().setSelectionLength(0.0f);
			parent.getWavDisplay().setCursorPosition(-1);
			isDraggingSelection = true;
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			isDraggingSelection = false;
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			if(isDraggingSelection) {
				final int x = e.getX();
				final float time = parent.getWavDisplay().viewToModel(x);
				final float dragStartTime = parent.getWavDisplay().viewToModel(dragStartX);
				
				final float startValue = 
						Math.min(time, dragStartTime);
				final float endValue = 
						Math.max(time, dragStartTime);
				final float len = endValue - startValue;
				
				parent.getWavDisplay().setValuesAdusting(true);
				parent.getWavDisplay().setSelectionStart(startValue);
				parent.getWavDisplay().setValuesAdusting(false);
				parent.getWavDisplay().setSelectionLength(len);
			}
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			if((e.getModifiers() & KeyEvent.CTRL_MASK) > 0) {
				final int numTicks = e.getWheelRotation();
				
				final float zoomAmount = 0.25f * numTicks;
				float windowLength = parent.getWavDisplay().getWindowLength() + zoomAmount;
				
				if(windowLength < 0.1f) {
					windowLength = 0.1f;
				} else if(windowLength > parent.getWavDisplay().getSampled().getLength()) {
					windowLength = parent.getWavDisplay().getSampled().getLength();
				}
				parent.getWavDisplay().setWindowLength(windowLength);
			}
		}
		
	};
}
