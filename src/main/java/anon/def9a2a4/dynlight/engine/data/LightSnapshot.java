package anon.def9a2a4.dynlight.engine.data;

import org.bukkit.entity.EntityType;

import java.util.UUID;

/**
 * Immutable snapshot of a light-emitting entity for async processing.
 * Uses primitives instead of Bukkit objects (Location is not thread-safe).
 * Block coordinates are pre-computed at creation to avoid repeated Math.floor() calls.
 */
public record LightSnapshot(
        UUID entityId,
        EntityType entityType,
        String worldName,
        double x,
        double y,
        double z,
        int lightLevel,
        int horizontalRadius,
        int height,
        int blockX,
        int blockY,
        int blockZ
) {
    private static final int DEFAULT_RADIUS = 2;
    private static final int DEFAULT_HEIGHT = 3;

    /**
     * Compact constructor with default radius/height.
     * Pre-computes block coordinates.
     */
    public LightSnapshot(UUID entityId, EntityType entityType, String worldName,
                         double x, double y, double z, int lightLevel) {
        this(entityId, entityType, worldName, x, y, z, lightLevel,
                DEFAULT_RADIUS, DEFAULT_HEIGHT,
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /**
     * Constructor with custom radius/height.
     */
    public LightSnapshot(UUID entityId, EntityType entityType, String worldName,
                         double x, double y, double z, int lightLevel,
                         int horizontalRadius, int height) {
        this(entityId, entityType, worldName, x, y, z, lightLevel,
                horizontalRadius, height,
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
