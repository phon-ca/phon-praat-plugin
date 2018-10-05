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