package anon.def9a2a4.dynlight.engine.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Computed light update for a single player.
 * Contains the delta: entities to add/update and entities to remove.
 * Keyed by entity UUID to preserve per-entity identity when multiple
 * entities share the same block position.
 * Collections are made immutable to prevent external modification.
 *
 * @param toAdd    Entities needing light placement (entityId -> ideal position and level)
 * @param toRemove Entity IDs to remove (no longer visible or emitting)
 */
public record PlayerLightUpdate(
        Map<UUID, EntityLightEntry> toAdd,
        Set<UUID> toRemove
) {
    public PlayerLightUpdate {
        // Wrap in unmodifiable views to prevent external modification
        toAdd = Collections.unmodifiableMap(new HashMap<>(toAdd));
        toRemove = Collections.unmodifiableSet(new HashSet<>(toRemove));
    }
}
