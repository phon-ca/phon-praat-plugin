package ca.phon.plugins.praat.opgraph;

import ca.gedge.opgraph.OpContext;
import ca.gedge.opgraph.OpNode;
import ca.gedge.opgraph.exceptions.ProcessingException;

/**
 * OpGraph node which will retrieve the formants
 * for a given interval in a sound file.
 *
 */
public class FormantsNode extends OpNode {
	
	/**
	 * Object input, this may be:
	 *   - an interval
	 *   - an extendable object with a TextGridInterval extension
	 */

	@Override
	public void operate(OpContext context) throws ProcessingException {
	}

}
