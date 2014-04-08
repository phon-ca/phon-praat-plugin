package ca.phon.plugins.praat;

import java.awt.BorderLayout;
import java.awt.Dimension;
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
		for(int i = 0; i < numFrames; i++) {
			retVal[i] = new double[numBins];
			final JSONArray frameData = spectrogramData.getJSONArray(i);
			for(int j = 0; j < numBins; j++) {
				retVal[i][j] = frameData.getDouble(j);
			}
		}
		
		return retVal;
	}
	
	@RunInBackground
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
	
	private final ComponentListener resizeListener = new ComponentListener() {
		
		@Override
		public void componentShown(ComponentEvent e) {
		}
		
		@Override
		public void componentResized(ComponentEvent e) {
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
