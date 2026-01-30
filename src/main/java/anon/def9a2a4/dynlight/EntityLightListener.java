package anon.def9a2a4.dynlight;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Event-driven listener for always-lit entities (Blaze, Glow Squid, etc.).
 * Registers light sources when entities spawn and removes them on death.
 */
public class EntityLightListener implements Listener {

    private final DynLightConfig config;
    private final LightSourceManager sourceManager;

    public EntityLightListener(DynLightConfig config, LightSourceManager sourceManager) {
        this.config = config;
        this.sourceManager = sourceManager;
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!config.alwaysLitEntitiesEnabled) {
            return;
        }

        Entity entity = event.getEntity();

        // Skip players (handled by PlayerLightDetector), items (handled by ItemLightListener),
        // and projectiles (handled by ProjectileLightListener)
        if (entity instanceof Player || entity instanceof Item || entity instanceof Projectile) {
            return;
        }

        EntityLightConfig entityConfig = config.getEntityConfig(entity.getType());
        int baseLight = entityConfig.baseLight();

        if (baseLight > 0) {
            sourceManager.addLightSource(entity, baseLight);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        sourceManager.removeLightSource(event.getEntity());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!config.alwaysLitEntitiesEnabled) {
            return;
        }

        // Scan loaded chunk for always-lit entities
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Player || entity instanceof Item || entity instanceof Projectile) {
                continue;
            }

            EntityLightConfig entityConfig = config.getEntityConfig(entity.getType());
            int baseLight = entityConfig.baseLight();

            if (baseLight > 0 && !sourceManager.isLightSource(entity)) {
                sourceManager.addLightSource(entity, baseLight);
            }
        }
    }
}
