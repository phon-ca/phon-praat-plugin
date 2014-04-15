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
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import org.json.JSONArray;
import org.json.JSONObject;

import com.orientechnologies.orient.core.record.impl.ODocument;

import ca.phon.app.session.editor.DelegateEditorAction;
import ca.phon.app.session.editor.EditorAction;
import ca.phon.app.session.editor.EditorEvent;
import ca.phon.app.session.editor.EditorEventType;
import ca.phon.app.session.editor.RunInBackground;
import ca.phon.app.session.editor.view.waveform.WaveformEditorView;
import ca.phon.app.session.editor.view.waveform.WaveformTier;
import ca.phon.app.session.editor.view.waveform.WaveformViewCalculator;
import ca.phon.media.wavdisplay.TimeBar;
import ca.phon.plugins.praat.db.PraatDbManager;
import ca.phon.ui.action.PhonUIAction;

/**
 * Adds a spectrogram tier to the waveform editor view.
 */
public class SpectrogramViewer extends JPanel implements WaveformTier {
	
	private static final long serialVersionUID = -6963658315933818319L;

	private final WaveformEditorView parent;

	private SpectrogramPanel spectrogramPanel;
	
	public SpectrogramViewer(WaveformEditorView parent) {
		super();
//		setVisible(false);
		this.parent = parent;
		this.parent.addComponentListener(resizeListener);
		
		setLayout(null);
		
		update();
		setupEditorEvents();
		setupToolbar();
	}

	private void setupEditorEvents() {
		final EditorAction recordChangedAct = new DelegateEditorAction(this, "onRecordChanged");
		parent.getEditor().getEventManager().registerActionForEvent(EditorEventType.RECORD_CHANGED_EVT, recordChangedAct);
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
			}
		});
		toolbar.addSeparator();
		toolbar.add(toggleSpectrogramBox);
	}
	
	private double[][] loadSpectrogram() {
		final PraatDbManager dbManager = PraatDbManager.getInstance(parent.getEditor().getProject());
		if(!dbManager.databaseExists()) return new double[0][];
		final List<ODocument> spectrogramDocs = 
				dbManager.documentsForRecord(parent.getEditor().currentRecord().getUuid(), "spectrogram");
		if(spectrogramDocs.size() == 0) return new double[0][];
		
		// if multiple documents exist, use last one as it's the newest
		final ODocument spectrogramDoc = spectrogramDocs.get(spectrogramDocs.size()-1);
		final JSONObject jsonObj = new JSONObject(spectrogramDoc.toJSON());
		
		final JSONObject spectrogramInfo = jsonObj.getJSONObject("info");
		final JSONArray spectrogramData = jsonObj.getJSONArray("data");
		
		final int numFrames = spectrogramInfo.getInt("numFrames");
		final int numBins = spectrogramInfo.getInt("numBins");
		
		double retVal[][] = new double[spectrogramInfo.getInt("numFrames")][];
		/*
		 * The following code comes from Praat
		 * Spectrogram.cpp
		 */
		double NUMln2 = 0.6931471805599453094172321214581765680755;
		double NUMln10 = 2.3025850929940456840179914546843642076011;
		double localMax[] = new double[numFrames];
		for(int ifreq = 0; ifreq < numBins; ifreq++) {
			double preemphasisFactor = (6.0 / NUMln2) * Math.log(ifreq * 31.25 / 1000.0);
			for(int itime = 0; itime < numFrames; itime++) {
				double frameArray[] = retVal[itime];
				if(frameArray == null) {
					retVal[itime] = new double[numBins];
				}
				final JSONArray frameData = spectrogramData.getJSONArray(itime);
				double value = frameData.getDouble(ifreq); // power
				value = (10.0/NUMln10) * Math.log((value + 1e-30) / 4.0e-10) + preemphasisFactor;  // dB
				if(value > localMax[itime]) localMax[itime] = value;
				retVal[itime][ifreq] = value;
			}
		}
		
//		for(int i = 0; i < numFrames; i++) {
//			retVal[i] = new double[numBins];
//			final JSONArray frameData = spectrogramData.getJSONArray(i);
//			double last = 0.0;
//			for(int j = 0; j < numBins; j++) {
//				retVal[i][j] = pre_emp(last, frameData.getDouble(j), j * 31.25, 2);
//				retVal[i][j] = psd_db(retVal[i][j]);
//				last = frameData.getDouble(j);
//			}
//		}
		
		return retVal;
	}
	
	/**
	 * Converts the stored spectrogram data in Pa^2/Hz to (loosley)
	 * Db/Hz.
	 * 
	 * @param psd
	 * @return
	 */
	private double psd_db(double psd) {
		final double pref_squared = 2e-5 * 2e-5;	
		return 10 * Math.log10( (psd / pref_squared) );
	}
	
	private double pre_emp(double prev, double val, double freq, double window) {
		double alpha = Math.exp(-2.0 * Math.PI * freq * window);
		return ( val - (alpha*prev) );
	}

	
	@RunInBackground(newThread=true)
	public void onRecordChanged(EditorEvent ee) {
		update();
	}
	
	public void update() {
		final double[][] spectrogramData = loadSpectrogram();
		final Runnable onEdt = new Runnable() {
			
			@Override
			public void run() {
				removeAll();
				if(spectrogramData == null 
						|| spectrogramData.length == 0){
					return;
				}
				spectrogramPanel = new SpectrogramPanel(spectrogramData);
				add(spectrogramPanel);
				final WaveformViewCalculator calculator = parent.getCalculator();
				
				final Rectangle2D segRect = calculator.getSegmentRect();
				int x = (segRect.getX() != Float.POSITIVE_INFINITY ? (int)segRect.getX() : 0);
				int y = (segRect.getY() != Float.POSITIVE_INFINITY ? (int)segRect.getY() : 0);
				float hscale = 1.0f;
				float vscale = 1.0f;
				if(segRect.getWidth() > 0) {
					hscale = (float)(segRect.getWidth() / (float)spectrogramPanel.getDataWidth());
					spectrogramPanel.setZoom(hscale, vscale);
				}
				spectrogramPanel.setBounds(
						x, y,
						(int)(spectrogramPanel.getDataWidth() * hscale), spectrogramPanel.getPreferredSize().height);
				setPreferredSize(
						new Dimension(parent.getWidth(), spectrogramPanel.getPreferredSize().height));
				setSize(new Dimension(parent.getWidth(), spectrogramPanel.getPreferredSize().height));
				revalidate();repaint();
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
			if(spectrogramPanel == null) return;
			final WaveformViewCalculator calculator = parent.getCalculator();
			
			final Rectangle2D segRect = calculator.getSegmentRect();
			
			int x = (segRect.getX() != Float.POSITIVE_INFINITY ? (int)segRect.getX() : 0);
			int y = 0;
			
			float hscale = 1.0f;
			float vscale = 1.0f;
			
			if(segRect.getWidth() > 0) {
				hscale = (float)(segRect.getWidth() / (float)spectrogramPanel.getDataWidth());
				spectrogramPanel.setZoom(hscale, vscale);
			}
			
			spectrogramPanel.setBounds(
					x, y,
					spectrogramPanel.getPreferredSize().width, spectrogramPanel.getPreferredSize().height);
			setPreferredSize(
					new Dimension(parent.getWidth(), spectrogramPanel.getPreferredSize().height));
			setSize(new Dimension(parent.getWidth(), spectrogramPanel.getPreferredSize().height));
			revalidate();repaint();
		}
		
		@Override
		public void componentMoved(ComponentEvent e) {
		}
		
		@Override
		public void componentHidden(ComponentEvent e) {
		}
	};

}
