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

import static ca.phon.textgrid.TextGridTokens.CLASS;
import static ca.phon.textgrid.TextGridTokens.HEADER_LINE_1;
import static ca.phon.textgrid.TextGridTokens.HEADER_LINE_2;
import static ca.phon.textgrid.TextGridTokens.INTERVALS;
import static ca.phon.textgrid.TextGridTokens.ITEM;
import static ca.phon.textgrid.TextGridTokens.MARK;
import static ca.phon.textgrid.TextGridTokens.NAME;
import static ca.phon.textgrid.TextGridTokens.POINTS;
import static ca.phon.textgrid.TextGridTokens.SIZE;
import static ca.phon.textgrid.TextGridTokens.TEXT;
import static ca.phon.textgrid.TextGridTokens.TIERS_EXISTS;
import static ca.phon.textgrid.TextGridTokens.TIME;
import static ca.phon.textgrid.TextGridTokens.XMAX;
import static ca.phon.textgrid.TextGridTokens.XMIN;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

import ca.phon.system.logger.PhonLogger;

/**
 * Write a text grid to an output stream.
 */
public class TextGridWriter {

    /* Writer */
    private PrintWriter writer;

    private TextGrid tg;

    /* Construction */
    public TextGridWriter(TextGrid tg, String path) {
        this(tg, new File(path));
    }

    public TextGridWriter(TextGrid tg, String path, String encoding) {
        this(tg, new File(path), encoding);
    }

    public TextGridWriter(TextGrid tg, File file) {
        this(tg, file, Charset.defaultCharset().name());
    }

    public TextGridWriter(TextGrid tg, File file, String encoding) {
        this.tg = tg;

        try {
            writer = new PrintWriter(
                  new BufferedWriter(
                  new OutputStreamWriter(new FileOutputStream(file), encoding)));
        } catch (IOException ex) {
            PhonLogger.severe(TextGridWriter.class, ex.toString());
        }
    }

    public TextGridWriter(TextGrid tg, OutputStream os) {
        this(tg, os, Charset.defaultCharset().name());
    }

    public TextGridWriter(TextGrid tg, OutputStream os, String encoding) {
        this.tg = tg;

        try {
            writer = new PrintWriter(
                    new BufferedWriter(
                    new OutputStreamWriter(os, encoding)));
        } catch (IOException ex) {
            PhonLogger.severe(TextGridWriter.class, ex.toString());
        }
    }

    public void close() {
        if(writer != null) {
//            try {
                writer.flush();
                writer.close();
//            } catch (IOException ex) {
//                PhonLogger.severe(TextGridWriter.class, ex.toString());
//            }
        }
    }

    public void writeTextGrid() {
       writeHeader();

       writer.println(XMIN + " = " + tg.getMin());
       writer.println(XMAX + " = " + tg.getMax());
       writer.println(TIERS_EXISTS);
       writer.println(SIZE + " = " + tg.getNumberOfTiers());
       writer.println(ITEM + " []:");

       for(int i = 0; i < tg.getNumberOfTiers(); i++) {
           TextGridTier tier = tg.getTier(i);
           writeTier(tier, i+1);
       }
    }

    public void writeHeader() {
        writer.println(HEADER_LINE_1);
        writer.println(HEADER_LINE_2);
        writer.println();
    }

    public void writeTier(TextGridTier tier, int idx) {
        // tab index = 1
        String t1 = "\t";
        String t2 = "\t\t";

        writer.println(t1 + ITEM + " [" + idx + "]:");

        if(tier.getTierType() == TextGridTierType.INTERVAL)
            writer.println(t2 + CLASS + " = \"IntervalTier\"");
        else
            writer.println(t2 + CLASS + " = \"TextTier\"");

        writer.println(t2 + NAME + " = \"" + tier.getTierName() + "\"");

        writer.println(t2 + XMIN + " = " + tg.getMin());
        writer.println(t2 + XMAX + " = " + tg.getMax());

        if(tier.getTierType() == TextGridTierType.INTERVAL)
           writer.println(t2 + INTERVALS + ": " + SIZE + " = " + tier.getNumberOfIntervals());
        else
           writer.println(t2 + POINTS + ": " + SIZE + " = " + tier.getNumberOfIntervals());

        for(int i = 0; i < tier.getNumberOfIntervals(); i++) {
           TextGridInterval interval = tier.getIntervalAt(i);

           if(tier.getTierType() == TextGridTierType.INTERVAL)
               writeInterval(interval, i+1);
           else
               writePoint(interval, i+1);
        }
    }

    public void writeInterval(TextGridInterval interval, int idx) {
        String t2 = "\t\t";
        String t3 = "\t\t\t";

        writer.println(t2 + INTERVALS + " [" + idx + "]:");
        writer.println(t3 + XMIN + " = " + interval.getStart());
        writer.println(t3 + XMAX + " = " + interval.getEnd());
        writer.println(t3 + TEXT + " = \"" + interval.getLabel() + "\"");
    }

    public void writePoint(TextGridInterval point, int idx) {
        String t2 = "\t\t";
        String t3 = "\t\t\t";

        writer.println(t2 + POINTS + " [" + idx + "]:");
        writer.println(t3 + TIME + " = " + point.getStart());
        writer.println(t3 + MARK + " = \"" + point.getLabel() + "\"");
    }

}
