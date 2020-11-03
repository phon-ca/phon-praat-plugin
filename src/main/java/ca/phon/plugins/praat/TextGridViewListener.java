package ca.phon.plugins.praat;
import java.awt.event.*;
import java.util.*;

import ca.hedlund.jpraat.binding.fon.*;
import ca.phon.util.*;

public interface TextGridViewListener extends EventListener {

	public void tierLabelClicked(TextGrid textGrid, Long tierIdx, MouseEvent me);
	
	public void intervalSelected(TextGrid textGrid, Tuple<Long, Long> intervalIndex);
	
}
