package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.api.DynLightAPI;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized listener for chunk load and player join events.
 * Scans all entities using registered detectors to ensure light sources are tracked.
 */
public class ChunkLoadListener implements Listener {

    private final DynLightAPI api;
    private final Plugin plugin;

    public ChunkLoadListener(DynLightAPI api, Plugin plugin) {
        this.api = api;
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Scan all entities in the chunk using registered detectors
        api.scanEntities(List.of(event.getChunk().getEntities()));
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        // Paper event: fires when entities are actually loaded into chunks.
        // Delay by 1 tick to ensure entities are fully initialized (isValid() returns true).
        List<Entity> entities = new ArrayList<>(event.getEntities());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            api.scanEntities(entities);
        }, 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Scan all loaded chunks in the player's world for untracked light sources.
        // This handles spawn chunks and other already-loaded chunks that don't
        // trigger ChunkLoadEvent when a player joins.
        // Delay by 1 tick to ensure entities are fully initialized.
        World world = event.getPlayer().getWorld();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Chunk chunk : world.getLoadedChunks()) {
                api.scanEntities(List.of(chunk.getEntities()));
            }
        }, 1L);
    }
}
