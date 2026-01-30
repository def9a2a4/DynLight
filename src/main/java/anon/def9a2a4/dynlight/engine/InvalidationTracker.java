package anon.def9a2a4.dynlight.engine;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks invalidated light source parents to prevent stale async results
 * from re-adding lights after entity removal.
 *
 * <p>The async render cycle captures snapshots on Tick N but applies results on Tick N+1.
 * If an entity is removed between capture and apply, the stale async result would
 * re-add the entity's lights. This tracker solves this by:</p>
 *
 * <ol>
 *   <li>Assigning a generation number to each snapshot capture</li>
 *   <li>Tracking which parent entities are invalidated per generation</li>
 *   <li>Filtering out invalidated sources at apply time</li>
 * </ol>
 *
 * <p>Thread-safe: invalidations happen on main thread, checks happen on async thread.</p>
 */
public class InvalidationTracker {

    private final AtomicLong generation = new AtomicLong(0);

    // Parent UUIDs invalidated per generation
    // Key: generation number, Value: set of invalidated parent UUIDs
    private final ConcurrentHashMap<Long, Set<UUID>> invalidationsByGeneration = new ConcurrentHashMap<>();

    // Maximum generations to keep (prevents memory leak from long-running servers)
    private static final int MAX_GENERATIONS = 5;

    /**
     * Start a new snapshot generation. Called at beginning of sync tick.
     *
     * @return The generation number for this snapshot
     */
    public long startGeneration() {
        long gen = generation.incrementAndGet();
        invalidationsByGeneration.put(gen, ConcurrentHashMap.newKeySet());

        // Cleanup old generations to prevent memory leak
        long oldestToKeep = gen - MAX_GENERATIONS;
        invalidationsByGeneration.keySet().removeIf(g -> g < oldestToKeep);

        return gen;
    }

    /**
     * Mark a parent entity as invalidated. Called on entity removal.
     * Marks in all tracked generations to catch any in-flight async operations.
     *
     * @param parentId The UUID of the parent entity being removed
     */
    public void invalidateParent(UUID parentId) {
        // Mark in all tracked generations (catches any in-flight async)
        for (Set<UUID> invalidated : invalidationsByGeneration.values()) {
            invalidated.add(parentId);
        }
    }

    /**
     * Check if a parent was invalidated after the given generation.
     * Called from apply phase to filter stale async results.
     *
     * @param parentId           The parent UUID to check
     * @param snapshotGeneration The generation when the snapshot was captured
     * @return true if the parent was invalidated and should be skipped
     */
    public boolean isInvalidated(UUID parentId, long snapshotGeneration) {
        Set<UUID> invalidated = invalidationsByGeneration.get(snapshotGeneration);
        return invalidated != null && invalidated.contains(parentId);
    }
}
