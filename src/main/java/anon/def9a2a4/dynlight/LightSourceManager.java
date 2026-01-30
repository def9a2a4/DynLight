package anon.def9a2a4.dynlight;

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
 */
public class LightSourceManager implements DynLightAPI {

    // Entity UUID -> Light level (1-15) for all persistent light sources
    private final Map<UUID, Integer> lightSources = new ConcurrentHashMap<>();

    @Override
    public void addLightSource(Entity entity, int level) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        if (level < 1 || level > 15) {
            throw new IllegalArgumentException("Light level must be between 1 and 15");
        }
        lightSources.put(entity.getUniqueId(), level);
    }

    @Override
    public void removeLightSource(Entity entity) {
        if (entity == null) return;
        lightSources.remove(entity.getUniqueId());
    }

    @Override
    public boolean isLightSource(Entity entity) {
        if (entity == null) return false;
        return lightSources.containsKey(entity.getUniqueId());
    }

    @Override
    public int getLightLevel(Entity entity) {
        if (entity == null) return 0;
        return lightSources.getOrDefault(entity.getUniqueId(), 0);
    }

    @Override
    public void addLightSources(Collection<Entity> entities, int level) {
        if (entities == null || entities.isEmpty()) return;
        if (level < 1 || level > 15) {
            throw new IllegalArgumentException("Light level must be between 1 and 15");
        }
        for (Entity entity : entities) {
            if (entity != null) {
                lightSources.put(entity.getUniqueId(), level);
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
                lightSources.put(entity.getUniqueId(), level);
            }
        }
    }

    @Override
    public void removeLightSources(Collection<Entity> entities) {
        if (entities == null || entities.isEmpty()) return;
        for (Entity entity : entities) {
            if (entity != null) {
                lightSources.remove(entity.getUniqueId());
            }
        }
    }

    /**
     * Update the light level for an existing light source.
     * Used when fire state changes (upgrade to fire light, downgrade to base light).
     *
     * @param entity The entity to update
     * @param level The new light level (1-15)
     */
    public void updateLightLevel(Entity entity, int level) {
        if (entity == null || level < 1 || level > 15) {
            return; // Invalid entity or light level, skip silently
        }
        lightSources.put(entity.getUniqueId(), level);
    }

    /**
     * Get all active light sources for rendering.
     * Returns a snapshot copy - modifications don't affect internal state.
     */
    public Map<UUID, Integer> getAllLightSources() {
        return new HashMap<>(lightSources);
    }

    /**
     * Capture snapshots of all registered light sources.
     * Called on main thread, returns immutable data for async processing.
     *
     * @return List of light source snapshots
     */
    public List<LightSnapshot> getApiSnapshots() {
        List<LightSnapshot> snapshots = new ArrayList<>();

        Iterator<Map.Entry<UUID, Integer>> it = lightSources.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid()) {
                it.remove(); // Clean up stale entry using iterator
                continue;
            }

            // Use direct accessors to avoid Location object allocation
            World world = entity.getWorld();
            if (world == null) {
                continue; // Entity in invalid state
            }
            snapshots.add(new LightSnapshot(
                    entity.getUniqueId(),
                    entity.getType(),
                    world.getName(),
                    entity.getX(), entity.getY(), entity.getZ(),
                    entry.getValue()
            ));
        }

        return snapshots;
    }

    /**
     * Check if an entity has a registered light source.
     */
    public boolean hasLightSource(UUID entityId) {
        return lightSources.containsKey(entityId);
    }
}
