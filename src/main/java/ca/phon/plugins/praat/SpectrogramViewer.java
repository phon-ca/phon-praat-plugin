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
import java.util.function.Supplier;
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
import javax.swing.event.MouseInputAdapter;

import ca.hedlund.jpraat.binding.fon.Formant;
import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.Intensity;
import ca.hedlund.jpraat.binding.fon.LongSound;
import ca.hedlund.jpraat.binding.fon.Pitch;
import ca.hedlund.jpraat.binding.fon.Sound;
import ca.hedlund.jpraat.binding.fon.Spectrogram;
import ca.hedlund.jpraat.binding.fon.Spectrum;
import ca.hedlund.jpraat.binding.stat.Table;
import ca.hedlund.jpraat.binding.sys.Interpreter;
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
import ca.phon.ui.HidablePanel;
import ca.phon.ui.action.PhonActionEvent;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.ui.decorations.DialogHeader;
import ca.phon.ui.layout.ButtonBarBuilder;
import ca.phon.util.PrefHelper;
import ca.phon.util.icons.IconManager;
import ca.phon.util.icons.IconSize;
import ca.phon.worker.PhonTask;
import ca.phon.worker.PhonTask.TaskStatus;
import ca.phon.worker.PhonTaskListener;
import ca.phon.worker.PhonWorker;
import ca.phon.worker.PhonWorkerGroup;

/**
 * Adds a spectrogram tier to the waveform editor view.
 */
public class SpectrogramViewer extends JPanel implements SpeechAnalysisTier {
	
	private static final Logger LOGGER = Logger
			.getLogger(SpectrogramViewer.class.getName());
	
	private static final long serialVersionUID = -6963658315933818319L;

	private final SpeechAnalysisEditorView parent;
	
	private SpectrogramPanel contentPane;
	
	/**
	 * Max analysis length in seconds.
	 */
	public final static String MAX_ANALYSIS_LENGTH_PROP = SpectrogramViewer.class.getName() + ".maxAnalysisLength";
	public final static double DEFAULT_MAX_ANALYSIS_LENGTH = 10.0;
	private double maxAnalysisLength = PrefHelper.getDouble(MAX_ANALYSIS_LENGTH_PROP, DEFAULT_MAX_ANALYSIS_LENGTH);
	
	private final HidablePanel maxAnalysisMessage = new HidablePanel("SpectrogramViewer.maxAnalysisLengthMessage");
	
	/*
	 * Spectrogram
	 */
	public final static String SHOW_SPECTROGRAM_PROP = SpectrogramViewer.class.getName() + ".showSpectrogram";
	private boolean showSpectrogram =
			PrefHelper.getBoolean(SHOW_SPECTROGRAM_PROP, false);

	private SpectrogramSettings spectrogramSettings = new SpectrogramSettings();
	
	private final SpectrogramPainter spectrogramPainter = new SpectrogramPainter(spectrogramSettings);
	
	
	/*
	 * Spectral moments
	 */
	private SpectralMomentsSettings spectralMomentsSettings = new SpectralMomentsSettings();

	/*
	 * Formants
	 */
	private FormantSettings formantSettings = new FormantSettings();
	
	private final AtomicReference<Spectrogram> spectrogramRef = new AtomicReference<>();
	
	public final static String SHOW_FORMANTS_PROP = SpectrogramViewer.class.getName() + ".showFormants";
	private boolean showFormants = 
			PrefHelper.getBoolean(SHOW_FORMANTS_PROP, false);
	
	private final AtomicReference<Formant> formantRef = new AtomicReference<>();
	
	private FormantPainter formantPainter = new FormantPainter();
	
	/*
	 * Pitch
	 */
	private final AtomicReference<Pitch> pitchRef = new AtomicReference<>();
	
	private PitchSettings pitchSettings = new PitchSettings();
	
	private PitchSpecklePainter pitchPainter = new PitchSpecklePainter();
	
	public final static String SHOW_PITCH_PROP = SpectrogramViewer.class.getName() + ".showPitch";
	private boolean showPitch =
			PrefHelper.getBoolean(SHOW_PITCH_PROP, false);

	private final AtomicReference<Intensity> intensityRef = new AtomicReference<>();
	
	/*
	 * Intensity
	 */
	private IntensitySettings intensitySettings = new IntensitySettings();
	
	private IntensityPainter intensityPainter = new IntensityPainter();
	
	public final static String SHOW_INTENSITY_PROP = SpectrogramViewer.class.getName() + ".showIntensity";
	private boolean showIntensity =
			PrefHelper.getBoolean(SHOW_INTENSITY_PROP, false);
	
	/*
	 * UI 
	 */
	private transient Point currentPoint = null;
	
	private final static String DISPLAY_HEIGHT = "SpectrogramViewer.displayHeight";
	private JComponent sizer;
	private int displayHeight = PrefHelper.getInt(DISPLAY_HEIGHT, -1);
	
	private transient volatile double lastStartTime = 0.0;
	private transient volatile double lastEndTime = 0.0;
	private transient volatile boolean forceReload = false;
	
	public SpectrogramViewer(SpeechAnalysisEditorView p) {
		super();
		setVisible(showSpectrogram);
		this.parent = p;
		
		init();
		
		setupEditorEvents();
		setupToolbar();
	}
	
	private void init() {
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
		
		addMouseListener(selectionListener);
		addMouseMotionListener(selectionListener);
		addMouseListener(pointListener);
		
		setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		
		final PhonUIAction forceUpdateAct = new PhonUIAction(this, "update", true);
		forceUpdateAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Force load spectrogram");
		
		maxAnalysisMessage.setTopLabelText("<html><b>Spectrogram Not Loaded</b></htmlL>");
		maxAnalysisMessage.setBottomLabelText(
				String.format("<html>Record segment exceeds max analysis length of %.1fs. Click this message to force loading.</html>", maxAnalysisLength));
		maxAnalysisMessage.addAction(forceUpdateAct);
		maxAnalysisMessage.setDefaultAction(forceUpdateAct);
		maxAnalysisMessage.setVisible(false);
		
		parent.getErrorPane().add(maxAnalysisMessage);
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
		final PhonUIAction menuAct = new PhonUIAction(this, "onShowSpectrumMenu");
		menuAct.putValue(PhonUIAction.NAME, "Spectrum");
		menuAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Show spectrum menu");
		menuAct.putValue(PhonUIAction.SMALL_ICON, IconManager.getInstance().getIcon("praat/spectrogram", IconSize.SMALL));
		
		final JButton btn = new JButton(menuAct);
		parent.getToolbar().addSeparator();
		parent.getToolbar().add(btn);
	}
	
	public void onShowSpectrumMenu(PhonActionEvent pae) {
		final JMenu praatMenu = new JMenu("Praat");
		final JMenu menu = new JMenu("Spectrum");
		praatMenu.add(menu);
		
		addMenuItems(praatMenu);
		
		final JButton btn = (JButton)pae.getActionEvent().getSource();
		menu.getPopupMenu().show(btn, 0, btn.getHeight());
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
					formantPainter.setMaxFrequency(spectrogramSettings.getMaxFrequency());
					formantPainter.setRepaintBuffer(true);
					spectrogramPainter.setSettings(spectrogramSettings);
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
						final PhonWorker worker = PhonWorker.createWorker();
						worker.invokeLater(spectrogramLoader);
						worker.invokeLater( () -> SwingUtilities.invokeLater(updateTask) );
						worker.setFinishWhenQueueEmpty(true);
						worker.start();
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
		
		final PhonWorker worker = PhonWorker.createWorker();
		worker.setFinishWhenQueueEmpty(true);
		if(showFormants) {
			formantPainter.setMaxFrequency(spectrogramSettings.getMaxFrequency());
			worker.invokeLater(formantLoader);
		}
		worker.invokeLater( () -> SwingUtilities.invokeLater(updateTask) );
		worker.start();
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
			formantPainter.setSettings(formantSettings);
			formantPainter.setMaxFrequency(spectrogramSettings.getMaxFrequency());
			formantPainter.setRepaintBuffer(true);
			
			final PhonWorker worker = PhonWorker.createWorker();
			worker.setFinishWhenQueueEmpty(true);
			worker.invokeLater(formantLoader);
			worker.invokeLater( () -> SwingUtilities.invokeLater(updateTask) );
			worker.start();
		}
	}
	
	public void onTogglePitch() {
		showPitch = !showPitch;
		PrefHelper.getUserPreferences().putBoolean(SHOW_PITCH_PROP, showPitch);
		
		final PhonWorker worker = PhonWorker.createWorker();
		worker.setFinishWhenQueueEmpty(true);
		
		if(showPitch) {
			worker.invokeLater(pitchLoader);
		}
		worker.invokeLater( () -> SwingUtilities.invokeLater(updateTask) );
		worker.start();
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
			pitchPainter.setSettings(pitchSettings);
			pitchPainter.setRepaintBuffer(true);
			
			final PhonWorker worker = PhonWorker.createWorker();
			worker.setFinishWhenQueueEmpty(true);
			worker.invokeLater(pitchLoader);
			worker.invokeLater( () -> SwingUtilities.invokeLater(updateTask) );
			worker.start();
		}
	}
	
	public void onEditSpectralMomentsSettings() {
		final JDialog dialog = new JDialog(CommonModuleFrame.getCurrentFrame());
		dialog.setModal(true);
		
		dialog.setLayout(new BorderLayout());
		
		final DialogHeader header = new DialogHeader("Spectral Moments settings", "Edit spectral moments settings");
		dialog.add(header, BorderLayout.NORTH);
		
		final SpectralMomentsSettingsPanel settingsPanel = new SpectralMomentsSettingsPanel();
		settingsPanel.loadSettings(spectralMomentsSettings);
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
			spectralMomentsSettings = settingsPanel.getSettings();
		}
	}
	
	public void onToggleIntensity() {
		showIntensity = !showIntensity;
		PrefHelper.getUserPreferences().putBoolean(SHOW_INTENSITY_PROP, showIntensity);
		
		final PhonWorker worker = PhonWorker.createWorker();
		worker.setFinishWhenQueueEmpty(true);
		if(showIntensity) {
			worker.invokeLater(intensityLoader);
		}
		worker.invokeLater( () -> SwingUtilities.invokeLater(updateTask) );
		worker.start();
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
			
			final PhonWorker worker = PhonWorker.createWorker();
			worker.setFinishWhenQueueEmpty(true);
			worker.invokeLater(intensityLoader);
			worker.invokeLater( () -> SwingUtilities.invokeLater(updateTask) );
			worker.start();
		}
	}
	
	public void listPitch() {
		final Pitch pitch = (pitchRef.get() != null ? pitchRef.get() : loadPitch());
		if(pitch == null) return;
		
		final Record r = parent.getEditor().currentRecord();
		final MediaSegment segment = 
				(r.getSegment().numberOfGroups() > 0 ? r.getSegment().getGroup(0) : null);
		if(segment == null ||
				(segment.getEndValue() - segment.getStartValue()) <= 0) {
			return;
		}
		
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
			final String unitText = pitch.getUnitText(Pitch.LEVEL_FREQUENCY, 
					pitchSettings.getUnits(), Function.UNIT_TEXT_SHORT);
			sb.append(unitText).append(')').append(qc);
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
		final Record r = parent.getEditor().currentRecord();
		final MediaSegment segment = 
				(r.getSegment().numberOfGroups() > 0 ? r.getSegment().getGroup(0) : null);
		if(segment == null ||
				(segment.getEndValue() - segment.getStartValue()) <= 0) {
			return;
		}
		
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
			final Formant formants = (formantRef.get() != null ? formantRef.get() : loadFormants());
			if(formants == null)
				throw new PraatException("No formant information loaded");
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
		final Intensity intensity = (intensityRef.get() != null ? intensityRef.get() : loadIntensity());
		if(intensity == null) return;
		
		final Record r = parent.getEditor().currentRecord();
		final MediaSegment segment = 
				(r.getSegment().numberOfGroups() > 0 ? r.getSegment().getGroup(0) : null);
		if(segment == null ||
				(segment.getEndValue() - segment.getStartValue()) <= 0) {
			return;
		}
		
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
	
	public void listSpectralMoments() {
		final Spectrum spectrum = loadSpectrumForSpectralMoments();
		
		final Record r = parent.getEditor().currentRecord();
		final MediaSegment segment = 
				(r.getSegment().numberOfGroups() > 0 ? r.getSegment().getGroup(0) : null);
		if(segment == null ||
				(segment.getEndValue() - segment.getStartValue()) <= 0) {
			return;
		}
		
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
		
		final BufferWindow bw = BufferWindow.getInstance();
		bw.showWindow();
		final BufferPanel bufferPanel = bw.createBuffer("Spectral Moments (" + 
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
			sb.append(qc).append("Start Time(s)").append(qc).append(sc);
			sb.append(qc).append("End Time(s)").append(qc).append(sc);
			sb.append(qc).append("Center of Gravity").append(qc).append(sc);
			sb.append(qc).append("Standard Deviation").append(qc).append(sc);
			sb.append(qc).append("Kurtosis").append(qc).append(sc);
			sb.append(qc).append("Skewness").append(qc).append(sc);
			out.println(sb.toString());
			sb.setLength(0);
			
			sb.append(qc).append(format.format(selStart)).append(qc).append(sc);
			sb.append(qc).append(format.format(selEnd)).append(qc).append(sc);
			sb.append(qc).append(format.format(spectrum.getCentreOfGravity(2))).append(qc).append(sc);
			sb.append(qc).append(format.format(spectrum.getStandardDeviation(2))).append(qc).append(sc);
			sb.append(qc).append(format.format(spectrum.getKurtosis(2))).append(qc).append(sc);
			sb.append(qc).append(format.format(spectrum.getSkewness(2))).append(qc).append(sc);
			out.println(sb.toString());
			sb.setLength(0);
			
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
		
		final double xmin = (double)segment.getStartValue()/1000.0;
		final double xmax = (double)segment.getEndValue()/1000.0;
		
		Spectrogram spectrogram = null;
		try {
			final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
			final Sound part = longSound.extractPart(xmin, xmax, 1);
		
			spectrogram = part.to_Spectrogram(
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
	
	private Spectrum loadSpectrumForSpectralMoments() {
		final MediaSegment segment = getSegment();
		if(segment == null || segment.getEndValue() - segment.getStartValue() <= 0.0f) {
			return null;
		}
		final File audioFile = parent.getAudioFile();
		if(audioFile == null) return null;
		
		Spectrum spectrum = null;
		try {
			final LongSound longSound = LongSound.open(MelderFile.fromPath(parent.getAudioFile().getAbsolutePath()));
			
			final double xmin = (parent.getWavDisplay().hasSelection() ? 
					parent.getWavDisplay().getSelectionStart()
					: segment.getStartValue() / 1000.0);
			final double xmax = (parent.getWavDisplay().hasSelection() ? 
					xmin + parent.getWavDisplay().getSelectionLength()
					: segment.getEndValue() / 1000.0);

			final Sound part = longSound.extractPart(xmin, xmax, 1);
			final Sound shapedPart = part.extractPart(xmin, xmax, spectralMomentsSettings.getWindowShape(), 2, true);
			
			spectrum = shapedPart.to_Spectrum(1);
			spectrum.passHannBand(spectralMomentsSettings.getFilterStart(), spectralMomentsSettings.getFilterEnd(), spectralMomentsSettings.getFilterSmoothing());
			
			if(spectralMomentsSettings.isUsePreemphasis()) {
				final String formula = 
						String.format("if x >= %d then self*x else self fi", 
								(new Double(spectralMomentsSettings.getPreempFrom())).intValue());
				spectrum.formula(formula, Interpreter.create(), null);
			}
		} catch (PraatException pe) {
			LOGGER.log(Level.SEVERE, pe.getLocalizedMessage(), pe);
		}
		
		return spectrum;
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

	
	private class LoadData<T> extends PhonTask {
		
		private final ReentrantLock updateLock = new ReentrantLock();

		private AtomicReference<T> ref;
		
		private Supplier<T> supplier;
		
		public LoadData(AtomicReference<T> ref, Supplier<T> supplier) {
			super();
			
			this.ref = ref;
			this.supplier = supplier;
		}
		
		@Override
		public void performTask() {
			super.setStatus(TaskStatus.RUNNING);
			
			final MediaSegment segment = getSegment();
			if(segment == null) {
				updateLock.unlock();
				return;
			}
			
			updateLock.lock();
			
			try {
				final T data = supplier.get();
				ref.set(data);
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
				super.err = e;
				super.setStatus(TaskStatus.ERROR);
			}
			
			updateLock.unlock();
			
			super.setStatus(TaskStatus.FINISHED);
		}
		
	}
	
	private final LoadData<Spectrogram> spectrogramLoader = new LoadData<>(spectrogramRef, this::loadSpectrogram);
	private final LoadData<Formant> formantLoader = new LoadData<>(formantRef, this::loadFormants);
	private final LoadData<Pitch> pitchLoader = new LoadData<>(pitchRef, this::loadPitch);
	private final LoadData<Intensity> intensityLoader = new LoadData<>(intensityRef, this::loadIntensity);
	
//	/**
//	 * Task used to update data
//	 */
//	private class LoadDataTask extends PhonTask {
//		
//		private boolean force;
//		
//		public LoadDataTask() {
//			this(false);
//		}
//		
//		public LoadDataTask(boolean force) {
//			super();
//			this.force = force;
//		}
//
//		@Override
//		public void performTask() {
//			super.setStatus(TaskStatus.RUNNING);
//			
//			updateLock.lock();
//			
//			final MediaSegment segment = getSegment();
//			if(segment == null) {
//				clearDisplay();
//				updateLock.unlock();
//				return;
//			}
//			
//			// check analysis length
//			final double startTime = segment.getStartValue()/1000.0;
//			final double endTime = segment.getEndValue()/1000.0;
//			final double len = endTime - startTime;
//			
//			if(lastStartTime == startTime && lastEndTime == endTime) {
//				// don't re-load data, return
//				updateLock.unlock();
//				return;
//			}
//			
//			if(len <= 0.0) return;
//			
//			if(len > maxAnalysisLength && !force) {
//				clearDisplay();
//				SwingUtilities.invokeLater( () -> maxAnalysisMessage.setVisible(true) );
//				updateLock.unlock();
//				return;
//			}
//			
//			SwingUtilities.invokeLater( () -> maxAnalysisMessage.setVisible(false) );
//			
//			if(getStatus() == TaskStatus.TERMINATED) {
//				updateLock.unlock();
//				return;
//			}
//			
//			Spectrogram spectrogram = null;
//			try {
//				spectrogram = loadSpectrogram();
//			} catch (IllegalArgumentException e) {
//				LOGGER.log(Level.WARNING, e.getLocalizedMessage(), e);
//			}
//			spectrogramRef.set(spectrogram);
//			spectrogramPainter.setSettings(spectrogramSettings);
//			spectrogramPainter.setRepaintBuffer(true);
//			
//			if(showFormants) {
//				if(getStatus() == TaskStatus.TERMINATED) {
//					updateLock.unlock();
//					return;
//				}
//				
//				Formant formant = null;
//				try {
//					formant = loadFormants();
//				} catch (IllegalArgumentException e) {
//					
//					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
//				}
//				formantRef.set(formant);
//				formantPainter.setMaxFrequency(spectrogramSettings.getMaxFrequency());
//				formantPainter.setSettings(formantSettings);
//			}
//			
//			if(showPitch) {
//				if(getStatus() == TaskStatus.TERMINATED) {
//					updateLock.unlock();
//					return;
//				}
//				
//				Pitch pitch = null;
//				try {
//					pitch = loadPitch();
//				} catch (IllegalArgumentException e) {
//					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
//				}
//				pitchRef.set(pitch);
//				pitchPainter.setSettings(pitchSettings);
//			}
//			
//			if(showIntensity) {
//				if(getStatus() == TaskStatus.TERMINATED) {
//					updateLock.unlock();
//					return;
//				}
//				
//				Intensity intensity = null;
//				try {
//					intensity = loadIntensity();
//				} catch (IllegalArgumentException e) {
//					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
//				}
//				intensityRef.set(intensity);
//				intensityPainter.setSettings(intensitySettings);
//			}
//			
//			lastStartTime = startTime;
//			lastEndTime = endTime;
//			
//			updateLock.unlock();
//			
//			SwingUtilities.invokeLater(updateTask);
//			
//			super.setStatus(TaskStatus.FINISHED);
//		}
//		
//	}
	
	/**
	 * Task used to update display.
	 */
	private final Runnable updateTask = new Runnable() {
		
		@Override
		public void run() {
			revalidate();
			repaint();
		}
		
	};
	
	/**
	 * Set all values to null and reset painters.
	 * 
	 */
	private void clearDisplay() {
		spectrogramRef.set(null);
		spectrogramPainter.setRepaintBuffer(true);
		
		formantRef.set(null);
		formantPainter.setRepaintBuffer(true);
		
		pitchRef.set(null);
		pitchPainter.setRepaintBuffer(true);
		
		intensityRef.set(null);
		intensityPainter.setRepaintBuffer(true);
		
		if(SwingUtilities.isEventDispatchThread())
			updateTask.run();
		else
			SwingUtilities.invokeLater(updateTask);
	}
	
	public void update() {
		update(false);
	}
	
	public void update(boolean force) {
		if(!isVisible()) return;
		
		final MediaSegment segment = getSegment();
		if(segment == null) {
			clearDisplay();
			return;
		}
		
		// check analysis length
		final double startTime = segment.getStartValue()/1000.0;
		final double endTime = segment.getEndValue()/1000.0;
		final double len = endTime - startTime;
		final boolean sameSegment = 
				(lastStartTime == startTime && lastEndTime == endTime);
		
		if(len <= 0.0) return;
		
		if(len > maxAnalysisLength && !force && !sameSegment) {
			lastStartTime = -1;
			lastEndTime = -1;
			
			clearDisplay();
			SwingUtilities.invokeLater( () -> maxAnalysisMessage.setVisible(true) );
			return;
		}
		
		if(sameSegment) {
			// don't re-load data, return
			return;
		}
		
		SwingUtilities.invokeLater( () -> maxAnalysisMessage.setVisible(false) );
		
		final PhonWorker worker = PhonWorker.createWorker();
		worker.setName(SpectrogramViewer.class.getName()+".worker");
		
		spectrogramPainter.setRepaintBuffer(true);
		spectrogramPainter.setSettings(spectrogramSettings);
		worker.invokeLater(spectrogramLoader);
		
		if(showFormants) {
			formantPainter.setRepaintBuffer(true);
			formantPainter.setSettings(formantSettings);
			formantPainter.setMaxFrequency(spectrogramSettings.getMaxFrequency());
			worker.invokeLater(formantLoader);
		}
		
		if(showPitch) {
			pitchPainter.setRepaintBuffer(true);
			pitchPainter.setSettings(pitchSettings);
			worker.invokeLater(pitchLoader);
		}
		
		if(showIntensity) {
			intensityPainter.setRepaintBuffer(true);
			intensityPainter.setSettings(intensitySettings);
			worker.invokeLater(intensityLoader);
		}
		
		worker.invokeLater( () -> { lastStartTime = startTime; lastEndTime = endTime; } );
		worker.invokeLater( () -> SwingUtilities.invokeLater(updateTask) );
		
		worker.setFinishWhenQueueEmpty(true);
		worker.start();
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
			
			if(spectrogramRef.get() != null) {
				spectrogramLoader.updateLock.lock();
				spectrogramPainter.paint(spectrogramRef.get(), g2, contentRect);
				spectrogramLoader.updateLock.unlock();
			}
			
			if(showFormants && formantRef.get() != null) {
				formantLoader.updateLock.lock();
				formantPainter.paint(formantRef.get(), g2, contentRect);
				formantLoader.updateLock.unlock();
			}
			
			if(showPitch && pitchRef.get() != null) {
				pitchLoader.updateLock.lock();
				pitchPainter.paint(pitchRef.get(), g2, contentRect);
				pitchLoader.updateLock.unlock();
			}
			
			if(showIntensity && intensityRef.get() != null) {
				intensityLoader.updateLock.lock();
				intensityPainter.paint(intensityRef.get(), g2, contentRect);
				intensityLoader.updateLock.unlock();
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
				
				if(showPitch && pitchRef.get() != null && !wavDisplay.hasSelection()) {
					final Pitch pitch = pitchRef.get();
					// get pitch at current x
					double pitchVal = pitch.getValueAtX(time, Pitch.LEVEL_FREQUENCY,
							pitchSettings.getUnits().ordinal(), true);
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
				
				if(showIntensity && intensityRef.get() != null && !wavDisplay.hasSelection()) {
					final Intensity intensity = intensityRef.get();
					double intensityVal = intensity.getValueAtX(time, 1, Intensity.UNITS_DB, true);
					
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
				
				if(showPitch && pitchRef.get() != null) {
					final Pitch pitch = pitchRef.get();
					// draw avg pitch
					double pitchVal = pitch.getMean(wavDisplay.getSelectionStart(), 
							wavDisplay.getSelectionStart()+wavDisplay.getSelectionLength(), Pitch.LEVEL_FREQUENCY,
							pitchSettings.getUnits().ordinal(), true);
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
				
				if(showIntensity && intensityRef.get() != null) {
					final Intensity intensity = intensityRef.get();
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
			
			final Spectrogram spectrogram = spectrogramRef.get();
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
				spectrogramLoader.updateLock.lock();
				spectrogramPainter.paintGarnish(spectrogram, g2, leftInsetRect, SwingConstants.LEFT);
				spectrogramLoader.updateLock.unlock();
			}
	
			if(showPitch && pitchRef.get() != null) {
				pitchLoader.updateLock.lock();
				pitchPainter.paintGarnish(pitchRef.get(), g2, rightInsetRect, SwingConstants.RIGHT);
				pitchLoader.updateLock.unlock();
			}
			
			if(showIntensity && intensityRef.get() != null) {
				intensityLoader.updateLock.lock();
				intensityPainter.paintGarnish(intensityRef.get(), g2, rightInsetRect, SwingConstants.RIGHT);
				intensityLoader.updateLock.unlock();
			}
		}
	}
	
	@Override
	public void onRefresh() {
		update();
	}
	
	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if(!visible && maxAnalysisMessage.isVisible())
			maxAnalysisMessage.setVisible(false);
	}

	@Override
	public void addMenuItems(JMenu menu) {
		JMenu praatMenu = null;
		for(int i = 0; i < menu.getItemCount(); i++) {
			if(menu.getItem(i) != null && menu.getItem(i).getText() != null 
					&& menu.getItem(i).getText().equals("Spectrum")) {
				praatMenu = (JMenu)menu.getItem(i);
			}
		}
		if(praatMenu == null) {
			praatMenu = new JMenu("Spectrum");
			praatMenu.setIcon(IconManager.getInstance().getIcon("praat/spectrogram", IconSize.SMALL));
			menu.addSeparator();
			menu.add(praatMenu);
		}
		
		final PhonUIAction toggleAct = new PhonUIAction(this, "onToggleSpectrogram");
		toggleAct.setRunInBackground(true);
		toggleAct.putValue(PhonUIAction.NAME, "Show Spectrogram");
		toggleAct.putValue(PhonUIAction.SELECTED_KEY, SpectrogramViewer.this.isVisible());
		praatMenu.add(new JCheckBoxMenuItem(toggleAct));
		
		final PhonUIAction settingsAct = new PhonUIAction(this, "onEditSettings");
		settingsAct.putValue(PhonUIAction.NAME, "Spectrogram settings...");
		settingsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Edit spectrogram settings...");
		praatMenu.add(settingsAct);
		
		praatMenu.addSeparator();
		
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
		
		praatMenu.addSeparator();
		
		final PhonUIAction togglePitchAct = new PhonUIAction(this, "onTogglePitch");
		togglePitchAct.putValue(PhonUIAction.NAME, "Show Pitch");
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
		
		praatMenu.addSeparator();
		
		final PhonUIAction toggleIntensityAct = new PhonUIAction(this, "onToggleIntensity");
		toggleIntensityAct.putValue(PhonUIAction.NAME, "Show Intensity");
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
		
		praatMenu.addSeparator();
		
		final PhonUIAction spectralMomentsSettingsAct = new PhonUIAction(this, "onEditSpectralMomentsSettings");
		spectralMomentsSettingsAct.putValue(PhonUIAction.NAME, "Spectral Moments settings...");
		spectralMomentsSettingsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Edit spectral moments settings...");
		praatMenu.add(spectralMomentsSettingsAct);
		
		final PhonUIAction listSpectralMomentsAct = new PhonUIAction(this, "listSpectralMoments");
		listSpectralMomentsAct.putValue(PhonUIAction.NAME, "Spectral Moments listing...");
		listSpectralMomentsAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "List spectral moments for segment/selection");
		praatMenu.add(listSpectralMomentsAct);
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
