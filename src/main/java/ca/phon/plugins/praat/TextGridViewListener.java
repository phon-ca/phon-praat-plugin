package ca.phon.plugins.praat;
import java.awt.event.MouseEvent;
import java.util.EventListener;

import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.phon.util.Tuple;

public interface TextGridViewListener extends EventListener {

	public void tierLabelClicked(TextGrid textGrid, Long tierIdx, MouseEvent me);
	
	public void intervalSelected(TextGrid textGrid, Tuple<Long, Long> intervalIndex);
	
}
