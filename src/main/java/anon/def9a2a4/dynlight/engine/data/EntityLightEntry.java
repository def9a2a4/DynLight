package anon.def9a2a4.dynlight.engine.data;

/**
 * Bundles the ideal position and light level for a single entity's light placement.
 * Used in {@link PlayerLightUpdate} to preserve per-entity identity through the async→sync pipeline.
 *
 * @param idealPos   The entity's block position (where it wants light placed)
 * @param lightLevel The light level to place (1-15)
 */
public record EntityLightEntry(BlockPos idealPos, int lightLevel) {}
