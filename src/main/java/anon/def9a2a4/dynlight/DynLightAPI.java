package anon.def9a2a4.dynlight;

import org.bukkit.entity.Entity;

/**
 * Public API for other plugins to register entities as light sources.
 */
public interface DynLightAPI {

    /**
     * Register an entity as a light source.
     *
     * @param entity The entity to emit light
     * @param level  Light level 1-15
     */
    void addLightSource(Entity entity, int level);

    /**
     * Remove an entity from light emission.
     *
     * @param entity The entity to stop emitting light
     */
    void removeLightSource(Entity entity);

    /**
     * Check if entity is registered as a light source.
     *
     * @param entity The entity to check
     * @return true if the entity emits light
     */
    boolean isLightSource(Entity entity);

    /**
     * Get light level for entity.
     *
     * @param entity The entity to check
     * @return Light level 1-15, or 0 if not a light source
     */
    int getLightLevel(Entity entity);
}
