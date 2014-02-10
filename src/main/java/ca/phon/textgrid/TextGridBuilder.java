package ca.phon.textgrid;

/**
 * <p>Helper class for building {@link TextGrid} objects.<br/>
 * 
 * E.g.,<br/>
 * <pre>
 * TextGridBuilder builder = new TextGridBuilder();
 * builder.minTime(0.0f).maxTime(1000.0f); // Set TextGrid min/max times
 * builder.newIntervalTier("Orthography"); // create a new interval tier 'Orthopgraphy'
 * builder.addInterval(100.0f, "Hello world!"); // set text for interval in Orthography tier to 'Hello world!'
 * </pre>
 *
 */
public class TextGridBuilder {
	
	/**
	 * The text grid being constructed
	 */
	private final TextGrid textGrid;
	
	/**
	 * The current TextGridTier
	 * 
	 */
	private TextGridTier currentTier;
	
	/**
	 * Constructor
	 */
	public TextGridBuilder() {
		super();
		this.textGrid = new TextGrid();
	}

	/**
	 * Set the minimum time for the TextGrid
	 * 
	 * @param minTime (in seconds)
	 */
	public TextGridBuilder minTime(float minTime) {
		textGrid.setMin(minTime);
		return this;
	}
	
	/**
	 * Set the maximum time of the TextGrid
	 * 
	 * @param maxTime (in seconds)
	 */
	public TextGridBuilder maxTime(float maxTime) {
		textGrid.setMax(maxTime);
		return this;
	}
	
	/**
	 * Create a new point tier with the given name
	 * 
	 * @param tierName
	 */
	public TextGridBuilder newPointTier(String tierName) {
		final TextGridTier tier = new TextGridTier(tierName, TextGridTierType.POINT);
		textGrid.addTier(currentTier);
		currentTier = tier;
		return this;
	}
	
	/**
	 * Create a new interval tier with the given name
	 * 
	 * @param tierName
	 */
	public TextGridBuilder newIntervalTier(String tierName) {
		final TextGridTier tier = new TextGridTier(tierName, TextGridTierType.INTERVAL);
		textGrid.addTier(tier);
		currentTier = tier;
		return this;
	}
	
	
}
