package anon.def9a2a4.dynlight;

import java.util.Map;
import java.util.Set;

/**
 * Computed light update for a single player.
 * Contains the delta (blocks to add/remove) and the new full state.
 *
 * @param toAdd      Blocks to add or update (position -> light level)
 * @param toRemove   Blocks to remove (restore original block)
 * @param newState   The complete set of active light blocks after this update
 */
public record PlayerLightUpdate(
        Map<BlockPos, Integer> toAdd,
        Set<BlockPos> toRemove,
        Map<BlockPos, Integer> newState
) {
}
