package anon.def9a2a4.dynlight.engine.data;

import org.bukkit.entity.EntityType;

import java.util.UUID;

/**
 * Immutable snapshot of a light-emitting entity for async processing.
 * Uses primitives instead of Bukkit objects (Location is not thread-safe).
 * Block coordinates are pre-computed at creation to avoid repeated Math.floor() calls.
 *
 * <p>Light sources can optionally have a parent entity (via parentId). Child lights
 * are automatically invalidated when their parent is removed, preventing orphaned
 * light blocks from async race conditions.</p>
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
        int blockZ,
        UUID parentId
) {
    private static final int DEFAULT_RADIUS = 2;
    private static final int DEFAULT_HEIGHT = 3;

    /**
     * Compact constructor for primary light sources (no parent).
     * Pre-computes block coordinates with default radius/height.
     */
    public LightSnapshot(UUID entityId, EntityType entityType, String worldName,
                         double x, double y, double z, int lightLevel) {
        this(entityId, entityType, worldName, x, y, z, lightLevel,
                DEFAULT_RADIUS, DEFAULT_HEIGHT,
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), null);
    }

    /**
     * Constructor with custom radius/height for primary light sources.
     */
    public LightSnapshot(UUID entityId, EntityType entityType, String worldName,
                         double x, double y, double z, int lightLevel,
                         int horizontalRadius, int height) {
        this(entityId, entityType, worldName, x, y, z, lightLevel,
                horizontalRadius, height,
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), null);
    }

    /**
     * Constructor for child light sources (e.g., trail lights).
     * Pre-computes block coordinates with default radius/height.
     *
     * @param parentId The UUID of the parent entity that owns this child light
     */
    public LightSnapshot(UUID entityId, EntityType entityType, String worldName,
                         double x, double y, double z, int lightLevel, UUID parentId) {
        this(entityId, entityType, worldName, x, y, z, lightLevel,
                DEFAULT_RADIUS, DEFAULT_HEIGHT,
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), parentId);
    }

    /**
     * Check if this is a child light source.
     *
     * @return true if this light has a parent entity
     */
    public boolean isChildLight() {
        return parentId != null;
    }
}
