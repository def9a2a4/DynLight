package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.EntityLightConfig;
import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.detection.util.EntityFilters;
import anon.def9a2a4.dynlight.detection.util.FireStateUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-driven listener for burning entities.
 * Tracks entities when they catch fire and uses a periodic sweep to detect fire expiration.
 */
public class BurningEntityListener implements Listener {

    private final DynLightConfig config;
    private final DynLightAPI api;
    private final Set<UUID> burningEntities = ConcurrentHashMap.newKeySet();

    public BurningEntityListener(DynLightConfig config, DynLightAPI api) {
        this.config = config;
        this.api = api;
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        if (!config.burningEntitiesEnabled) {
            return;
        }

        Entity entity = event.getEntity();

        // Skip players (handled by PlayerLightDetector), items, and projectiles
        if (EntityFilters.shouldSkipForBurning(entity)) {
            return;
        }

        trackBurningEntity(entity);
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!config.burningEntitiesEnabled) {
            return;
        }

        Entity entity = event.getEntity();

        // Skip players, items, and projectiles
        if (EntityFilters.shouldSkipForBurning(entity)) {
            return;
        }

        // Check if entity spawns already on fire
        if (FireStateUtil.isOnFire(entity)) {
            trackBurningEntity(entity);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID entityId = event.getEntity().getUniqueId();
        burningEntities.remove(entityId);
        // Note: EntityLightListener handles removal from sourceManager
    }

    private void trackBurningEntity(Entity entity) {
        UUID entityId = entity.getUniqueId();
        EntityLightConfig entityConfig = config.getEntityConfig(entity.getType());
        int fireLight = entityConfig.fireLight();

        burningEntities.add(entityId);

        // Update light level (fire light is typically higher than base light)
        int currentLevel = api.getLightLevel(entity);
        if (fireLight > currentLevel) {
            api.updateLightLevel(entity, fireLight);
        }
    }

    /**
     * Periodic sweep to detect fire expiration.
     * Only checks tracked burning entities, not all entities in the world.
     * Called by the plugin's consolidated fire sweep task.
     */
    public void checkFireExpiration() {
        Iterator<UUID> it = burningEntities.iterator();
        while (it.hasNext()) {
            UUID entityId = it.next();
            Entity entity = Bukkit.getEntity(entityId);

            if (entity == null || !entity.isValid()) {
                it.remove();
                continue;
            }

            if (!FireStateUtil.isOnFire(entity)) {
                it.remove();

                // Check if this is an always-lit entity - downgrade to base light instead of removing
                EntityLightConfig entityConfig = config.getEntityConfig(entity.getType());
                int baseLight = entityConfig.baseLight();

                if (config.alwaysLitEntitiesEnabled && baseLight > 0) {
                    // Downgrade to base light
                    api.updateLightLevel(entity, baseLight);
                } else {
                    // Remove light source entirely
                    api.removeLightSource(entity);
                }
            }
        }
    }
}
