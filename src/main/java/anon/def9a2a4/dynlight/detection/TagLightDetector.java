package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.api.EntityLightDetector;
import anon.def9a2a4.dynlight.api.LightSourceInfo;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

/**
 * Detects entities tagged with scoreboard tags matching {@code dynlight:<level>}.
 * Supports an extended format: {@code dynlight:<level>:<radius>:<height>}.
 * <p>
 * This enables cross-plugin integration (e.g., BlockShips tagging display entities)
 * without any compile-time dependency between plugins.
 */
public class TagLightDetector implements Listener, EntityLightDetector {

    private static final String TAG_PREFIX = "dynlight:";

    private final DynLightConfig config;
    private final DynLightAPI api;

    public TagLightDetector(DynLightConfig config, DynLightAPI api) {
        this.config = config;
        this.api = api;
        api.registerDetector(this);
    }

    @Override
    public LightSourceInfo detect(Entity entity) {
        if (!config.taggedEntitiesEnabled) {
            return null;
        }
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                LightSourceInfo info = parseTag(tag);
                if (info != null) {
                    return info;
                }
            }
        }
        return null;
    }

    private LightSourceInfo parseTag(String tag) {
        String[] parts = tag.substring(TAG_PREFIX.length()).split(":");
        try {
            int level = Integer.parseInt(parts[0]);
            if (level < 1 || level > 15) return null;

            if (parts.length >= 3) {
                int radius = Math.clamp(Integer.parseInt(parts[1]), 0, 5);
                int height = Math.clamp(Integer.parseInt(parts[2]), 1, 10);
                return LightSourceInfo.of(level, radius, height);
            }
            return LightSourceInfo.of(level);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        LightSourceInfo info = detect(event.getEntity());
        if (info != null) {
            api.addLightSource(event.getEntity(), info);
        }
    }

    @EventHandler
    public void onEntityRemove(EntityRemoveEvent event) {
        if (hasLightTag(event.getEntity())) {
            api.removeLightSource(event.getEntity());
        }
    }

    private boolean hasLightTag(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith(TAG_PREFIX)) return true;
        }
        return false;
    }
}
