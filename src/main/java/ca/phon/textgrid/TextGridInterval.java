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

/**
 * An interval on a text grid tier.
 * Each interval can have a string label.
 */
public class TextGridInterval implements Comparable<TextGridInterval> {

    /* Label */
    private String label;

    /* Range */
    private float start;
    private float end;

    /*
     * Constructor
     */
    public TextGridInterval(String label, float start, float end) {
        this.label = label;
        this.start = start;
        this.end = end;
    }

    public TextGridInterval(float start, float end) {
        this("", start, end);
    }

    public TextGridInterval() {
        this("", 0.0f, 0.0f);
    }

    public String getLabel() {
        return this.label;
    }

    public void setLabel(String lbl) {
        this.label = lbl;
    }

    public float getStart() {
        return this.start;
    }

    public void setStart(float start) {
        this.start = start;
    }

    public float getEnd() {
        return this.end;
    }

    public void setEnd(float end) {
        this.end = end;
    }

    public boolean containsPoint(float point) {
        boolean retVal =
                (point >= getStart()) && (point <= getEnd());
        return retVal;
    }

    public boolean overlaps(TextGridInterval t) {
		boolean retVal = false;
        return retVal;
    }

    /**
     * Is this a point or interval?
     * 
     */
    public boolean isPoint() {
        return getStart() == getEnd();
    }

    public int compareTo(TextGridInterval t) {

        // sort by start values
        Float myStart = getStart();
        Float tStart = t.getStart();

        return myStart.compareTo(tStart);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof TextGridInterval)) return false;

        TextGridInterval t = (TextGridInterval)obj;

        boolean retVal = t.getLabel().equals(getLabel());
        retVal &= t.getStart() == getStart();
        retVal &= t.getEnd() == getEnd();

        return retVal;
    }
}
