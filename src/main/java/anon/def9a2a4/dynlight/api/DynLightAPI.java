package anon.def9a2a4.dynlight.api;

import org.bukkit.entity.Entity;

import java.util.Collection;
import java.util.Map;

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

    /**
     * Update the light level for an existing light source.
     * If the entity is not already a light source, this behaves like addLightSource.
     *
     * @param entity The entity to update
     * @param level  Light level 1-15
     */
    void updateLightLevel(Entity entity, int level);

    /**
     * Register multiple entities as light sources with the same light level.
     *
     * @param entities The entities to emit light
     * @param level    Light level 1-15
     */
    void addLightSources(Collection<Entity> entities, int level);

    /**
     * Register multiple entities as light sources with individual light levels.
     *
     * @param entityLevels Map of entity to light level (1-15)
     */
    void addLightSources(Map<Entity, Integer> entityLevels);

    /**
     * Remove multiple entities from light emission.
     *
     * @param entities The entities to stop emitting light
     */
    void removeLightSources(Collection<Entity> entities);

    /**
     * Register an entity as a light source with full configuration.
     *
     * @param entity The entity to emit light
     * @param info   Light source configuration (level, radius, height)
     */
    void addLightSource(Entity entity, LightSourceInfo info);

    /**
     * Get full light info for entity.
     *
     * @param entity The entity to check
     * @return Light info, or null if not a light source
     */
    LightSourceInfo getLightSourceInfo(Entity entity);

    /**
     * Register a detector that evaluates entities for light emission.
     * Detectors are called when scanning entities (e.g., on chunk load).
     *
     * @param detector The detector to register
     */
    void registerDetector(EntityLightDetector detector);

    /**
     * Unregister a previously registered detector.
     *
     * @param detector The detector to unregister
     */
    void unregisterDetector(EntityLightDetector detector);

    /**
     * Scan a collection of entities using registered detectors
     * and register any that should emit light.
     *
     * @param entities The entities to scan
     */
    void scanEntities(Collection<Entity> entities);
}
