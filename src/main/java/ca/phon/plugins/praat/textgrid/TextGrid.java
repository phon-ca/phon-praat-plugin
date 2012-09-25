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

import java.util.ArrayList;
import java.util.List;

/**
 * A TextGrid class which aligns tiers with
 * time data.  Each tier can be either an interval
 * tier or a point tier.  This class is meant
 * to be compatible with Praat's TextGrid format.
 */
public class TextGrid {

    /* Text grid min val */
    private float tgMin;

    /* Text grid max val */
    private float tgMax;

    /* Tiers */
    private List<TextGridTier> tiers;

    /**
     * Constructor
     */
    public TextGrid(float min, float max) {
        this.tgMin = min;
        this.tgMax = max;
        this.tiers = new ArrayList<TextGridTier>();
    }

    public TextGrid() {
        this(0.0f, 0.0f);
    }

    public float getMin() {
        return tgMin;
    }

    public void setMin(float min) {
        this.tgMin = min;
    }

    public float getMax() {
        return tgMax;
    }

    public void setMax(float max) {
        this.tgMax = max;
    }

    public int getNumberOfTiers() {
        return tiers.size();
    }

    public TextGridTier getTier(int idx) {
        return tiers.get(idx);
    }

    public TextGridTier getTier(String tierName) {
        TextGridTier retVal = null;

        for(TextGridTier tier:tiers) {
            if(tier.getTierName().equals(tierName)) {
                retVal = tier;
                break;
            }
        }

        return retVal;
    }

    public void addTier(TextGridTier tier) {
        tiers.add(tier);
    }

    public void removeTier(int idx) {
        tiers.remove(idx);
    }

    public void removeTier(TextGridTier tier) {
        tiers.remove(tier);
    }

//    /**
//     * Read text grid from file.
//     */
//    public static TextGrid parse(String data) {
//
//    }
//
//    public static TextGrid readFromFile(File file)
//        throws IOException {
//
//    }
//
//    public static TextGrid readFromInputStream(InputStream is)
//        throws IOException {
//
//        BufferedReader reader = new BufferedReader(
//                new InputStreamReader(is));
//
//        StringBuffer data = new StringBuffer();
//        String line = null;
//        while((line = reader.readLine()) != null) {
//            data.append(line + "\n");
//        }
//
//        reader.close();
//
//        return parse(data);
//    }
//
//
}
