package ca.phon.plugins.praat;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.event.EventListenerList;
import javax.swing.plaf.ComponentUI;

import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.phon.app.media.TimeComponent;
import ca.phon.app.media.TimeUIModel;
import ca.phon.util.Tuple;

public class TextGridView extends TimeComponent {
	
	private final static String uiClassId = "TimeGridViewUI";

	private TextGrid textGrid;

	private boolean showLabels = true;
	
	private Map<String, Color> tierColorMap = new LinkedHashMap<String, Color>();
	
	public final static Color DEFAULT_TIER_LABEL_COLOR = new Color(255, 255, 0);
	
	private Map<String, Boolean> tierVisibilityMap = new LinkedHashMap<String, Boolean>();
	
	private final EventListenerList listenerList = new EventListenerList();
	
	public TextGridView() {
		this(null, new TimeUIModel());
	}
	
	public TextGridView(TextGrid textGrid) {
		this(textGrid, new TimeUIModel());
	}
	
	public TextGridView(TimeUIModel timeModel) {
		this(null, timeModel);
	}
	
	public TextGridView(TextGrid textGrid, TimeUIModel timeModel) {
		super(timeModel);
		
		this.textGrid = textGrid;
		
		setOpaque(true);
		setBackground(Color.white);
		
		updateUI();
	}
	
	@Override
	public String getUIClassID() {
		return uiClassId;
	}
	
	@Override
	public void updateUI() {
		setUI(new TextGridViewUI());
	}
	
	@Override
	public void setUI(ComponentUI ui) {
		if(!(ui instanceof TextGridViewUI))
			throw new IllegalArgumentException("Invalid UI");
		super.setUI(ui);
	}
	
	@Override
	public TextGridViewUI getUI() {
		return (TextGridViewUI)super.getUI();
	}
	
	public Color getLabelBackground(String tierName) {
		return tierColorMap.getOrDefault(tierName, DEFAULT_TIER_LABEL_COLOR);
	}
	
	public void setLabelBackground(String tierName, Color color) {
		Color oldColor = getLabelBackground(tierName);
		tierColorMap.put(tierName, color);
		super.firePropertyChange(tierName + ".labelBackground", oldColor, color);
	}
	
	public boolean isShowLabels() {
		return this.showLabels;
	}
	
	public void setShowLabels(boolean showLabels) {
		var oldVal = this.showLabels;
		this.showLabels = showLabels;
		super.firePropertyChange("showLabels", oldVal, showLabels);
	}
	
	public int indexOfTier(String tierName) {
		if(textGrid == null) return -1;
		
		for(long i = 1; i <= textGrid.numberOfTiers(); i++) {
			if(textGrid.tier(i).getName().contentEquals(tierName)) {
				return (int)i;
			}
		}
		return -1;
	}
	
	public TextGrid getTextGrid() {
		return this.textGrid;
	}
	
	public void setTextGrid(TextGrid textGrid) {
		var oldVal = this.textGrid;
		this.textGrid = textGrid;
		super.firePropertyChange("textGrid", oldVal, textGrid);
	}
	
	public boolean isTierVisible(String tierName) {
		Boolean visible = tierVisibilityMap.get(tierName);
		if(visible == null)
			visible = Boolean.TRUE;
		return visible;
	}
	
	public void setTierVisible(String tierName, boolean visible) {
		boolean oldVal = isTierVisible(tierName);
		tierVisibilityMap.put(tierName, visible);
		super.firePropertyChange(tierName + ".visible", oldVal, visible);
	}
	
	public int getVisibleTierCount() {
		int retVal = 0;
		
		if(textGrid != null) {
			for(long i = 1; i <= textGrid.numberOfTiers(); i++) {
				if(isTierVisible(textGrid.tier(i).getName())) ++retVal;
			}
		}
		return retVal;
	}
	
	public void addTextGridViewListener(TextGridViewListener listener) {
		listenerList.add(TextGridViewListener.class, listener);
	}
	
	public void removeTextGridViewListener(TextGridViewListener listener) {
		listenerList.remove(TextGridViewListener.class, listener);
	}
	
	public void fireIntervalSelected(Tuple<Long, Long> intervalIdx) {
		for(TextGridViewListener listener:listenerList.getListeners(TextGridViewListener.class)) {
			listener.intervalSelected(getTextGrid(), intervalIdx);
		}
	}
	
	public void fireTierLabelClicked(Long tierIdx, MouseEvent me) {
		for(TextGridViewListener listener:listenerList.getListeners(TextGridViewListener.class)) {
			listener.tierLabelClicked(getTextGrid(), tierIdx, me);
		}
	}
	
}
