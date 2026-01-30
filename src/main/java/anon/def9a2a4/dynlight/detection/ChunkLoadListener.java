package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.api.DynLightAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.List;

/**
 * Centralized listener for chunk load events.
 * Scans all entities in newly loaded chunks using registered detectors.
 */
public class ChunkLoadListener implements Listener {

    private final DynLightAPI api;

    public ChunkLoadListener(DynLightAPI api) {
        this.api = api;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Scan all entities in the chunk using registered detectors
        api.scanEntities(List.of(event.getChunk().getEntities()));
    }
}
