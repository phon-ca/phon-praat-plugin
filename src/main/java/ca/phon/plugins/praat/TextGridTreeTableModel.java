/*
 * Copyright (C) 2012-2018 Gregory Hedlund
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *    http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.phon.plugins.praat;

import org.jdesktop.swingx.treetable.AbstractTreeTableModel;

import ca.hedlund.jpraat.binding.fon.IntervalTier;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.binding.fon.TextInterval;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.util.Tuple;

/**
 *
 * @author ghedlund
 */
public class TextGridTreeTableModel extends AbstractTreeTableModel {

	/** The text grid */
	private TextGrid tg;

	public TextGridTreeTableModel(TextGrid tg) {
		super();
		this.tg = tg;
	}

	@Override
	public Object getRoot() {
		return tg;
	}
	
	@Override
	public int getColumnCount() {
		return 2;
	}

	@Override
	public Object getValueAt(Object obj, int col) {
		Object retVal = new String();

		if(col == 0) {
			if(obj == tg) {
				retVal = "TextGrid";
			} else if(obj instanceof IntervalTier) {
				IntervalTier tier = (IntervalTier)obj;
				retVal = tier.getName();
			} else if (obj instanceof TextInterval) {
				TextInterval interval = (TextInterval)obj;
				retVal = interval.getText();
			} else if (obj instanceof Tuple) {
				Tuple<String, Object> tup =
						(Tuple<String,Object>)obj;
				retVal = tup.getObj1();
			}
		} else if(col == 1) {
			if(obj instanceof IntervalTier) {
				retVal = "Interval Tier";
			} else if (obj instanceof TextInterval) {
				retVal = "Interval";
			} else if(obj instanceof Tuple) {
				Tuple<String, Object> tup =
						(Tuple<String,Object>)obj;
				retVal = tup.getObj2();
			}
		}

		return retVal;
	}

	@Override
	public Object getChild(Object parent, int childIndex) {
		Object retVal = null;

		if(parent == tg) {
			try {
				retVal = tg.checkSpecifiedTierIsIntervalTier(childIndex);
			} catch (PraatException e) {
				try {
					retVal = tg.checkSpecifiedTierIsPointTier(childIndex);
				} catch (PraatException ex) {
					retVal = tg.tier(childIndex);
				}
			}
		} else if(parent instanceof IntervalTier) {
			IntervalTier tier = (IntervalTier)parent;
			retVal = tier.interval(childIndex);
		} else if(parent instanceof TextInterval) {
			TextInterval interval = (TextInterval)parent;
			if(childIndex == 0)
				retVal = 
						new Tuple<String, Object>("Start", new Float(interval.getXmin()));
			else if(childIndex == 1)
				retVal =
						new Tuple<String, Object>("End", new Float(interval.getXmax()));
		}

		return retVal;
	}

	@Override
	public int getChildCount(Object parent) {
		int retVal = 0;

		if(parent == tg) {
			retVal = (int)tg.numberOfTiers();
		} else if(parent instanceof IntervalTier) {
			IntervalTier tier = (IntervalTier)parent;
			retVal = (int)tier.numberOfIntervals();
		} else if(parent instanceof TextInterval) {
			retVal = 2;
		}

		return retVal;
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		int retVal = 0;

		if(parent == tg) {
			for(int i = 0; i < tg.numberOfTiers(); i++) {
				if(tg.tier(i) == child) {
					retVal = i;
					break;
				}
			}
		} else if (parent instanceof IntervalTier) {
			IntervalTier tier = (IntervalTier)parent;
			for(int i = 0; i < tier.numberOfIntervals(); i++) {
				if(tier.interval(i) == child) {
					retVal = i;
					break;
				}
			}
		} else if (parent instanceof TextInterval) {
			Tuple<String,Float> tuple =
					(Tuple<String,Float>)child;
			if(tuple.getObj1().equals("Start"))
				retVal = 0;
			else
				retVal = 1;
		}

		return retVal;
	}

}
