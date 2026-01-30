package anon.def9a2a4.dynlight.detection.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Utility class for checking if entities are underwater.
 * Used to suppress water-sensitive light sources (torches, lanterns, etc.).
 */
public final class WaterUtil {

    private WaterUtil() {
        // Utility class - no instantiation
    }

    /**
     * Check if an entity is underwater.
     * Uses eye location for living entities, regular location for others.
     *
     * @param entity The entity to check
     * @return true if the entity is in water
     */
    public static boolean isUnderwater(Entity entity) {
        Location loc = (entity instanceof LivingEntity living)
                ? living.getEyeLocation()
                : entity.getLocation();
        return loc.getBlock().getType() == Material.WATER;
    }
}
