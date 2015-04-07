package ca.phon.plugins.praat.script;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 */
public class PraatScriptParams {
	
	private final static Logger LOGGER = Logger
			.getLogger(PraatScriptParams.class.getName());

	private final static String PARAM_REGEX = "# BEGIN PARAMS #(.*)# END PARAMS #";
	
	private final static int REGEX_OPTIONS = Pattern.MULTILINE | Pattern.DOTALL;
	
	/**
	 * Extract script param section from given text
	 * 
	 * @param text
	 * @return param section if found
	 */
	public static String extractParamSection(String text) {
		final Pattern pattern = Pattern.compile(PARAM_REGEX, REGEX_OPTIONS);
		final Matcher matcher = pattern.matcher(text);
		if(matcher.find()) {
			return matcher.group(1).replaceAll("#", "");
		}
		return null;
	}
	
	public static List<PraatScriptParam> parseScriptParams(String json) {
		final List<PraatScriptParam> retVal = new ArrayList<PraatScriptParam>();
		
//		final JSONTokener tokenizer = new JSONTokener(json);
//		final JSONArray jsonArray = new JSONArray(tokenizer);
//		
//		for(int i = 0; i < jsonArray.length(); i++) {
//			final JSONObject jsonObj = jsonArray.getJSONObject(i);
//			
//			final String name = jsonObj.getString("name");
//			final String label = jsonObj.getString("label");
//			final String type = jsonObj.getString("type");
//			final String prompt = jsonObj.getString("prompt");
//			final Object defVal = jsonObj.get("default");
//			
//			
//			try {
//				final Class<?> clazz = Class.forName(type);
//				
//				final PraatScriptParam param = new PraatScriptParam();
//				param.setName(name);
//				param.setType(clazz);
//				param.setDescription(label);
//				param.setPrompt(prompt);
//				param.setDefaultValue(defVal);
//				retVal.add(param);
//			} catch (ClassNotFoundException e) {
//				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
//			}
//		}
//		
		return retVal;
	}
	
}
