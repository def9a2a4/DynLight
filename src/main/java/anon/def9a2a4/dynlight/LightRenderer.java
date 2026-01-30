package anon.def9a2a4.dynlight;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends fake light blocks to players and tracks sent blocks for cleanup.
 */
public class LightRenderer implements Listener {

    private final DynLightConfig config;
    private final double maxDistanceSquared;

    // Per-player tracking of sent light block locations
    private final Map<UUID, Set<Location>> sentBlocks = new ConcurrentHashMap<>();

    // Pre-created light block data for each level (1-15)
    private final Light[] lightLevels = new Light[16];

    public LightRenderer(DynLightConfig config) {
        this.config = config;
        int renderDistance = config.renderDistance;
        this.maxDistanceSquared = (double) renderDistance * renderDistance;

        // Pre-create BlockData for each light level
        for (int i = 1; i <= 15; i++) {
            Light light = (Light) Bukkit.createBlockData(Material.LIGHT);
            light.setLevel(i);
            lightLevels[i] = light;
        }
    }

    /**
     * Update light blocks for all online players.
     */
    public void updateAllPlayers(Map<UUID, Integer> lightSources) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player, lightSources);
        }
    }

    private void updatePlayer(Player player, Map<UUID, Integer> lightSources) {
        Set<Location> newBlocks = new HashSet<>();
        Set<Location> oldBlocks = sentBlocks.getOrDefault(player.getUniqueId(), Set.of());

        // Calculate new light positions
        for (Map.Entry<UUID, Integer> entry : lightSources.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.getWorld().equals(player.getWorld())) {
                continue;
            }

            Location entityLoc = entity.getLocation();
            if (entityLoc.distanceSquared(player.getLocation()) > maxDistanceSquared) {
                continue;
            }

            int lightLevel = entry.getValue();
            EntityLightConfig entityConfig = config.getEntityConfig(entity.getType());

            // Try to place light in the configured radius
            Location placedAt = tryPlaceLight(player, entityLoc, entityConfig, lightLevel, newBlocks);
            if (placedAt != null) {
                newBlocks.add(placedAt);
            }
        }

        // Clear old blocks that are no longer needed
        for (Location oldLoc : oldBlocks) {
            if (!newBlocks.contains(oldLoc)) {
                // Restore original block state
                player.sendBlockChange(oldLoc, oldLoc.getBlock().getBlockData());
            }
        }

        sentBlocks.put(player.getUniqueId(), newBlocks);
    }

    /**
     * Try to place a light block within the configured radius.
     * Returns the location where light was placed, or null if no suitable location found.
     */
    private Location tryPlaceLight(Player player, Location entityLoc, EntityLightConfig entityConfig,
                                   int lightLevel, Set<Location> alreadyUsed) {
        Location baseLoc = entityLoc.getBlock().getLocation();
        int baseX = baseLoc.getBlockX();
        int baseY = baseLoc.getBlockY();
        int baseZ = baseLoc.getBlockZ();

        // Try center column first (most likely to be valid)
        for (int dy = 0; dy < entityConfig.height(); dy++) {
            Location tryLoc = new Location(baseLoc.getWorld(), baseX, baseY + dy, baseZ);
            if (canPlaceLight(tryLoc) && !alreadyUsed.contains(tryLoc)) {
                player.sendBlockChange(tryLoc, lightLevels[lightLevel]);
                return tryLoc;
            }
        }

        // Try surrounding positions (cardinal directions only, no corners)
        int hr = entityConfig.horizontalRadius();
        for (int dx = -hr; dx <= hr; dx++) {
            for (int dz = -hr; dz <= hr; dz++) {
                if (dx == 0 && dz == 0) continue; // Already tried center
                if (dx != 0 && dz != 0) continue; // Skip corners

                for (int dy = 0; dy < entityConfig.height(); dy++) {
                    Location tryLoc = new Location(baseLoc.getWorld(), baseX + dx, baseY + dy, baseZ + dz);
                    if (canPlaceLight(tryLoc) && !alreadyUsed.contains(tryLoc)) {
                        player.sendBlockChange(tryLoc, lightLevels[lightLevel]);
                        return tryLoc;
                    }
                }
            }
        }

        return null; // No suitable location found
    }

    private boolean canPlaceLight(Location loc) {
        Material type = loc.getBlock().getType();
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.WATER;
    }

    /**
     * Clear all sent light blocks for all players (called on plugin disable).
     */
    public void clearAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearPlayer(player);
        }
        sentBlocks.clear();
    }

    private void clearPlayer(Player player) {
        Set<Location> blocks = sentBlocks.remove(player.getUniqueId());
        if (blocks != null) {
            for (Location loc : blocks) {
                player.sendBlockChange(loc, loc.getBlock().getBlockData());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sentBlocks.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        clearPlayer(event.getPlayer());
    }
}
