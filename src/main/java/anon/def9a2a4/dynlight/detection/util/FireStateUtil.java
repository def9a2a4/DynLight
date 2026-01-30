package anon.def9a2a4.dynlight.detection.util;

import org.bukkit.entity.Entity;

/**
 * Utility class for checking entity fire state.
 * Consolidates fire checking logic used across multiple listeners.
 */
public final class FireStateUtil {

    private FireStateUtil() {
        // Utility class - no instantiation
    }

    /**
     * Check if an entity is on fire (either actually burning or visually on fire).
     *
     * @param entity The entity to check
     * @return true if the entity is on fire
     */
    public static boolean isOnFire(Entity entity) {
        return entity.getFireTicks() > 0 || entity.isVisualFire();
    }
}
