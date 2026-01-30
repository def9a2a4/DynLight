package anon.def9a2a4.dynlight;

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
        int blockX,
        int blockY,
        int blockZ
) {
    /**
     * Compact constructor that pre-computes block coordinates.
     */
    public LightSnapshot(UUID entityId, EntityType entityType, String worldName,
                         double x, double y, double z, int lightLevel) {
        this(entityId, entityType, worldName, x, y, z, lightLevel,
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
