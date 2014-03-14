package ca.phon.plugins.praat.script;

/**
 * Interface for handling messages from Praat.
 */
public interface PraatScriptTcpHandler {

	/**
	 * Indicates that a Praat script has finished
	 * 
	 * @param data the data returned by the script
	 *  or <code>null</code>
	 */
	public void praatScriptFinished(String data);
	
}
