package anon.def9a2a4.dynlight;

/**
 * Configuration for a specific item's light behavior.
 *
 * @param lightLevel     Light level emitted (1-15)
 * @param waterSensitive Whether light is suppressed underwater
 */
public record ItemLightConfig(
        int lightLevel,
        boolean waterSensitive
) {
    /**
     * Create config for simple integer format (backwards compatible).
     * Defaults to NOT water-sensitive.
     */
    public static ItemLightConfig simple(int lightLevel) {
        return new ItemLightConfig(lightLevel, false);
    }
}
