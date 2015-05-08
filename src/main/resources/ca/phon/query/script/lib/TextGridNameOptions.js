
exports.TextGridNameOptions = function(id) {
	
	var textGridNameParamInfo = {
	        "id": id + ".name",
	        "title": "Use TextGrid:",
	        "prompt": "Enter TextGrid name, leave empty to use default TextGrid",
	        "def": ""
	    };
    this.textGridNameParam;
    this.textGridName = textGridNameParamInfo.def;
		
    this.param_setup = function(params) {
		textGridNameParam = new StringScriptParam(
				textGridNameParamInfo.id,
				textGridNameParamInfo.title,
				textGridNameParamInfo.def);
		textGridNameParam.setPrompt(textGridNameParamInfo.prompt);
		
		params.add(textGridNameParam);
	}
    
};
