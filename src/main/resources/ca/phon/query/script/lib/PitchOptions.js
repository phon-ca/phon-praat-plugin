
exports.PitchOptions = function(id) {

	var timeStepParamInfo = {
		"id": id+".timeStep",
		"title": "",
		"desc": "Time step",
		"def": 0.0
	};
	var timeStepParam;
	this.timeStep = timeStepParamInfo.def;
	
	var rangeStartParamInfo = {
		"id": id+".rangeStart",
		"title": "",
		"desc": "Range start",
		"def": 75.0
	};
	var rangeStartParam;
	this.rangeStart = rangeStartParamInfo.def;
	
	var rangeEndParamInfo = {
		"id": id+".rangeEnd",
		"title": "",
		"desc": "Range end",
		"def": 500.0
	};
	var rangeEndParam;
	this.rangeEnd = rangeEndParamInfo.def;
	
	var unitTypeParamInfo = {
        "id": id + ".unitType",
        "title": "Units",
        "desc":[ "HERTZ", "HERTZ_LOGARITHMIC", "MEL", "LOG_HERTZ", "SEMITONES_1", "SEMITONES_100", 
                 "SEMITONES_200", "SEMITONES_440", "ERB"],
        "def": 0
    };
    var unitTypeParam;
    this.unitType = {
        "index": 0, "toString": "HERTZ"
    };
    
    var corTypeParamInfo = {
    	"id": id + ".corType",
    	"title": "Correlation",
    	"desc":[ "Auto-correlate", "Cross-correlate" ],
    	"def": 0
    };
    var corTypeParam;
    this.corType = {
    	"index": 0, "toString": "Auto-correlate"
    };
    
    var veryAccurateParamInfo = {
    	"id": id + ".veryAccurate",
    	"title": "Very accurate",
    	"desc": "",
    	"def": false
    };
    var veryAccurateParam;
    this.veryAccurate = veryAccurateParamInfo.def;
    
    var maxCandidatesParamInfo = {
    	"id": id + ".maxCandidates",
    	"title": "",
    	"desc": "Max candidates",
    	"def": 15
    };
    var maxCandidatesParam;
    this.maxCandidates = maxCandidatesParamInfo.def;
    
    var silenceThresholdParamInfo = {
    	"id": id + ".silenceThreshold",
    	"title": "",
    	"desc": "Silence threshold",
    	"def": 0.03
    };
    var silenceThresholdParam;
    this.silenceThreshold = silenceThresholdParamInfo.def;
    
    var voicingThresholdParamInfo = {
    	"id": id + ".voicingThreshold",
    	"title": "",
    	"desc": "Voicing threshold",
    	"def": 0.45
    };
    var voicingThresholdParam;
    this.voicingThreshold = voicingThresholdParamInfo.def;
    
    var octaveCostParamInfo = {
    	"id": id + ".octaveCost",
    	"title": "",
    	"desc": "Octave cost",
    	"def": 0.01
    };
    var octaveCostParam;
    this.octaveCost = octaveCostParamInfo.def;
    
    var octaveJumpCostParamInfo = {
    	"id": id + ".octaveJumpCost",
    	"title": "",
    	"desc": "Octave jump cost",
    	"def": 0.35
    };
    var octaveJumpCostParam;
    this.octaveJumpCost = octaveJumpCostParamInfo.def;
    
    var vUnvCostParamInfo = {
    	"id": id + ".vUnvCost",
    	"title": "",
    	"desc": "Voiced/unvoiced cost",
    	"def": 0.14
    };
    var vUnvCostParam;
    this.vUnvCost = vUnvCostParamInfo.def;
    
    this.param_setup = function(params) {
    	timeStepParam = new StringScriptParam(
    			timeStepParamInfo.id,
    			timeStepParamInfo.desc,
    			timeStepParamInfo.def);
    	rangeStartParam = new StringScriptParam(
    			rangeStartParamInfo.id,
    			rangeStartParamInfo.desc,
    			rangeStartParamInfo.def);
    	rangeEndParam = new StringScriptParam(
    			rangeEndParamInfo.id,
    			rangeEndParamInfo.desc,
    			rangeEndParamInfo.def);
        unitTypeParam = new EnumScriptParam(
	        unitTypeParamInfo.id,
	        unitTypeParamInfo.title,
	        unitTypeParamInfo.def,
	        unitTypeParamInfo.desc);
        corTypeParam = new EnumScriptParam(
        	corTypeParamInfo.id,
        	corTypeParamInfo.title,
        	corTypeParamInfo.def,
        	corTypeParamInfo.desc);
        veryAccurateParam = new BooleanScriptParam(
        	veryAccurateParamInfo.id,
        	veryAccurateParamInfo.title,
        	veryAccurateParamInfo.desc,
        	veryAccurateParamInfo.def);
        
        var advParamsSep = new SeparatorScriptParam("Advanced Pitch Options", true);

        maxCandidatesParam = new StringScriptParam(
        	maxCandidatesParamInfo.id,
        	maxCandidatesParamInfo.desc,
        	maxCandidatesParamInfo.def);
        silenceThresholdParam = new StringScriptParam(
        	silenceThresholdParamInfo.id,
        	silenceThresholdParamInfo.desc,
        	silenceThresholdParamInfo.def);
        voicingThresholdParam = new StringScriptParam(
        	voicingThresholdParamInfo.id,
        	voicingThresholdParamInfo.desc,
        	voicingThresholdParamInfo.def);
        octaveCostParam = new StringScriptParam(
        	octaveCostParamInfo.id,
        	octaveCostParamInfo.desc,
        	octaveCostParamInfo.def);
        octaveJumpCostParam = new StringScriptParam(
    		octaveJumpCostParamInfo.id,
    		octaveJumpCostParamInfo.desc,
    		octaveJumpCostParamInfo.def);
        vUnvCostParam = new StringScriptParam(
        	vUnvCostParamInfo.id,
        	vUnvCostParamInfo.desc,
        	vUnvCostParamInfo.def);
        
        params.add(timeStepParam);
        params.add(rangeStartParam);
        params.add(rangeEndParam);
        params.add(unitTypeParam);
        params.add(corTypeParam);
        params.add(veryAccurateParam);
        
        params.add(advParamsSep);
        params.add(maxCandidatesParam);
        params.add(silenceThresholdParam);
        params.add(voicingThresholdParam);
        params.add(octaveCostParam);
        params.add(octaveJumpCostParam);
        params.add(vUnvCostParam);
    };
    
}
