package anon.def9a2a4.dynlight;

/**
 * Configuration for a specific entity type's light behavior.
 *
 * @param baseLight        Light level emitted always (0 = only when on fire)
 * @param fireLight        Light level when on fire
 * @param horizontalRadius Blocks out from center for light placement (0=1x1, 1=3x3, 2=5x5)
 * @param height           Blocks upward from feet level
 */
public record EntityLightConfig(
        int baseLight,
        int fireLight,
        int horizontalRadius,
        int height
) {
    /**
     * Default configuration for unknown entity types.
     * base-light=0, fire-light=12, horizontal-radius=1, height=2
     */
    public static final EntityLightConfig DEFAULT = new EntityLightConfig(0, 12, 1, 2);
}
