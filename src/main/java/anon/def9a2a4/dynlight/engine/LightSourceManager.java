package anon.def9a2a4.dynlight.engine;

import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.api.EntityLightDetector;
import anon.def9a2a4.dynlight.api.LightSourceInfo;
import anon.def9a2a4.dynlight.engine.data.LightSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry of all light-emitting entities.
 * All light sources (API, event-driven, players) are registered here persistently.
 * Partitioned by world for efficient per-world lookups.
 */
public class LightSourceManager implements DynLightAPI {

    // World name -> (Entity UUID -> Light info) for all persistent light sources
    private final Map<String, Map<UUID, LightSourceInfo>> lightSourcesByWorld = new ConcurrentHashMap<>();

    // Registered detectors for evaluating entities
    private final List<EntityLightDetector> detectors = new CopyOnWriteArrayList<>();

    @Override
    public void addLightSource(Entity entity, int level) {
        addLightSource(entity, LightSourceInfo.of(level));
    }

    @Override
    public void addLightSource(Entity entity, LightSourceInfo info) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        if (info == null) {
            throw new IllegalArgumentException("Light info cannot be null");
        }
        if (info.lightLevel() < 1 || info.lightLevel() > 15) {
            throw new IllegalArgumentException("Light level must be between 1 and 15");
        }
        String worldName = entity.getWorld().getName();
        lightSourcesByWorld
                .computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .put(entity.getUniqueId(), info);
    }

    @Override
    public void removeLightSource(Entity entity) {
        if (entity == null) return;
        String worldName = entity.getWorld().getName();
        Map<UUID, LightSourceInfo> worldSources = lightSourcesByWorld.get(worldName);
        if (worldSources != null) {
            worldSources.remove(entity.getUniqueId());
        }
    }

    @Override
    public boolean isLightSource(Entity entity) {
        if (entity == null) return false;
        String worldName = entity.getWorld().getName();
        Map<UUID, LightSourceInfo> worldSources = lightSourcesByWorld.get(worldName);
        return worldSources != null && worldSources.containsKey(entity.getUniqueId());
    }

    @Override
    public int getLightLevel(Entity entity) {
        LightSourceInfo info = getLightSourceInfo(entity);
        return info != null ? info.lightLevel() : 0;
    }

    @Override
    public LightSourceInfo getLightSourceInfo(Entity entity) {
        if (entity == null) return null;
        String worldName = entity.getWorld().getName();
        Map<UUID, LightSourceInfo> worldSources = lightSourcesByWorld.get(worldName);
        if (worldSources == null) return null;
        return worldSources.get(entity.getUniqueId());
    }

    @Override
    public void updateLightLevel(Entity entity, int level) {
        if (entity == null || level < 1 || level > 15) {
            return;
        }
        String worldName = entity.getWorld().getName();
        lightSourcesByWorld
                .computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .put(entity.getUniqueId(), LightSourceInfo.of(level));
    }

    @Override
    public void addLightSources(Collection<Entity> entities, int level) {
        if (entities == null || entities.isEmpty()) return;
        if (level < 1 || level > 15) {
            throw new IllegalArgumentException("Light level must be between 1 and 15");
        }
        LightSourceInfo info = LightSourceInfo.of(level);
        for (Entity entity : entities) {
            if (entity != null) {
                String worldName = entity.getWorld().getName();
                lightSourcesByWorld
                        .computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                        .put(entity.getUniqueId(), info);
            }
        }
    }

    @Override
    public void addLightSources(Map<Entity, Integer> entityLevels) {
        if (entityLevels == null || entityLevels.isEmpty()) return;
        for (Map.Entry<Entity, Integer> entry : entityLevels.entrySet()) {
            Entity entity = entry.getKey();
            Integer level = entry.getValue();
            if (entity != null && level != null && level >= 1 && level <= 15) {
                String worldName = entity.getWorld().getName();
                lightSourcesByWorld
                        .computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                        .put(entity.getUniqueId(), LightSourceInfo.of(level));
            }
        }
    }

    @Override
    public void removeLightSources(Collection<Entity> entities) {
        if (entities == null || entities.isEmpty()) return;
        for (Entity entity : entities) {
            if (entity != null) {
                String worldName = entity.getWorld().getName();
                Map<UUID, LightSourceInfo> worldSources = lightSourcesByWorld.get(worldName);
                if (worldSources != null) {
                    worldSources.remove(entity.getUniqueId());
                }
            }
        }
    }

    /**
     * Get all active light sources for rendering, organized by world.
     * Returns a snapshot copy preserving world partitioning.
     *
     * @return Map of world name to (entity UUID to light info)
     */
    public Map<String, Map<UUID, LightSourceInfo>> getAllLightSources() {
        Map<String, Map<UUID, LightSourceInfo>> result = new HashMap<>(lightSourcesByWorld.size());
        for (Map.Entry<String, Map<UUID, LightSourceInfo>> entry : lightSourcesByWorld.entrySet()) {
            result.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return result;
    }

    /**
     * Get total count of light sources across all worlds.
     * More efficient than getAllLightSources().size() for stats.
     *
     * @return Total number of registered light sources
     */
    public int getTotalLightSourceCount() {
        int count = 0;
        for (Map<UUID, LightSourceInfo> worldSources : lightSourcesByWorld.values()) {
            count += worldSources.size();
        }
        return count;
    }

    /**
     * Get light source counts per world.
     *
     * @return Map of world name to light source count
     */
    public Map<String, Integer> getLightSourceCountsByWorld() {
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, Map<UUID, LightSourceInfo>> entry : lightSourcesByWorld.entrySet()) {
            int size = entry.getValue().size();
            if (size > 0) {
                counts.put(entry.getKey(), size);
            }
        }
        return counts;
    }

    /**
     * Capture snapshots of all registered light sources.
     * Called on main thread, returns immutable data for async processing.
     *
     * @return List of light source snapshots
     */
    public List<LightSnapshot> getApiSnapshots() {
        List<LightSnapshot> snapshots = new ArrayList<>();

        // Snapshot the world keys first to avoid concurrent modification
        Set<String> worlds = new HashSet<>(lightSourcesByWorld.keySet());

        for (String expectedWorld : worlds) {
            Map<UUID, LightSourceInfo> worldSources = lightSourcesByWorld.get(expectedWorld);
            if (worldSources == null) {
                continue;
            }

            // Create defensive copy to avoid CME during iteration
            Map<UUID, LightSourceInfo> sourcesCopy = new HashMap<>(worldSources);

            for (Map.Entry<UUID, LightSourceInfo> entry : sourcesCopy.entrySet()) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity == null || !entity.isValid()) {
                    // Remove stale entry from original map
                    worldSources.remove(entry.getKey());
                    continue;
                }

                World world = entity.getWorld();
                if (world == null) {
                    continue;
                }

                String actualWorld = world.getName();
                if (!actualWorld.equals(expectedWorld)) {
                    // Entity moved to a different world - migrate the entry
                    worldSources.remove(entry.getKey());
                    lightSourcesByWorld
                            .computeIfAbsent(actualWorld, k -> new ConcurrentHashMap<>())
                            .put(entity.getUniqueId(), entry.getValue());
                }

                LightSourceInfo info = entry.getValue();
                snapshots.add(new LightSnapshot(
                        entity.getUniqueId(),
                        entity.getType(),
                        actualWorld,
                        entity.getX(), entity.getY(), entity.getZ(),
                        info.lightLevel(),
                        info.horizontalRadius(),
                        info.height()
                ));
            }
        }

        return snapshots;
    }

    /**
     * Check if an entity has a registered light source.
     * Searches all worlds since we only have UUID.
     */
    public boolean hasLightSource(UUID entityId) {
        for (Map<UUID, LightSourceInfo> worldSources : lightSourcesByWorld.values()) {
            if (worldSources.containsKey(entityId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove stale entries for entities that no longer exist.
     * Called periodically independent of render cycle to prevent memory leaks.
     *
     * @return Number of stale entries removed
     */
    public int cleanup() {
        int removed = 0;
        for (Map<UUID, LightSourceInfo> worldSources : lightSourcesByWorld.values()) {
            Iterator<UUID> it = worldSources.keySet().iterator();
            while (it.hasNext()) {
                Entity entity = Bukkit.getEntity(it.next());
                if (entity == null || !entity.isValid()) {
                    it.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    // --- Detector API ---

    @Override
    public void registerDetector(EntityLightDetector detector) {
        if (detector != null && !detectors.contains(detector)) {
            detectors.add(detector);
        }
    }

    @Override
    public void unregisterDetector(EntityLightDetector detector) {
        detectors.remove(detector);
    }

    @Override
    public void scanEntities(Collection<Entity> entities) {
        if (entities == null || entities.isEmpty()) return;
        for (Entity entity : entities) {
            if (entity == null || !entity.isValid()) continue;
            // Skip if already registered
            if (isLightSource(entity)) continue;

            LightSourceInfo info = detectLight(entity);
            if (info != null) {
                addLightSource(entity, info);
            }
        }
    }

    /**
     * Run all registered detectors on an entity.
     * Returns the best result (highest light level) or null if no detector matched.
     */
    public LightSourceInfo detectLight(Entity entity) {
        if (entity == null) return null;

        LightSourceInfo best = null;
        for (EntityLightDetector detector : detectors) {
            LightSourceInfo info = detector.detect(entity);
            if (info != null) {
                if (best == null || info.lightLevel() > best.lightLevel()) {
                    best = info;
                }
            }
        }
        return best;
    }
}
