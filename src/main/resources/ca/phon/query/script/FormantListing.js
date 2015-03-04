/*
params =
		{enum, searchTier, "IPA Target"|"IPA Actual", 1, "<html><b>Search Tier</b></html>"}
	;
*/

var FormantOptions = require("lib/FormantOptions").FormantOptions;
var GroupFilter = require("lib/GroupFilter").GroupFilter;
var AlignedGroupFilter = require("lib/TierFilter").TierFilter;
var WordFilter = require("lib/WordFilter").WordFilter;
var AlignedWordFilter = require("lib/TierFilter").TierFilter;
var SyllableFilter = require("lib/SyllableFilter").SyllableFilter;
var ParticipantFilter = require("lib/ParticipantFilter").ParticipantFilter;
var PatternFilter = require("lib/PatternFilter").PatternFilter;
var PatternType = require("lib/PatternFilter").PatternType;

/********************************
 * Setup params
 *******************************/

var filters = {
    "primary": new PatternFilter("filters.primary"),
    "formantOpts": new FormantOptions("filters.formantOpts"),
    "group": new GroupFilter("filters.group"),
    "groupPattern": new PatternFilter("filters.groupPattern"),
    "alignedGroup": new AlignedGroupFilter("filters.alignedGroup"),
    "word": new WordFilter("filters.word"),
    "wordPattern": new PatternFilter("filters.wordPattern"),
    "alignedWord": new AlignedWordFilter("filters.alignedWord"),
    "syllable": new SyllableFilter("filters.syllable"),
    "speaker": new ParticipantFilter("filters.speaker")
};


function setup_params(params) {
	filters.primary.setSelectedPatternType(PatternType.PHONEX);
	filters.primary.param_setup(params);

	var formantSep = new SeparatorScriptParam("Formant Options", false);
	params.add(formantSep);
	filters.formantOpts.param_setup(params);
	
	filters.group.param_setup(params);
	filters.groupPattern.param_setup(params);
	var sep = new LabelScriptParam("", "<html><b>Aligned Group</b></html>");
	params.add(sep);
	filters.alignedGroup.param_setup(params);
	
	filters.word.param_setup(params);
	filters.wordPattern.param_setup(params);
    filters.wordPattern.setEnabled(false);
	var wordsep = new LabelScriptParam("", "<html><b>Aligned Word</b></html>");
    params.add(wordsep);
    filters.alignedWord.param_setup(params);
    var searchByWordListener = new java.beans.PropertyChangeListener {
        propertyChange: function(e) {
            var enabled = e.source.getValue(e.source.paramId);
            filters.wordPattern.setEnabled(enabled);
            filters.alignedWord.setEnabled(enabled);
        }    
    };
    filters.word.searchByWordOpt.addPropertyChangeListener(filters.word.searchByWordOpt.paramId, searchByWordListener);
    var enabled = filters.word.searchByWordOpt.getValue(filters.word.searchByWordOpt.paramId);
    filters.wordPattern.setEnabled(enabled);
    filters.alignedWord.setEnabled(enabled);
    
	filters.syllable.param_setup(params);
	filters.speaker.param_setup(params);
}

importPackage(java.io)
importPackage(Packages.ca.phon.textgrid)
importPackage(Packages.ca.phon.plugins.praat)
importPackage(Packages.ca.hedlund.jpraat.binding)
importPackage(Packages.ca.hedlund.jpraat.binding.fon)
importPackage(Packages.ca.hedlund.jpraat.binding.sys)
importPackage(Packages.ca.phon.media.util)

/*
 * Globals
 */
var session;
var textGridManager;
var longSound;

var printedTableHeader = false;

function getMediaFile(s) {
	
		selectedMedia = 
				MediaLocator.findMediaFile(project, s);
		if(selectedMedia == null) return null;
		audioFile = null;
		
		lastDot = selectedMedia.getName().lastIndexOf('.');
		mediaName = selectedMedia.getName();
		if(lastDot >= 0) {
			mediaName = mediaName.substring(0, lastDot);
		}
		if(!selectedMedia.isAbsolute()) selectedMedia = 
			MediaLocator.findMediaFile(s.getMediaLocation(), project, s.getCorpus());
		
		if(selectedMedia != null) {
			parentFile = selectedMedia.getParentFile();
			audioFile = new File(parentFile, mediaName + ".wav");
			
			if(!audioFile.exists()) {
				audioFile = null;
			}
		}
		return audioFile;

}

function begin_search(s) {
	session = s;
	printedTableHeader = false;
	
	textGridManager = TextGridManager.getInstance(project);
	
	wavPath = getMediaFile(s);
	if(wavPath != null) { 
		wavPath = wavPath.getAbsolutePath();
	
		// load our audio file
		var mf = MelderFile.fromPath(wavPath);
		longSound = LongSound.open(mf);
	}
}

function annotateRecord(r) {
	tg = textGridManager.loadTextGrid(r.uuid.toString());
	if(tg == null) {
		err.println( "No text grid for record " + r.uuid);
		return;
	}
	tga = new TextGridAnnotator();
	tga.annotateRecord(tg, r);
}

function listFormants(recordIndex, groupIndex, formants, ipa) {
	tgi = ipa.textGridInterval;
	if(tgi == null && ipa.length() > 0) {
		tgi = ipa.elementAt(0).textGridInterval;
		if(tgi == null) {
			err.println("No TextGrid information for " + ipa);
			return;
		}
		if(ipa.length() > 1 && ipa.elementAt(ipa.length()-1).textGridInterval) {
			tgi = new TextGridInterval(ipa.text,
				tgi.start, ipa.elementAt(ipa.length()-1).textGridInterval.end);
		}
	}
	
	var formantTable = formants.downto_Table(
		false, // never include frame numbers
		true, // always include times
		6,
		filters.formantOpts.includeIntensity, 6,
		filters.formantOpts.includeNumFormants, 6,
		filters.formantOpts.includeBandwidths);
	
	if(!printedTableHeader) {
		printedTableHeader = true;
		
		// print ipa column
		out.print("\"record\",\"group\",\"ipa\"");
		
		for(col = 1; col <= formantTable.getNcol(); col++) {
			out.print(",\"" + formantTable.getColStr(col) + "\"");
		}
		out.println();
	}
	var printedPrefix = false;
	for(row = 1; row < formantTable.getNrow(); row++) {
		// get time
		rowTime = formantTable.getNumericValue_Assert(row, 1);
		if(rowTime > tgi.end) break;
		if(rowTime >= tgi.start) {
		    if(!printedPrefix) {
		        out.print("\"" + (recordIndex+1) + "\",");
		        out.print("\"" + (groupIndex+1) + "\",");
		        printedPrefix = true;
		    } else {
		        out.print("\"\",");
		        out.print("\"\",");
		    }
			out.print("\"" + ipa.toString() + "\"");
			for(col = 1; col <= formantTable.getNcol(); col++) {
				out.print(",\"" + formantTable.getNumericValue(row, col) + "\"");
			}
			out.println();
		}
	}
}

function query_record(recordIndex, record) {
	// check participant filter
    if(!filters.speaker.check_speaker(record.speaker)) return;
    
    // check group+groupPattern filters
	var groups = filters.group.getRequestedGroups(record);
    if(filters.groupPattern.isUseFilter()) {
        groups = filters.groupPattern.filter_groups(groups, searchTier);
    }
	
	// check aligned group for each group returned
	if(filters.alignedGroup.isUseFilter()) {
	    groups = filters.alignedGroup.filter_groups(record, groups);
	}
	
	// annotate record with text grid information
	annotateRecord(record);
	
	// get formant information
	segment = (record.segment.numberOfGroups() == 1 ? record.segment.getGroup(0) : null);
	var formants = null;
	if(segment) {
		sound = longSound.extractPart(segment.startValue / 1000.0, segment.endValue / 1000.0, 1);
		formants = sound.to_Formant_burg(
			filters.formantOpts.timeStep,
			filters.formantOpts.maxFormants,
			filters.formantOpts.maxFreq,
			filters.formantOpts.windowLength,
			filters.formantOpts.preEmp);
	} else {
		err.println("Record " + recordIndex + " does not have segment information.");
		return;
	}
	
	if(formants == null) {
		err.println("Unable to load formant information for record " + recordIndex);
		return;
	}

	// perform searches
	for(var i = 0; i < groups.length; i++)
	{
		var group = groups[i];
		
		var ipa = (searchTier == "IPA Target" ? group.IPATarget : group.IPAActual);
		
		var toSearch = new Array();
		toSearch.push(ipa);
		
		// search by word?
		if(filters.word.isUseFilter()) {
		   toSearch.length = 0;
		   var selectedWords = filters.word.getRequestedWords(group);
		   for(j = 0; j < selectedWords.length; j++) {
		       var word = selectedWords[j];
		       var addWord = true;

               var wordIpa = (searchTier == "IPA Target" ? word.IPATarget : word.IPAActual);
               // check word pattern if necessary
		       if(filters.wordPattern.isUseFilter()) {
		           addWord = filters.wordPattern.check_filter(wordIpa);
		       }
		      
		       // check aligned word pattern if necessary
		       if(filters.alignedWord.isUseFilter()) {
		           addWord = filters.alignedWord.check_word(word);
		       }
		       
		       if(addWord) {
		           toSearch.push(wordIpa);
		       }
		   }
		}
		
		// search by syllable?
		if(filters.syllable.isUseFilter()) {
		    var syllList = new Array();
		    for(j = 0; j < toSearch.length; j++) {
		        var obj = toSearch[j];
		        var sylls = filters.syllable.getRequestedSyllables(obj);
		        
		        for(k = 0; k < sylls.length; k++) {
		            syllList.push(sylls[k]);
		        }
		    }
		    toSearch = syllList;
		}
		
		for(j = 0; j < toSearch.length; j++) {
		    var obj = toSearch[j];
		    var matches = filters.primary.find_pattern(obj);
		    
		    for(k = 0; k < matches.length; k++) {
    	        var match = matches[k];
    	        
    	        listFormants(recordIndex, i, formants, match.value);
    	        
    			var result = factory.createResult();
    			// calculate start/end positions of data in text
    			var startIndex = ipa.stringIndexOf(match.value);
    			var length = match.value.toString().length();
    			
    			result.recordIndex = recordIndex;
    			result.schema = "LINEAR";
    
    			var rv = factory.createResultValue();
    			rv.tierName = searchTier;
    			rv.groupIndex = group.groupIndex;
    			rv.range = new Range(startIndex, startIndex + length, false);
    			rv.data = match.value;
    			result.addResultValue(rv);
    			
    			results.addResult(result);
    	    }
		}
	}
}
