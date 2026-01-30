package anon.def9a2a4.dynlight.engine;

import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.engine.data.LightSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all light-emitting entities.
 * All light sources (API, event-driven, players) are registered here persistently.
 * Partitioned by world for efficient per-world lookups.
 */
public class LightSourceManager implements DynLightAPI {

    // World name -> (Entity UUID -> Light level) for all persistent light sources
    private final Map<String, Map<UUID, Integer>> lightSourcesByWorld = new ConcurrentHashMap<>();

    @Override
    public void addLightSource(Entity entity, int level) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        if (level < 1 || level > 15) {
            throw new IllegalArgumentException("Light level must be between 1 and 15");
        }
        String worldName = entity.getWorld().getName();
        lightSourcesByWorld
                .computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .put(entity.getUniqueId(), level);
    }

    @Override
    public void removeLightSource(Entity entity) {
        if (entity == null) return;
        String worldName = entity.getWorld().getName();
        Map<UUID, Integer> worldSources = lightSourcesByWorld.get(worldName);
        if (worldSources != null) {
            worldSources.remove(entity.getUniqueId());
        }
    }

    @Override
    public boolean isLightSource(Entity entity) {
        if (entity == null) return false;
        String worldName = entity.getWorld().getName();
        Map<UUID, Integer> worldSources = lightSourcesByWorld.get(worldName);
        return worldSources != null && worldSources.containsKey(entity.getUniqueId());
    }

    @Override
    public int getLightLevel(Entity entity) {
        if (entity == null) return 0;
        String worldName = entity.getWorld().getName();
        Map<UUID, Integer> worldSources = lightSourcesByWorld.get(worldName);
        if (worldSources == null) return 0;
        return worldSources.getOrDefault(entity.getUniqueId(), 0);
    }

    @Override
    public void updateLightLevel(Entity entity, int level) {
        if (entity == null || level < 1 || level > 15) {
            return;
        }
        String worldName = entity.getWorld().getName();
        lightSourcesByWorld
                .computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .put(entity.getUniqueId(), level);
    }

    @Override
    public void addLightSources(Collection<Entity> entities, int level) {
        if (entities == null || entities.isEmpty()) return;
        if (level < 1 || level > 15) {
            throw new IllegalArgumentException("Light level must be between 1 and 15");
        }
        for (Entity entity : entities) {
            if (entity != null) {
                String worldName = entity.getWorld().getName();
                lightSourcesByWorld
                        .computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                        .put(entity.getUniqueId(), level);
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
                        .put(entity.getUniqueId(), level);
            }
        }
    }

    @Override
    public void removeLightSources(Collection<Entity> entities) {
        if (entities == null || entities.isEmpty()) return;
        for (Entity entity : entities) {
            if (entity != null) {
                String worldName = entity.getWorld().getName();
                Map<UUID, Integer> worldSources = lightSourcesByWorld.get(worldName);
                if (worldSources != null) {
                    worldSources.remove(entity.getUniqueId());
                }
            }
        }
    }

    /**
     * Get all active light sources for rendering.
     * Returns a snapshot copy aggregated from all worlds.
     */
    public Map<UUID, Integer> getAllLightSources() {
        Map<UUID, Integer> allSources = new HashMap<>();
        for (Map<UUID, Integer> worldSources : lightSourcesByWorld.values()) {
            allSources.putAll(worldSources);
        }
        return allSources;
    }

    /**
     * Get total count of light sources across all worlds.
     * More efficient than getAllLightSources().size() for stats.
     *
     * @return Total number of registered light sources
     */
    public int getTotalLightSourceCount() {
        int count = 0;
        for (Map<UUID, Integer> worldSources : lightSourcesByWorld.values()) {
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
        for (Map.Entry<String, Map<UUID, Integer>> entry : lightSourcesByWorld.entrySet()) {
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

        for (Map.Entry<String, Map<UUID, Integer>> worldEntry : lightSourcesByWorld.entrySet()) {
            String expectedWorld = worldEntry.getKey();
            Map<UUID, Integer> worldSources = worldEntry.getValue();

            Iterator<Map.Entry<UUID, Integer>> it = worldSources.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity == null || !entity.isValid()) {
                    it.remove();
                    continue;
                }

                World world = entity.getWorld();
                if (world == null) {
                    continue;
                }

                String actualWorld = world.getName();
                if (!actualWorld.equals(expectedWorld)) {
                    // Entity moved to a different world - migrate the entry
                    it.remove();
                    lightSourcesByWorld
                            .computeIfAbsent(actualWorld, k -> new ConcurrentHashMap<>())
                            .put(entity.getUniqueId(), entry.getValue());
                }

                snapshots.add(new LightSnapshot(
                        entity.getUniqueId(),
                        entity.getType(),
                        actualWorld,
                        entity.getX(), entity.getY(), entity.getZ(),
                        entry.getValue()
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
        for (Map<UUID, Integer> worldSources : lightSourcesByWorld.values()) {
            if (worldSources.containsKey(entityId)) {
                return true;
            }
        }
        return false;
    }
}
