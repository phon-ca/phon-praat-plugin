/*
 * Phon - An open source tool for research in phonology.
 * Copyright (C) 2008 The Phon Project, Memorial University <http://phon.ling.mun.ca>
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
package ca.phon.plugins.praat.textgrid;

import org.jdesktop.swingx.treetable.AbstractTreeTableModel;

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
			} else if(obj instanceof TextGridTier) {
				TextGridTier tier = (TextGridTier)obj;
				retVal = tier.getTierName();
			} else if (obj instanceof TextGridInterval) {
				TextGridInterval interval = (TextGridInterval)obj;
				retVal = interval.getLabel();
			} else if (obj instanceof Tuple) {
				Tuple<String, Object> tup =
						(Tuple<String,Object>)obj;
				retVal = tup.getObj1();
			}
		} else if(col == 1) {
			if(obj instanceof TextGridTier) {
				TextGridTier tier = (TextGridTier)obj;
				retVal = tier.getTierType();
			} else if (obj instanceof TextGridInterval) {
				TextGridInterval interval = (TextGridInterval)obj;
				retVal = (interval.isPoint() ? "Point" : "Interval");
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
			retVal = tg.getTier(childIndex);
		} else if(parent instanceof TextGridTier) {
			TextGridTier tier = (TextGridTier)parent;
			retVal = tier.getIntervalAt(childIndex);
		} else if(parent instanceof TextGridInterval) {
			TextGridInterval interval = (TextGridInterval)parent;
			if(childIndex == 0)
				retVal = 
						new Tuple<String, Object>("Start", new Float(interval.getStart()));
			else if(childIndex == 1)
				retVal =
						new Tuple<String, Object>("End", new Float(interval.getEnd()));
		}

		return retVal;
	}

	@Override
	public int getChildCount(Object parent) {
		int retVal = 0;

		if(parent == tg) {
			retVal = tg.getNumberOfTiers();
		} else if(parent instanceof TextGridTier) {
			TextGridTier tier = (TextGridTier)parent;
			retVal = tier.getNumberOfIntervals();
		} else if(parent instanceof TextGridInterval) {
			retVal = 2;
		}

		return retVal;
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		int retVal = 0;

		if(parent == tg) {
			for(int i = 0; i < tg.getNumberOfTiers(); i++) {
				if(tg.getTier(i) == child) {
					retVal = i;
					break;
				}
			}
		} else if (parent instanceof TextGridTier) {
			TextGridTier tier = (TextGridTier)parent;
			for(int i = 0; i < tier.getNumberOfIntervals(); i++) {
				if(tier.getIntervalAt(i) == child) {
					retVal = i;
					break;
				}
			}
		} else if (parent instanceof TextGridInterval) {
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
