package anon.def9a2a4.dynlight.api;

/**
 * Information about a light source returned by detectors.
 * Contains the light level and placement parameters.
 */
public record LightSourceInfo(
        int lightLevel,       // 1-15
        int horizontalRadius, // 0-5, blocks out from center
        int height            // 1-10, blocks upward
) {
    private static final int DEFAULT_RADIUS = 2;
    private static final int DEFAULT_HEIGHT = 3;

    /**
     * Create light info with default radius and height.
     */
    public static LightSourceInfo of(int lightLevel) {
        return new LightSourceInfo(lightLevel, DEFAULT_RADIUS, DEFAULT_HEIGHT);
    }

    /**
     * Create light info with custom parameters.
     */
    public static LightSourceInfo of(int lightLevel, int horizontalRadius, int height) {
        return new LightSourceInfo(lightLevel, horizontalRadius, height);
    }
}
