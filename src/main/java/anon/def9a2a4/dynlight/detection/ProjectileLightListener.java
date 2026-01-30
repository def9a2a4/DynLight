package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.EntityLightConfig;
import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.detection.util.FireStateUtil;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-driven listener for projectile light sources (flaming arrows, spectral arrows).
 */
public class ProjectileLightListener implements Listener {

    private final DynLightConfig config;
    private final DynLightAPI api;
    // Store Entity references directly to avoid O(n) Bukkit.getEntity() lookups
    private final Map<UUID, Entity> burningProjectiles = new ConcurrentHashMap<>();
    // Watch list for projectiles that might catch fire mid-flight (e.g., passing through lava)
    private final Map<UUID, Projectile> watchedProjectiles = new ConcurrentHashMap<>();

    public ProjectileLightListener(DynLightConfig config, DynLightAPI api) {
        this.config = config;
        this.api = api;
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();

        // Handle spectral arrows (always-lit)
        if (config.alwaysLitEntitiesEnabled && projectile instanceof SpectralArrow) {
            EntityLightConfig arrowConfig = config.getEntityConfig(projectile.getType());
            int baseLight = arrowConfig.baseLight();
            if (baseLight > 0) {
                api.addLightSource(projectile, baseLight);
            }
        }

        // Handle arrows - track if on fire, otherwise watch for mid-flight ignition
        if (config.flamingArrowsEnabled && projectile instanceof Arrow arrow) {
            if (FireStateUtil.isOnFire(arrow)) {
                trackBurningProjectile(arrow);
            } else {
                watchedProjectiles.put(arrow.getUniqueId(), arrow);
            }
        }

        // Handle snowballs - track if on fire, otherwise watch for mid-flight ignition
        if (config.flamingArrowsEnabled && projectile instanceof Snowball snowball) {
            if (FireStateUtil.isOnFire(snowball)) {
                trackBurningProjectile(snowball);
            } else {
                watchedProjectiles.put(snowball.getUniqueId(), snowball);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (!config.flamingArrowsEnabled) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity instanceof Arrow arrow) {
            trackBurningProjectile(arrow);
        } else if (entity instanceof Snowball snowball) {
            trackBurningProjectile(snowball);
        }
    }

    @EventHandler
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Projectile) {
            burningProjectiles.remove(entity.getUniqueId());
            watchedProjectiles.remove(entity.getUniqueId());
            api.removeLightSource(entity);
        }
    }

    private void trackBurningProjectile(Projectile projectile) {
        // Remove from watch list if present (prevents double-tracking)
        watchedProjectiles.remove(projectile.getUniqueId());

        // Skip if already tracked as burning
        if (burningProjectiles.containsKey(projectile.getUniqueId())) {
            return;
        }

        EntityLightConfig projectileConfig = config.getEntityConfig(projectile.getType());
        int fireLight = projectileConfig.fireLight();

        burningProjectiles.put(projectile.getUniqueId(), projectile);
        api.addLightSource(projectile, fireLight);
    }

    /**
     * Periodic sweep to detect fire expiration on projectiles.
     * Called by the plugin's consolidated fire sweep task.
     */
    public void checkFireExpiration() {
        Iterator<Map.Entry<UUID, Entity>> it = burningProjectiles.entrySet().iterator();
        while (it.hasNext()) {
            Entity entity = it.next().getValue();

            if (!entity.isValid()) {
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
                        api.updateLightLevel(entity, baseLight);
                    } else {
                        api.removeLightSource(entity);
                    }
                } else {
                    api.removeLightSource(entity);
                }
            }
        }
    }

    /**
     * Periodic sweep to detect mid-flight fire ignition on watched projectiles.
     * Called by the plugin's consolidated fire sweep task.
     */
    public void checkFireIgnition() {
        Iterator<Map.Entry<UUID, Projectile>> it = watchedProjectiles.entrySet().iterator();
        while (it.hasNext()) {
            Projectile projectile = it.next().getValue();

            if (!projectile.isValid()) {
                it.remove();
                continue;
            }

            if (FireStateUtil.isOnFire(projectile)) {
                it.remove();
                trackBurningProjectile(projectile);
            }
        }
    }
}
