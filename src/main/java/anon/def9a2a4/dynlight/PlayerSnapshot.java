package anon.def9a2a4.dynlight;

import java.util.UUID;

/**
 * Immutable snapshot of a player's position for async processing.
 * Uses primitives instead of Bukkit objects (Location is not thread-safe).
 */
public record PlayerSnapshot(
        UUID playerId,
        String worldName,
        double x,
        double y,
        double z
) {
    /**
     * Calculate squared distance to a light source (for render distance checks).
     */
    public double distanceSquaredTo(LightSnapshot light) {
        double dx = x - light.x();
        double dy = y - light.y();
        double dz = z - light.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
