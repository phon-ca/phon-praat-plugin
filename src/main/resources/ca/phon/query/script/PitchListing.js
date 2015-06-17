/*
params =
		{enum, searchTier, "IPA Target"|"IPA Actual", 1, "<html><b>Search Tier</b></html>"}
	;
*/
importClass(java.util.concurrent.atomic.AtomicReference)

var TextGridNameOptions = require("lib/TextGridNameOptions").TextGridNameOptions;
var PitchOptions = require("lib/PitchOptions").PitchOptions;
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
	"textGridName" : new TextGridNameOptions("filters.textGridName"),
    "primary": new PatternFilter("filters.primary"),
    "pitchOpts": new PitchOptions("filters.pitchOpts"),
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
	
	filters.textGridName.param_setup(params);

	var pitchSep = new SeparatorScriptParam("Pitch Options", false);
	params.add(pitchSep);
	filters.pitchOpts.param_setup(params);
	
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
var textGrid;
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
	
	textGridManager = new TextGridManager(project);
	
	wavPath = getMediaFile(s);
	if(wavPath != null) { 
		wavPath = wavPath.getAbsolutePath();
	
		// load our audio file
		var mf = MelderFile.fromPath(wavPath);
		longSound = LongSound.open(mf);
	}
	
	try {
		tgName = (filters.textGridName.name.length() > 0 ? filters.textGridName.name : textGridManager.defaultTextGridName(s.corpus, s.name));
		textGrid = textGridManager.openTextGrid(s.corpus, s.name, tgName);
		if(textGrid == null) {
			err.println("No TextGrid found for session with name " + tgName);
		}
	} catch (e) {
		err.println(e.message);
	}
}

function annotateRecord(r) {
	if(textGrid == null) return;
	
	// extract record from textgrid
	if(r.segment.numberOfGroups() == 0) return;
	mediaSeg = r.segment.getGroup(0);
	
	startTime = mediaSeg.startValue / 1000.0;
	endTime = mediaSeg.endValue / 1000.0;
	if(endTime - startTime <= 0) return;
	
	if(startTime <= textGrid.xmin || endTime >= textGrid.xmax) return;
	
	tg = textGrid.extractPart(startTime, endTime, 1);
	if(tg == null) return;
	
	tga = new TextGridAnnotator();
	tga.annotateRecord(tg, r);
}

function listPitch(recordIndex, groupIndex, pitch, ipa) {
	tgi = ipa.textInterval;
	if(tgi == null && ipa.length() > 0) {
		tgi = ipa.elementAt(0).textInterval;
		if(tgi == null) {
			err.println("No TextGrid information for " + ipa);
			return;
		}
		if(ipa.length() > 1 && ipa.elementAt(ipa.length()-1).textInterval) {
			tgi = TextInterval(
				tgi.getXmin(), ipa.elementAt(ipa.length()-1).textGridInterval.getXmax(), ipa.text);
		}
	}
	
	var ixminPtr = new AtomicReference(new java.lang.Long(0));
	var ixmaxPtr = new AtomicReference(new java.lang.Long(0));
	
	pitch.getWindowSamples(tgi.getXmin(), tgi.getXmax(), ixminPtr, ixmaxPtr);
	
	var xmin = ixminPtr.get().intValue();
	var xmax = ixmaxPtr.get().intValue();
	
	var pitchUnit = kPitch_unit.fromString(filters.pitchOpts.unitType);
	var unitTxt = pitch.getUnitText(Pitch.LEVEL_FREQUENCY, pitchUnit,
	    Packages.ca.hedlund.jpraat.binding.fon.Function.UNIT_TEXT_SHORT);
	
	var nf = java.text.NumberFormat.getNumberInstance();
	nf.setMaximumFractionDigits(6);
	
	// print header
	if(!printedTableHeader) {
	    out.println("\"record\",\"group\",\"ipa\",\"Time(s)\",\"F0(" + unitTxt + ")\"");
	    printedTableHeader = true;
	}
	
    for(i = xmin; i <= xmax; i++) {
        var t = pitch.indexToX(i);
        var f0 = pitch.getValueAtSample(i, Pitch.LEVEL_FREQUENCY, pitchUnit.ordinal());
        f0 = pitch.convertToNonlogarithmic(f0, Pitch.LEVEL_FREQUENCY, pitchUnit.ordinal());
        
        if(i == xmin) {
            out.print("\"" + (recordIndex+1) + "\",");
            out.print("\"" + (groupIndex+1) + "\",");
        } else {
            out.print("\"\",");
            out.print("\"\",");
        }
       
        out.print("\"" + ipa + "\",");
        out.print("\"" + nf.format(t) + "\",");
        out.print("\"" + nf.format(f0) + "\"\n");
    }
    out.flush();
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
	var pitch = null;
	if(segment) {
		sound = longSound.extractPart(segment.startValue / 1000.0, segment.endValue / 1000.0, 1);
		if(filters.pitchOpts.corType.index == 0) {
		    // auto-correlate
		    pitch = sound.to_Pitch_ac(filters.pitchOpts.timeStep, filters.pitchOpts.rangeStart, 3.0, 
					filters.pitchOpts.maxCandidates, (filters.pitchOpts.veryAccurate ? 1 : 0), filters.pitchOpts.silenceThreshold, 
					filters.pitchOpts.voicingThreshold, filters.pitchOpts.octaveCost, 
					filters.pitchOpts.octaveJumpCost, filters.pitchOpts.vUnvCost, filters.pitchOpts.rangeEnd);
		} else {
		    // cross-correlate
		    pitch = sound.to_Pitch_cc(filters.pitchOpts.timeStep, filters.pitchOpts.rangeStart, 3.0, 
				filters.pitchOpts.maxCandidates, (filters.pitchOpts.veryAccurate ? 1 : 0), filters.pitchOpts.silenceThreshold, 
				filters.pitchOpts.voicingThreshold, filters.pitchOpts.octaveCost, 
				filters.pitchOpts.octaveJumpCost, filters.pitchOpts.vUnvCost, filters.pitchOpts.rangeEnd);
		}
	} else {
		err.println("Record " + recordIndex + " does not have segment information.");
		return;
	}
	
	if(pitch == null) {
		err.println("Unable to load pitch information for record " + recordIndex);
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
    	        
    	        listPitch(recordIndex, i, pitch, match.value);
    	        
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
