
exports.IntensityOptions = function(id) {

	var timeStepParamInfo = {
		"id": id+".timeStep",
		"title": "",
		"desc": "Time step",
		"def": 0.0
	};
	var timeStepParam;
	this.timeStep = timeStepParamInfo.def;
	
	var minPitchParamInfo = {
		"id": id+".minPitch",
		"title": "",
		"desc": "Minimum pitch (Hz)",
		"def": 100.0
	};
	var minPitchParam;
	this.minPitch = minPitchParamInfo.def;
	
	var subtractMeanParamInfo = {
		"id": id+".subtractMean",
		"title": "Subtract mean",
		"desc": "",
		"def": true
	};
	var subtractMeanParam;
	this.subtractMean = subtractMeanParamInfo.def;
	
	this.param_setup = function(params) {
		timeStepParam = new StringScriptParam(
			timeStepParamInfo.id,
			timeStepParamInfo.desc,
			timeStepParamInfo.def);
		
		minPitchParam = new StringScriptParam(
			minPitchParamInfo.id,
			minPitchParamInfo.desc,
			minPitchParamInfo.def);
		
		subtractMeanParam = new BooleanScriptParam(
			subtractMeanParamInfo.id,
			subtractMeanParamInfo.title,
			subtractMeanParamInfo.desc,
			subtractMeanParamInfo.def);
		
		params.add(timeStepParam);
		params.add(minPitchParam);
		params.add(subtractMeanParam);
	};

}
