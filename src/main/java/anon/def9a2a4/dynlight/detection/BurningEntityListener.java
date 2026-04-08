package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.EntityLightConfig;
import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.api.EntityLightDetector;
import anon.def9a2a4.dynlight.api.LightSourceInfo;
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
 * Also provides a detector for chunk-load scanning.
 */
public class BurningEntityListener implements Listener, EntityLightDetector {

    private final DynLightConfig config;
    private final DynLightAPI api;
    // Store Entity references directly to avoid O(n) Bukkit.getEntity() lookups
    private final Map<UUID, Entity> burningEntities = new ConcurrentHashMap<>();

    public BurningEntityListener(DynLightConfig config, DynLightAPI api) {
        this.config = config;
        this.api = api;
        // Register this listener as a detector for chunk-load scanning
        api.registerDetector(this);
    }

    @Override
    public LightSourceInfo detect(Entity entity) {
        if (!config.burningEntitiesEnabled) {
            return null;
        }

        // Skip players, items, and projectiles
        if (EntityFilters.shouldSkipForBurning(entity)) {
            return null;
        }

        if (!FireStateUtil.isOnFire(entity)) {
            return null;
        }

        EntityLightConfig entityConfig = config.getEntityConfig(entity.getType());
        int fireLight = entityConfig.fireLight();
        if (fireLight <= 0) {
            return null;
        }

        // Track for fire expiration handling
        if (!burningEntities.containsKey(entity.getUniqueId())) {
            burningEntities.put(entity.getUniqueId(), entity);
        }

        return LightSourceInfo.of(fireLight, entityConfig.horizontalRadius(), entityConfig.height());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (!config.burningEntitiesEnabled) return;

        Entity entity = event.getEntity();
        if (EntityFilters.shouldSkipForBurning(entity)) return;

        // Use event duration instead of current fire ticks — the event fires
        // BEFORE Bukkit applies fire ticks, so isOnFire() would return false
        // for one-time ignition sources like flaming projectile hits.
        if (event.getDuration() <= 0) return;

        EntityLightConfig entityConfig = config.getEntityConfig(entity.getType());
        int fireLight = entityConfig.fireLight();
        if (fireLight <= 0) return;

        burningEntities.put(entity.getUniqueId(), entity);

        int currentLevel = api.getLightLevel(entity);
        if (fireLight > currentLevel) {
            api.addLightSource(entity, LightSourceInfo.of(fireLight,
                    entityConfig.horizontalRadius(), entityConfig.height()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        // Use detector for immediate registration on spawn (if already on fire)
        LightSourceInfo info = detect(event.getEntity());
        if (info != null) {
            api.addLightSource(event.getEntity(), info);
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
