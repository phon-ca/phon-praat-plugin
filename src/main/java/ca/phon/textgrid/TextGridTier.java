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
package ca.phon.textgrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for text grid tiers.
 */
public class TextGridTier {

    /* Tier name */
    private String tierName;

    /* Tier type */
    private TextGridTierType tierType;

    /* Intervals */
    private List<TextGridInterval> intervals;

    /**
     * Constructor
     */
    public TextGridTier(String name, TextGridTierType type) {
        this.tierName = name;
        this.tierType = type;
        this.intervals = new ArrayList<TextGridInterval>();
    }

    public TextGridTier() {
        this("<undef>", TextGridTierType.INTERVAL);
    }

    /**
     * Get tier name
     *
     * @return tier name
     */
    public String getTierName() {
        return this.tierName;
    }

    /**
     * Set tier name
     *
     * @param tierName
     */
    public void setTierName(String tierName) {
        this.tierName = tierName;
    }

    /**
     * Get tier type
     */
    public TextGridTierType getTierType() {
        return this.tierType;
    }

    /**
     * Set tier type
     *
     */
    public void setTierType(TextGridTierType type) {
        this.tierType = type;
    }

    /**
     * Get the number of defined intervals/points.
     */
    public int getNumberOfIntervals() {
        return intervals.size();
    }

    /**
     * Get the intervals at the given index
     *
     * @param idx
     */
    public TextGridInterval getIntervalAt(int idx) {
        return intervals.get(idx);
    }

    /**
     * Add an interval/point to the list. The list is
     * kept in-order..
     *
     * @param interval
     */
    public void addInterval(TextGridInterval interval) {
        // first make sure we don't have any overlap
        boolean okToAdd = true;
        for(TextGridInterval t:intervals) {
            okToAdd &= !t.overlaps(interval);
        }

        if(okToAdd) {
            intervals.add(interval);
            Collections.sort(intervals);
        } else {
            throw new IllegalArgumentException("Interval overlaps");
        }
    }
    
    /**
     * Remove an interval/point from the list.
     *
     * @param idx
     */
    public void removeInterval(int idx) {
        intervals.remove(idx);
    }

    /**
     * Remove an interval/point from the list
     */
    public void removeInterval(TextGridInterval interval) {
        intervals.remove(interval);
    }
}
