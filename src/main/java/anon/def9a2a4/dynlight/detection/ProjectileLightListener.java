package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.EntityLightConfig;
import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.api.EntityLightDetector;
import anon.def9a2a4.dynlight.api.LightSourceInfo;
import anon.def9a2a4.dynlight.detection.util.FireStateUtil;
import anon.def9a2a4.dynlight.engine.InvalidationTracker;
import anon.def9a2a4.dynlight.engine.LightRenderer;
import anon.def9a2a4.dynlight.engine.data.LightSnapshot;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
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
    private final LightRenderer renderer;
    private final InvalidationTracker invalidationTracker;
    // Store Entity references directly to avoid O(n) Bukkit.getEntity() lookups
    private final Map<UUID, Entity> burningProjectiles = new ConcurrentHashMap<>();
    // Watch list for projectiles that might catch fire mid-flight (e.g., passing through lava)
    private final Map<UUID, Projectile> watchedProjectiles = new ConcurrentHashMap<>();
    // Position history for trail rendering (most recent position first, using Deque for O(1) prepend)
    private final Map<UUID, Deque<Location>> positionHistory = new ConcurrentHashMap<>();

    public ProjectileLightListener(DynLightConfig config, DynLightAPI api, LightRenderer renderer,
                                   InvalidationTracker invalidationTracker) {
        this.config = config;
        this.api = api;
        this.renderer = renderer;
        this.invalidationTracker = invalidationTracker;
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

    /**
     * Create a deterministic synthetic UUID for a trail point.
     * Uses cheap bit manipulation instead of MD5 hashing.
     */
    private static UUID syntheticTrailUUID(UUID entityId, int trailIndex) {
        // Add 1 to ensure trailIndex=0 doesn't produce the same UUID as entityId
        // (XOR with 0 would return the original UUID, causing collision with main entity light)
        int offset = trailIndex + 1;
        long msb = entityId.getMostSignificantBits() ^ ((long) offset << 48);
        long lsb = entityId.getLeastSignificantBits() ^ ((long) offset);
        return new UUID(msb, lsb);
    }

    /**
     * Check if a projectile has landed (stuck in block or on ground).
     * Landed projectiles should not generate trail lights to prevent flickering.
     */
    private boolean isProjectileLanded(Entity entity) {
        if (entity instanceof Arrow arrow) {
            return arrow.isInBlock();
        }
        if (entity instanceof Trident trident) {
            return trident.isInBlock();
        }
        // Snowballs/fireballs disappear on impact, so if valid they're still flying
        return false;
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
            positionHistory.remove(entityId);
            api.removeLightSource(entity);
            // Mark as invalidated (prevents stale async results from re-adding)
            // and immediately clear trail lights from all players
            invalidationTracker.invalidateParent(entityId);
            renderer.clearChildLights(entityId);
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
            UUID entityId = entry.getKey();
            Entity entity = entry.getValue();

            if (!entity.isValid()) {
                it.remove();
                positionHistory.remove(entityId);
                // Mark as invalidated and clear trail lights for invalid entity
                invalidationTracker.invalidateParent(entityId);
                renderer.clearChildLights(entityId);
                continue;
            }

            if (!FireStateUtil.isOnFire(entity)) {
                it.remove();
                positionHistory.remove(entityId);
                // Mark as invalidated and clear trail lights when fire expires
                invalidationTracker.invalidateParent(entityId);
                renderer.clearChildLights(entityId);

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
     * Clear all position history. Called when trails are disabled via config reload.
     */
    public void clearPositionHistory() {
        positionHistory.clear();
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

    /**
     * Update position history for all burning projectiles.
     * Called each render cycle before generating trail snapshots.
     */
    public void updatePositionHistory() {
        // Clean up stale entries: entities not in burningProjectiles shouldn't have history
        // This handles race conditions where entity removal and snapshot capture overlap
        positionHistory.keySet().removeIf(uuid -> !burningProjectiles.containsKey(uuid));

        int trailLength = config.projectileTrailLength;
        if (trailLength <= 0) {
            positionHistory.clear(); // No trails needed, clear all history
            return;
        }

        double minSpacingSquared = config.projectileTrailSpacing * config.projectileTrailSpacing;

        for (Entity entity : burningProjectiles.values()) {
            if (!entity.isValid()) {
                continue;
            }

            // Skip landed projectiles - clear their trail to prevent flickering
            if (isProjectileLanded(entity)) {
                positionHistory.remove(entity.getUniqueId());
                continue;
            }

            Deque<Location> history = positionHistory.computeIfAbsent(
                    entity.getUniqueId(), k -> new ArrayDeque<>());

            Location current = entity.getLocation();

            // Only add if moved enough from last recorded position
            if (history.isEmpty() || history.peekFirst().distanceSquared(current) >= minSpacingSquared) {
                history.addFirst(current.clone());

                // Trim to max trail length
                while (history.size() > trailLength) {
                    history.removeLast();
                }
            }
        }
    }

    /**
     * Generate light snapshots for trail positions behind burning projectiles.
     * Each trail position gets a synthetic UUID derived from the projectile's UUID,
     * and includes the projectile's UUID as parentId for invalidation tracking.
     *
     * @return List of trail light snapshots
     */
    public List<LightSnapshot> getTrailSnapshots() {
        int trailLength = config.projectileTrailLength;
        if (trailLength <= 0) {
            return List.of(); // Trails disabled
        }

        List<LightSnapshot> trails = new ArrayList<>();

        for (Map.Entry<UUID, Entity> entry : burningProjectiles.entrySet()) {
            Entity entity = entry.getValue();
            if (!entity.isValid()) {
                continue;
            }

            // Skip landed projectiles - only flying projectiles show trails
            if (isProjectileLanded(entity)) {
                continue;
            }

            Deque<Location> history = positionHistory.get(entity.getUniqueId());
            if (history == null || history.isEmpty()) {
                continue;
            }

            EntityLightConfig entityConfig = config.getEntityConfig(entity.getType());
            int lightLevel = entityConfig.fireLight();
            String worldName = entity.getWorld().getName();

            // Generate synthetic snapshots for each trail position
            // Each trail light has the projectile's UUID as parentId for invalidation tracking
            UUID parentId = entity.getUniqueId();
            int i = 0;
            for (Location pos : history) {
                trails.add(new LightSnapshot(
                        syntheticTrailUUID(parentId, i++), entity.getType(), worldName,
                        pos.getX(), pos.getY(), pos.getZ(), lightLevel,
                        parentId));  // Link to parent for invalidation
            }
        }

        return trails;
    }
}
