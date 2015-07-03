
exports.FormantOptions = function(id) {

	var maxFormantsParamInfo = {
		"id": id+".maxFormants",
		"title": "",
		"desc": "Max formants",
		"def": "4"
	};
	var maxFormantsParam;
	this.maxFormants = maxFormantsParamInfo.def;

	var windowLengthParamInfo = {
		"id": id+".windowLength",
		"title": "",
		"desc": "Window length (s)",
		"def": "0.025"
	};
	var windowLengthParam;
	this.windowLength = windowLengthParamInfo.def;
	
	var maxFreqParamInfo = {
		"id": id+".maxFreq",
		"title": "",
		"desc": "Max freq (Hz)",
		"def": "5500.0"
	};
	var maxFreqParam;
	this.maxFreq = maxFreqParamInfo.def;

	var timeStepParamInfo = {
		"id": id+".timeStep",
		"title": "",
		"desc": "Time step (s)",
		"def": "0.0"
	};
	var timeStepParam;
	this.timeStep = timeStepParamInfo.def;
	
	var preEmpParamInfo = {
		"id": id+".preEmp",
		"title": "",
		"desc": "Pre-emphasis",
		"def": "50.0"
	};
	var preEmpParam;
	this.preEmp = preEmpParamInfo.def;
	
	var tableOptsParamInfo = {
        "id":[id + ".includeIntensity", id+ ".includeNumFormants", id + ".includeBandwidths"],
        "title": "",
        "desc":[ "Include intensity", "Include numbers of formants", "Include bandwidths"],
        "def":[ true, true, true],
        "numCols": 3
    };
    var tableOptsParam;
    this.includeIntensity = tableOptsParamInfo.def[0];
    this.includeNumFormants = tableOptsParamInfo.def[1];
    this.includeBandwidths = tableOptsParamInfo.def[2];
	
	this.param_setup = function(params) {
		maxFormantsParam = new StringScriptParam(
			maxFormantsParamInfo.id,
			maxFormantsParamInfo.desc,
			maxFormantsParamInfo.def);
		
		windowLengthParam = new StringScriptParam(
			windowLengthParamInfo.id,
			windowLengthParamInfo.desc,
			windowLengthParamInfo.def);
		
		maxFreqParam = new StringScriptParam(
			maxFreqParamInfo.id,
			maxFreqParamInfo.desc,
			maxFreqParamInfo.def);
			
		timeStepParam = new StringScriptParam(
			timeStepParamInfo.id,
			timeStepParamInfo.desc,
			timeStepParamInfo.def);
			
		preEmpParam = new StringScriptParam(
			preEmpParamInfo.id,
			preEmpParamInfo.desc,
			preEmpParamInfo.def);
			
		tableOptsParam = new MultiboolScriptParam(
        tableOptsParamInfo.id,
        tableOptsParamInfo.def,
        tableOptsParamInfo.desc,
        tableOptsParamInfo.title,
        tableOptsParamInfo.numCols);
			
		params.add(maxFormantsParam);
		params.add(windowLengthParam);
		params.add(maxFreqParam);
		params.add(timeStepParam);
		params.add(preEmpParam);
		params.add(tableOptsParam);
	}

}
