package anon.def9a2a4.dynlight.detection.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

/**
 * Utility class for entity type filtering.
 * Provides consistent entity filtering logic across listeners.
 */
public final class EntityFilters {

    private EntityFilters() {
        // Utility class - no instantiation
    }

    /**
     * Check if an entity should be skipped for burning entity detection.
     * Players, items, and projectiles are handled by their own dedicated listeners.
     *
     * @param entity The entity to check
     * @return true if the entity should be skipped
     */
    public static boolean shouldSkipForBurning(Entity entity) {
        return entity instanceof Player || entity instanceof Item || entity instanceof Projectile;
    }
}
