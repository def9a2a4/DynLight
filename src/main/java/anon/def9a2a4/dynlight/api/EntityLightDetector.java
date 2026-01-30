package anon.def9a2a4.dynlight.api;

import org.bukkit.entity.Entity;

/**
 * Functional interface for detecting if an entity should emit light.
 * Detectors are called when scanning entities (e.g., on chunk load)
 * to determine which should be registered as light sources.
 */
@FunctionalInterface
public interface EntityLightDetector {
    /**
     * Evaluate if an entity should emit light.
     *
     * @param entity The entity to evaluate
     * @return Light info if entity should glow, or null if not
     */
    LightSourceInfo detect(Entity entity);
}
