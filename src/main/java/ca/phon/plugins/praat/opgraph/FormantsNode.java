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
