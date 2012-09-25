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

import static ca.phon.plugins.praat.textgrid.TextGridTokens.CLASS;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.HEADER_LINE_1;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.HEADER_LINE_2;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.INTERVALS;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.ITEM;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.MARK;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.NAME;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.POINTS;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.SIZE;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.TEXT;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.TIERS_EXISTS;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.TIME;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.XMAX;
import static ca.phon.plugins.praat.textgrid.TextGridTokens.XMIN;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.phon.exceptions.ParserException;
import ca.phon.system.logger.PhonLogger;
import ca.phon.util.StringUtils;

/**
 * Read TextGrid from a character stream.
 */
public class TextGridReader {
    
    /* Reader */
    private BufferedReader reader;

    /**
     * Constructor
     */
    public TextGridReader(String path) {
        this(new File(path));
    }

    public TextGridReader(String path, String encoding) {
        this(new File(path), encoding);
    }

    public TextGridReader(File f) {
        this(f, Charset.defaultCharset().name());
    }

    public TextGridReader(File f, String encoding) {
        try {
            reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), encoding));
        } catch (IOException ex) {
            PhonLogger.warning(TextGridReader.class, ex.toString());
        }
    }

    public TextGridReader(InputStream is) {
        this(is, Charset.defaultCharset().name());
    }

    public TextGridReader(InputStream is, String encoding) {
        try {
            reader = new BufferedReader(
                    new InputStreamReader(is, encoding));
        } catch (IOException ex) {
            PhonLogger.warning(TextGridReader.class, ex.toString());
        }
    }

    public void close() {
        if(reader != null) {
            try {
                reader.close();
                reader = null;
            } catch (IOException ex) {
                PhonLogger.warning(TextGridReader.class, ex.toString());
            }
        }
    }
    
    public String nextLine() {
        String retVal = null;

        if(reader != null){
            try {
                retVal = reader.readLine();
            } catch (IOException ex) {
                PhonLogger.warning(TextGridReader.class, ex.toString());
            }
        }

        return retVal;
    }

    public TextGrid readTextGrid()
        throws ParserException {
        TextGrid retVal = new TextGrid();

        readHeader();

        float xmin = readFloatField(XMIN);
        retVal.setMin(xmin);
        
        float xmax = readFloatField(XMAX);
        retVal.setMax(xmax);
    
        readVerbatimLine(TIERS_EXISTS);
        
        float size = readFloatField(SIZE);
        
        readArrayIndexLine(ITEM);
        for(int i = 0; i < size; i++) {
            TextGridTier tier = readTier();
            retVal.addTier(tier);
        }
        
        return retVal;
    }

    /*
     * Read header
     */
    public void readHeader() throws ParserException {
        readVerbatimLine(HEADER_LINE_1);
        readVerbatimLine(HEADER_LINE_2);
        
        // skip newline after header
        nextLine();
    }

    public TextGridTier readTier() throws ParserException {
        TextGridTier retVal = new TextGridTier();

        int tierIdx = readArrayIndexLine(ITEM);
        String typeStr = readStringField(CLASS);

        TextGridTierType type = null;
        if(typeStr.equals("IntervalTier"))
            type = TextGridTierType.INTERVAL;
        else if(typeStr.equals("TextTier"))
            type = TextGridTierType.POINT;
        else
            throw new ParserException(typeStr, 0, "Invalid tier type " + typeStr);
        retVal.setTierType(type);

        String name = readStringField(NAME);
        retVal.setTierName(name);

        float xmin = readFloatField(XMIN);
        float xmax = readFloatField(XMAX);

        float size = 0.0f;

        if(type == TextGridTierType.INTERVAL)
            size = readFloatField(INTERVALS + ": " + SIZE);
        else
            size = readFloatField(POINTS + ": " + SIZE);
        
        for(int i = 0; i < size; i++) {
            TextGridInterval interval =
                    (type == TextGridTierType.INTERVAL ? readInterval()
                        : readPoint());
            retVal.addInterval(interval);
        }

        return retVal;
    }

    public TextGridInterval readInterval() throws ParserException {
        TextGridInterval retVal = null;

        readArrayIndexLine(INTERVALS);
        float xmin = readFloatField(XMIN);
        float xmax = readFloatField(XMAX);
        String text = readStringField(TEXT);

        retVal = new TextGridInterval(text, xmin, xmax);

        return retVal;
    }

    public TextGridPoint readPoint() throws ParserException {
        TextGridPoint retVal = null;

        readArrayIndexLine(POINTS);
        float time = readFloatField(TIME);
        String mark = readStringField(MARK);

        retVal = new TextGridPoint(mark, time);

        return retVal;
    }

    public int readArrayIndexLine(String arrayName) throws ParserException {        
        Pattern pattern = Pattern.compile(arrayName + " \\[([0-9])*\\]:");
        Integer retVal = 0;

        String line = nextLine();
        if(line == null)
            throw new ParserException(line, 0, "Unexpected end of input");
        line = StringUtils.strip(line);

        Matcher m = pattern.matcher(line);
        if(m.matches()) {
            String idxStr = m.group(1);
            if(idxStr == null || idxStr.length() == 0)
                idxStr = "0";
            retVal = Integer.parseInt(idxStr);
        } else {
            throw new ParserException(line, 0, "Expected " + arrayName + " [<idx>]:");
        }
        return retVal;
    }

    public void readVerbatimLine(String txt) throws ParserException {
        String line = nextLine();
        if(line == null)
            throw new ParserException(line, 0, "Unexpected end of input");
        line = StringUtils.strip(line);

        if(!line.equals(txt))
            throw new ParserException(line, 0, "Expected " + txt);
    }

    public float readFloatField(String field) throws ParserException {
        Pattern pattern = Pattern.compile(field + " = ([0-9]*(\\.[0-9]+)?)");
        Float retVal = 0.0f;

        String line = nextLine();
        if(line == null)
            throw new ParserException(line, 0, "Unexpected end of input");
        line = StringUtils.strip(line);
        Matcher m = pattern.matcher(line);
        if(m.matches()) {
            String numStr = m.group(1);

            retVal = Float.parseFloat(numStr);
        } else {
            throw new ParserException(line, 0, "Expected " + field + " = <val>");
        }

        return retVal.floatValue();
    }

    public String readStringField(String field) throws ParserException {
        Pattern pattern = Pattern.compile(field + " = \"([^\"\\\\]*)\"");
        String retVal = "";

        String line = nextLine();
        if(line == null)
            throw new ParserException(line, 0, "Unexpected end of input");
        line = StringUtils.strip(line);
        Matcher m = pattern.matcher(line);
        if(m.matches()) {
            retVal = m.group(1);
        } else {
            throw new ParserException(line, 0, "Expected " + field + " = \"<val>\"");
        }

        return retVal;
    }
}
