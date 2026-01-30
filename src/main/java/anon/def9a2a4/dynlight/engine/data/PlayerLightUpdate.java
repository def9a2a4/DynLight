package anon.def9a2a4.dynlight.engine.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Computed light update for a single player.
 * Contains the delta: blocks to add/update and blocks to remove.
 * Collections are made immutable to prevent external modification.
 *
 * @param toAdd    Blocks to add or update (position -> light level)
 * @param toRemove Blocks to remove (restore original block)
 */
public record PlayerLightUpdate(
        Map<BlockPos, Integer> toAdd,
        Set<BlockPos> toRemove
) {
    public PlayerLightUpdate {
        // Wrap in unmodifiable views to prevent external modification
        toAdd = Collections.unmodifiableMap(new HashMap<>(toAdd));
        toRemove = Collections.unmodifiableSet(new HashSet<>(toRemove));
    }
}
