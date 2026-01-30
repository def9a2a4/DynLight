package anon.def9a2a4.dynlight;

import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all light-emitting entities.
 */
public class LightSourceManager implements DynLightAPI {

    // Entity UUID -> Light level (1-15) for API-registered sources
    private final Map<UUID, Integer> apiSources = new ConcurrentHashMap<>();

    // Entity UUID -> Light level for built-in detected sources (cleared each tick)
    private final Map<UUID, Integer> builtInSources = new ConcurrentHashMap<>();

    @Override
    public void addLightSource(Entity entity, int level) {
        if (level < 1 || level > 15) {
            throw new IllegalArgumentException("Light level must be between 1 and 15");
        }
        apiSources.put(entity.getUniqueId(), level);
    }

    @Override
    public void removeLightSource(Entity entity) {
        apiSources.remove(entity.getUniqueId());
    }

    @Override
    public boolean isLightSource(Entity entity) {
        UUID uuid = entity.getUniqueId();
        return apiSources.containsKey(uuid) || builtInSources.containsKey(uuid);
    }

    @Override
    public int getLightLevel(Entity entity) {
        UUID uuid = entity.getUniqueId();
        // API sources take priority over built-in detection
        Integer apiLevel = apiSources.get(uuid);
        if (apiLevel != null) {
            return apiLevel;
        }
        return builtInSources.getOrDefault(uuid, 0);
    }

    /**
     * Set a built-in detected light source (called by BuiltInDetector).
     */
    void setBuiltInSource(UUID entityId, int level) {
        builtInSources.put(entityId, level);
    }

    /**
     * Clear all built-in sources (called at start of each detection cycle).
     */
    void clearBuiltInSources() {
        builtInSources.clear();
    }

    /**
     * Get all active light sources for rendering.
     * API sources override built-in sources for the same entity.
     */
    public Map<UUID, Integer> getAllLightSources() {
        Map<UUID, Integer> combined = new HashMap<>(builtInSources);
        combined.putAll(apiSources);
        return combined;
    }
}
