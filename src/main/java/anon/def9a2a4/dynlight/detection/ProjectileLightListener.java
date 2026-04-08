package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.EntityLightConfig;
import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.api.EntityLightDetector;
import anon.def9a2a4.dynlight.api.LightSourceInfo;
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
 * Also provides a detector for chunk-load scanning.
 */
public class ProjectileLightListener implements Listener, EntityLightDetector {

    private final DynLightConfig config;
    private final DynLightAPI api;
    // Store Entity references directly to avoid O(n) Bukkit.getEntity() lookups
    private final Map<UUID, Entity> burningProjectiles = new ConcurrentHashMap<>();
    // Watch list for projectiles that might catch fire mid-flight (e.g., passing through lava)
    private final Map<UUID, Projectile> watchedProjectiles = new ConcurrentHashMap<>();

    public ProjectileLightListener(DynLightConfig config, DynLightAPI api) {
        this.config = config;
        this.api = api;
        // Register this listener as a detector for chunk-load scanning
        api.registerDetector(this);
    }

    @Override
    public LightSourceInfo detect(Entity entity) {
        if (!(entity instanceof Projectile)) {
            return null;
        }

        EntityLightConfig entityConfig = config.getEntityConfig(entity.getType());

        // Check for always-lit projectiles (spectral arrows)
        if (config.alwaysLitEntitiesEnabled) {
            int baseLight = entityConfig.baseLight();
            if (baseLight > 0) {
                return LightSourceInfo.of(baseLight, entityConfig.horizontalRadius(), entityConfig.height());
            }
        }

        // Check for flaming projectiles (arrows, snowballs)
        if (config.flamingArrowsEnabled && (entity instanceof Arrow || entity instanceof Snowball)) {
            if (FireStateUtil.isOnFire(entity)) {
                int fireLight = entityConfig.fireLight();
                if (fireLight > 0) {
                    // Also track as burning for fire expiration handling
                    if (!burningProjectiles.containsKey(entity.getUniqueId())) {
                        burningProjectiles.put(entity.getUniqueId(), entity);
                    }
                    return LightSourceInfo.of(fireLight, entityConfig.horizontalRadius(), entityConfig.height());
                }
            }
        }

        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();

        // Use detector for immediate registration on launch
        LightSourceInfo info = detect(projectile);
        if (info != null) {
            api.addLightSource(projectile, info);
        }

        // Add to watch list for mid-flight ignition (arrows and snowballs not currently on fire)
        if (config.flamingArrowsEnabled) {
            if (projectile instanceof Arrow arrow && !FireStateUtil.isOnFire(arrow)) {
                watchedProjectiles.put(arrow.getUniqueId(), arrow);
            } else if (projectile instanceof Snowball snowball && !FireStateUtil.isOnFire(snowball)) {
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
            UUID entityId = entity.getUniqueId();
            burningProjectiles.remove(entityId);
            watchedProjectiles.remove(entityId);
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
        api.addLightSource(projectile, LightSourceInfo.of(fireLight,
                projectileConfig.horizontalRadius(), projectileConfig.height()));
    }

    /**
     * Periodic sweep to detect fire expiration on projectiles.
     * Called by the plugin's consolidated fire sweep task.
     */
    public void checkFireExpiration() {
        Iterator<Map.Entry<UUID, Entity>> it = burningProjectiles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entity> entry = it.next();
            Entity entity = entry.getValue();

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
