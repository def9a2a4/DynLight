package anon.def9a2a4.dynlight;

import anon.def9a2a4.dynlight.util.FireStateUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-driven listener for projectile light sources (flaming arrows, spectral arrows).
 */
public class ProjectileLightListener implements Listener {

    private final DynLightConfig config;
    private final LightSourceManager sourceManager;
    private final Set<UUID> burningProjectiles = ConcurrentHashMap.newKeySet();

    public ProjectileLightListener(DynLightConfig config, LightSourceManager sourceManager) {
        this.config = config;
        this.sourceManager = sourceManager;
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();

        // Handle spectral arrows (always-lit)
        if (config.alwaysLitEntitiesEnabled && projectile instanceof SpectralArrow) {
            EntityLightConfig arrowConfig = config.getEntityConfig(projectile.getType());
            int baseLight = arrowConfig.baseLight();
            if (baseLight > 0) {
                sourceManager.addLightSource(projectile, baseLight);
            }
        }

        // Handle flaming arrows
        if (config.flamingArrowsEnabled && projectile instanceof Arrow arrow) {
            if (FireStateUtil.isOnFire(arrow)) {
                trackBurningProjectile(arrow);
            }
        }
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        if (!config.flamingArrowsEnabled) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity instanceof Arrow arrow) {
            trackBurningProjectile(arrow);
        }
    }

    @EventHandler
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Projectile) {
            burningProjectiles.remove(entity.getUniqueId());
            sourceManager.removeLightSource(entity);
        }
    }

    private void trackBurningProjectile(Arrow arrow) {
        UUID arrowId = arrow.getUniqueId();
        EntityLightConfig arrowConfig = config.getEntityConfig(arrow.getType());
        int fireLight = arrowConfig.fireLight();

        burningProjectiles.add(arrowId);
        sourceManager.addLightSource(arrow, fireLight);
    }

    /**
     * Periodic sweep to detect fire expiration on projectiles.
     * Called by the plugin's consolidated fire sweep task.
     */
    public void checkFireExpiration() {
        Iterator<UUID> it = burningProjectiles.iterator();
        while (it.hasNext()) {
            UUID arrowId = it.next();
            Entity entity = Bukkit.getEntity(arrowId);

            if (entity == null || !entity.isValid()) {
                it.remove();
                continue;
            }

            if (!FireStateUtil.isOnFire(entity)) {
                it.remove();

                // Check if this is a spectral arrow (always-lit)
                if (config.alwaysLitEntitiesEnabled && entity instanceof SpectralArrow) {
                    EntityLightConfig arrowConfig = config.getEntityConfig(entity.getType());
                    int baseLight = arrowConfig.baseLight();
                    if (baseLight > 0) {
                        sourceManager.updateLightLevel(entity, baseLight);
                    } else {
                        sourceManager.removeLightSource(entity);
                    }
                } else {
                    sourceManager.removeLightSource(entity);
                }
            }
        }
    }
}
