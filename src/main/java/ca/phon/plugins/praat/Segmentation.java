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

public enum Segmentation {
	TIER("Tier"), // full tier in one interval
	GROUP("Group"), // each group in an interval
	WORD("Word"), // each word in an inverval
	SYLLABLE("Syllable"), // (IPA-only) each syllable in an interval
	PHONE("Phone"); // (IPA-only) each phone in an interval
	
	private String tierSuffix;
	
	private Segmentation(String suffix) {
		this.tierSuffix = suffix;
	}
	
	@Override
	public String toString() {
		return tierSuffix;
	}
}