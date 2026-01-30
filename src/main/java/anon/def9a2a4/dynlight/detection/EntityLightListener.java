package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.EntityLightConfig;
import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.api.EntityLightDetector;
import anon.def9a2a4.dynlight.api.LightSourceInfo;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

/**
 * Event-driven listener for always-lit entities (Blaze, Glow Squid, etc.).
 * Registers light sources when entities spawn and removes them on death.
 * Also provides a detector for chunk-load scanning.
 */
public class EntityLightListener implements Listener, EntityLightDetector {

    private final DynLightConfig config;
    private final DynLightAPI api;

    public EntityLightListener(DynLightConfig config, DynLightAPI api) {
        this.config = config;
        this.api = api;
        // Register this listener as a detector for chunk-load scanning
        api.registerDetector(this);
    }

    @Override
    public LightSourceInfo detect(Entity entity) {
        if (!config.alwaysLitEntitiesEnabled) {
            return null;
        }

        // Skip players, items, and projectiles (handled by other listeners/detectors)
        if (entity instanceof Player || entity instanceof Item || entity instanceof Projectile) {
            return null;
        }

        EntityLightConfig entityConfig = config.getEntityConfig(entity.getType());
        int baseLight = entityConfig.baseLight();

        if (baseLight > 0) {
            return LightSourceInfo.of(baseLight, entityConfig.horizontalRadius(), entityConfig.height());
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        // Use detector for immediate registration on spawn
        LightSourceInfo info = detect(event.getEntity());
        if (info != null) {
            api.addLightSource(event.getEntity(), info);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        api.removeLightSource(event.getEntity());
    }
}
