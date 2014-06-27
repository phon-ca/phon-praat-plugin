
exports.FormantOptions = function(id) {

	var maxFormantsParamInfo = {
		"id": id+".maxFormants",
		"title": "",
		"desc": "Max formants",
		"def": "5"
	};
	var maxFormantsParam;
	this.maxFormants = maxFormantsParamInfo.def;

	var windowLengthParamInfo = {
		"id": id+".windowLength",
		"title": "",
		"desc": "Window length (s)",
		"def": "0.2"
	};
	var windowLengthParam;
	this.windowLength = windowLengthParamInfo.def;
	
	var maxFreqParamInfo = {
		"id": id+".maxFreq",
		"title": "",
		"desc": "Max freq (Hz)",
		"def": "5000.0"
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
			
		params.add(maxFormantsParam);
		params.add(windowLengthParam);
		params.add(maxFreqParam);
		params.add(timeStepParam);
		params.add(preEmpParam);
	}

}
