package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.EntityLightConfig;
import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.detection.util.EntityFilters;
import anon.def9a2a4.dynlight.detection.util.FireStateUtil;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-driven listener for burning entities.
 * Tracks entities when they catch fire and uses a periodic sweep to detect fire expiration.
 */
public class BurningEntityListener implements Listener {

    private final DynLightConfig config;
    private final DynLightAPI api;
    // Store Entity references directly to avoid O(n) Bukkit.getEntity() lookups
    private final Map<UUID, Entity> burningEntities = new ConcurrentHashMap<>();

    public BurningEntityListener(DynLightConfig config, DynLightAPI api) {
        this.config = config;
        this.api = api;
    }

    @EventHandler(ignoreCancelled = true)
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

    @EventHandler(ignoreCancelled = true)
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
        burningEntities.remove(event.getEntity().getUniqueId());
        // Note: EntityLightListener handles removal from sourceManager
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        // Clean up entities when their chunk unloads to prevent memory leak
        for (Entity entity : event.getChunk().getEntities()) {
            burningEntities.remove(entity.getUniqueId());
        }
    }

    private void trackBurningEntity(Entity entity) {
        EntityLightConfig entityConfig = config.getEntityConfig(entity.getType());
        int fireLight = entityConfig.fireLight();

        burningEntities.put(entity.getUniqueId(), entity);

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
        Iterator<Map.Entry<UUID, Entity>> it = burningEntities.entrySet().iterator();
        while (it.hasNext()) {
            Entity entity = it.next().getValue();

            if (!entity.isValid()) {
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
