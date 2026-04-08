package anon.def9a2a4.dynlight.engine;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.engine.data.BlockPos;
import anon.def9a2a4.dynlight.engine.data.LightSnapshot;
import anon.def9a2a4.dynlight.engine.data.PlayerLightUpdate;
import anon.def9a2a4.dynlight.engine.data.PlayerSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import io.papermc.paper.math.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Renders dynamic lights to players using fake light blocks.
 * Split into async computation and sync application for performance.
 */
public class LightRenderer implements Listener {

    private DynLightConfig config;
    private PlayerPreferences preferences;
    private final Plugin plugin;
    private double maxDistanceSquared;

    // Pre-created light block data for each level (1-15)
    private final Light[] lightLevels = new Light[16];
    private final Light[] lightLevelsWaterlogged = new Light[16];

    // Precomputed offsets for light placement, keyed by (radius, height) pair
    private final Map<Long, List<Offset>> offsetCache = new HashMap<>();

    // Per-player tracking: entityId -> placed light block position, level, and parent
    private final Map<UUID, Map<UUID, PlacedLight>> playerEntityState = new HashMap<>();

    private record Offset(int dx, int dy, int dz) {}
    private record LightPlacement(BlockPos pos, boolean isWater) {}

    /** Records a placed light block with its position and level. */
    public record PlacedLight(BlockPos pos, int level) {}

    // Static comparator to avoid allocation on each computeOffsets() call
    private static final Comparator<Offset> DISTANCE_COMPARATOR =
            Comparator.comparingInt(o -> o.dx * o.dx + o.dy * o.dy + o.dz * o.dz);

    public LightRenderer(DynLightConfig config, PlayerPreferences preferences, Plugin plugin) {
        this.config = config;
        this.preferences = preferences;
        this.plugin = plugin;
        int renderDistance = config.renderDistance;
        this.maxDistanceSquared = (double) renderDistance * renderDistance;

        // Pre-create BlockData for each light level (0-15)
        // Light level 0 is technically "no light" but we initialize it to avoid NPE
        for (int i = 0; i <= 15; i++) {
            Light light = (Light) Bukkit.createBlockData(Material.LIGHT);
            light.setLevel(i);
            lightLevels[i] = light;
        }

        // Pre-create waterlogged light blocks for underwater placement
        for (int i = 0; i <= 15; i++) {
            Light light = (Light) Bukkit.createBlockData(Material.LIGHT);
            light.setLevel(i);
            light.setWaterlogged(true);
            lightLevelsWaterlogged[i] = light;
        }
    }

    /**
     * Update configuration (called on reload).
     * Updates render distance for future calculations.
     *
     * @param newConfig The new configuration
     */
    public void updateConfig(DynLightConfig newConfig) {
        this.config = newConfig;
        this.maxDistanceSquared = (double) newConfig.renderDistance * newConfig.renderDistance;
    }

    private List<Offset> getOffsets(int radius, int height) {
        long key = ((long) radius << 32) | (height & 0xFFFFFFFFL);
        return offsetCache.computeIfAbsent(key, k -> computeOffsets(radius, height));
    }

    private List<Offset> computeOffsets(int radius, int height) {
        List<Offset> offsets = new ArrayList<>();

        // Center column
        for (int dy = 0; dy < height; dy++) {
            offsets.add(new Offset(0, dy, 0));
        }

        // Cardinal directions only (no diagonals - intentional design choice)
        for (int r = 1; r <= radius; r++) {
            for (int dy = 0; dy < height; dy++) {
                offsets.add(new Offset(r, dy, 0));
                offsets.add(new Offset(-r, dy, 0));
                offsets.add(new Offset(0, dy, r));
                offsets.add(new Offset(0, dy, -r));
            }
        }

        // Sort by distance from origin (use static comparator to avoid allocation)
        offsets.sort(DISTANCE_COMPARATOR);

        return offsets;
    }

    /**
     * Snapshot the current player entity state for async processing.
     * MUST be called from main thread before async computation to avoid race conditions.
     *
     * @return Deep copy of player entity state safe for async access
     */
    public Map<UUID, Map<UUID, PlacedLight>> snapshotEntityState() {
        Map<UUID, Map<UUID, PlacedLight>> snapshot = new HashMap<>(playerEntityState.size());
        for (Map.Entry<UUID, Map<UUID, PlacedLight>> entry : playerEntityState.entrySet()) {
            snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return snapshot;
    }

    /**
     * Compute light updates for all players. ASYNC-SAFE.
     * Determines which entities need light updates per player.
     *
     * @param lightSources  All light source snapshots
     * @param players       All player snapshots
     * @param stateSnapshot Snapshot of entity state (from snapshotEntityState, taken on main thread)
     * @return Map of player UUID to their computed light updates
     */
    public Map<UUID, PlayerLightUpdate> computeUpdates(
            List<LightSnapshot> lightSources,
            List<PlayerSnapshot> players,
            Map<UUID, Map<UUID, PlacedLight>> stateSnapshot) {

        // Pre-size based on player count
        Map<UUID, PlayerLightUpdate> updates = new HashMap<>(players.size());

        for (PlayerSnapshot player : players) {
            // Skip players who have disabled dynamic lights
            if (!preferences.isEnabled(player.playerId())) {
                continue;
            }
            Map<UUID, PlacedLight> previousState = stateSnapshot.getOrDefault(player.playerId(), Map.of());
            PlayerLightUpdate update = computePlayerUpdate(player, lightSources, previousState);
            updates.put(player.playerId(), update);
        }

        return updates;
    }

    /**
     * Compute light update for a single player. ASYNC-SAFE.
     * Uses single-pass algorithm for efficiency.
     */
    private PlayerLightUpdate computePlayerUpdate(PlayerSnapshot player, List<LightSnapshot> lightSources,
                                                   Map<UUID, PlacedLight> previousState) {
        // Calculate which entities should have lights for this player
        Map<UUID, LightSnapshot> currentSources = new HashMap<>();
        for (LightSnapshot source : lightSources) {
            // Must be in same world (check first - cheaper than distance calc)
            if (!source.worldName().equals(player.worldName())) {
                continue;
            }
            // Must be within render distance
            if (player.distanceSquaredTo(source) > maxDistanceSquared) {
                continue;
            }
            // Player snapshots come first in merged list and take priority
            // For any duplicates, keep the first occurrence
            if (!currentSources.containsKey(source.entityId())) {
                currentSources.put(source.entityId(), source);
            }
        }

        // Pre-size based on expected changes (use previousState size as heuristic)
        int expectedSize = Math.max(previousState.size(), currentSources.size());
        Map<BlockPos, Integer> toAdd = new HashMap<>(expectedSize);
        Set<BlockPos> toRemove = new HashSet<>(previousState.size() / 4 + 1);

        // Track which previous entities we've seen (for single-pass removal detection)
        Set<UUID> seenPreviousEntities = new HashSet<>(previousState.size());

        // Single pass: check current sources against previous state
        for (Map.Entry<UUID, LightSnapshot> entry : currentSources.entrySet()) {
            UUID entityId = entry.getKey();
            LightSnapshot source = entry.getValue();

            PlacedLight oldPlacement = previousState.get(entityId);
            if (oldPlacement != null) {
                seenPreviousEntities.add(entityId);
            }

            // Compare positions using primitives first (avoid BlockPos allocation for unchanged)
            int newX = source.blockX();
            int newY = source.blockY();
            int newZ = source.blockZ();

            if (oldPlacement == null) {
                // New entity - needs light placed
                toAdd.put(new BlockPos(source.worldName(), newX, newY, newZ), source.lightLevel());
            } else {
                BlockPos oldPos = oldPlacement.pos();
                int dx = Math.abs(newX - oldPos.x());
                int dy = Math.abs(newY - oldPos.y());
                int dz = Math.abs(newZ - oldPos.z());

                if (dx >= 1 || dy >= 1 || dz >= 1) {
                    // Entity moved to different block - remove old, add new
                    toRemove.add(oldPos);
                    toAdd.put(new BlockPos(source.worldName(), newX, newY, newZ), source.lightLevel());
                } else if (oldPlacement.level() != source.lightLevel()) {
                    // Same position but light level changed - update in place
                    toAdd.put(oldPos, source.lightLevel());
                }
                // else: Unchanged - keep existing (no action needed)
            }
        }

        // Single pass removal: entities in previous but not seen are removed
        for (Map.Entry<UUID, PlacedLight> entry : previousState.entrySet()) {
            if (!seenPreviousEntities.contains(entry.getKey())) {
                toRemove.add(entry.getValue().pos());
            }
        }

        // Don't remove positions that are being re-added
        toRemove.removeAll(toAdd.keySet());

        return new PlayerLightUpdate(toAdd, toRemove);
    }

    /**
     * Apply computed updates to players. MAIN THREAD ONLY.
     * Sends block changes and updates internal state.
     * Filters out invalidated sources to prevent stale async results from re-adding removed lights.
     *
     * @param updates      Map of player UUID to their computed updates
     * @param lightSources The original light sources (needed for entity config lookup)
     * @param tracker      Invalidation tracker to filter stale sources
     * @param generation   Snapshot generation for invalidation checking
     */
    public void applyUpdates(Map<UUID, PlayerLightUpdate> updates, List<LightSnapshot> lightSources,
                             InvalidationTracker tracker, long generation) {
        // Build entity lookup maps (forward and reverse), filtering out invalidated sources
        Map<UUID, LightSnapshot> sourceMap = new HashMap<>();
        Map<BlockPos, UUID> positionToEntity = new HashMap<>();

        for (LightSnapshot source : lightSources) {
            // Check if this source is invalidated (entity removed between snapshot and apply)
            if (tracker.isInvalidated(source.entityId(), generation)) {
                continue;
            }

            sourceMap.put(source.entityId(), source);
            // Build reverse lookup: position -> entity UUID
            BlockPos pos = new BlockPos(source.worldName(), source.blockX(), source.blockY(), source.blockZ());
            positionToEntity.put(pos, source.entityId());
        }

        for (Map.Entry<UUID, PlayerLightUpdate> entry : updates.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerLightUpdate update = entry.getValue();

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            applyPlayerUpdate(player, update, sourceMap, positionToEntity);
        }
    }

    /**
     * Apply update for a single player. MAIN THREAD ONLY.
     */
    private void applyPlayerUpdate(Player player, PlayerLightUpdate update,
                                   Map<UUID, LightSnapshot> sourceMap, Map<BlockPos, UUID> positionToEntity) {
        UUID playerId = player.getUniqueId();
        World world = player.getWorld();
        String worldName = world.getName();

        // Get or create entity state map
        Map<UUID, PlacedLight> entityState = playerEntityState.computeIfAbsent(playerId, k -> new HashMap<>());

        // Single-pass: build reverse lookup AND used positions set simultaneously
        Map<BlockPos, UUID> statePositionToEntity = new HashMap<>(entityState.size());
        Set<BlockPos> usedPositions = new HashSet<>(entityState.size());
        for (Map.Entry<UUID, PlacedLight> entry : entityState.entrySet()) {
            BlockPos pos = entry.getValue().pos();
            statePositionToEntity.put(pos, entry.getKey());
            usedPositions.add(pos);
        }

        // Collect all block changes for batch sending
        Map<Position, BlockData> batchChanges = new HashMap<>();

        // Collect removals (restore original blocks)
        for (BlockPos pos : update.toRemove()) {
            if (!pos.worldName().equals(worldName)) {
                continue;
            }
            Location loc = new Location(world, pos.x(), pos.y(), pos.z());
            batchChanges.put(Position.block(pos.x(), pos.y(), pos.z()), loc.getBlock().getBlockData());

            // Remove from entity state using O(1) lookup
            UUID entityToRemove = statePositionToEntity.get(pos);
            if (entityToRemove != null) {
                entityState.remove(entityToRemove);
                usedPositions.remove(pos); // Keep usedPositions in sync
            }
        }

        // Collect additions (find valid positions, add lights)
        for (Map.Entry<BlockPos, Integer> entry : update.toAdd().entrySet()) {
            BlockPos idealPos = entry.getKey();
            int lightLevel = entry.getValue();

            if (!idealPos.worldName().equals(worldName)) {
                continue;
            }

            // Validate light level bounds (1-15)
            if (lightLevel < 1 || lightLevel > 15) {
                continue;
            }

            // Find which entity this position corresponds to (O(1) lookup)
            UUID entityId = positionToEntity.get(idealPos);
            if (entityId == null) {
                continue;
            }

            LightSnapshot source = sourceMap.get(entityId);
            if (source == null) {
                continue; // Safety check
            }

            // Find valid position for light placement using snapshot data
            LightPlacement placement = findLightPosition(world, idealPos,
                    source.horizontalRadius(), source.height(), usedPositions);
            if (placement != null) {
                BlockPos placedPos = placement.pos();
                Light lightData = placement.isWater() ? lightLevelsWaterlogged[lightLevel] : lightLevels[lightLevel];
                batchChanges.put(Position.block(placedPos.x(), placedPos.y(), placedPos.z()), lightData);
                usedPositions.add(placedPos);
                entityState.put(entityId, new PlacedLight(placedPos, lightLevel));
            }
        }

        // Send all changes in one batch
        if (!batchChanges.isEmpty()) {
            player.sendMultiBlockChange(batchChanges);
        }
    }


    /**
     * Find a valid position to place a light block at or near the given position.
     * Returns the position and water status, or null if no valid position.
     */
    private LightPlacement findLightPosition(World world, BlockPos idealPos,
                                              int horizontalRadius, int height,
                                              Set<BlockPos> usedPositions) {
        // Fast path: check ideal position first (most common success case)
        if (!usedPositions.contains(idealPos)) {
            Material type = world.getBlockAt(idealPos.x(), idealPos.y(), idealPos.z()).getType();
            if (type == Material.AIR || type == Material.CAVE_AIR) {
                return new LightPlacement(idealPos, false);
            } else if (type == Material.WATER) {
                return new LightPlacement(idealPos, true);
            }
        }

        // Slower path: iterate through offset positions
        List<Offset> offsets = getOffsets(horizontalRadius, height);
        String worldName = idealPos.worldName();
        int baseX = idealPos.x();
        int baseY = idealPos.y();
        int baseZ = idealPos.z();

        for (Offset o : offsets) {
            // Skip (0,0,0) since we already checked ideal position
            if (o.dx == 0 && o.dy == 0 && o.dz == 0) {
                continue;
            }

            int tryX = baseX + o.dx;
            int tryY = baseY + o.dy;
            int tryZ = baseZ + o.dz;

            Material type = world.getBlockAt(tryX, tryY, tryZ).getType();
            boolean isWater = type == Material.WATER;
            if (type != Material.AIR && type != Material.CAVE_AIR && !isWater) {
                continue;
            }

            // Only create BlockPos for positions that pass the air/water check
            BlockPos tryPos = new BlockPos(worldName, tryX, tryY, tryZ);
            if (!usedPositions.contains(tryPos)) {
                return new LightPlacement(tryPos, isWater);
            }
        }

        return null;
    }

    /**
     * Clear all sent light blocks for all players (called on plugin disable).
     */
    public void clearAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearPlayer(player);
        }
        playerEntityState.clear();
    }

    /**
     * Clear all sent light blocks for a specific player.
     * Called when player disables lights or changes world.
     */
    public void clearPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        Map<UUID, PlacedLight> entityState = playerEntityState.remove(playerId);

        if (entityState != null && player.isOnline()) {
            World world = player.getWorld();
            String worldName = world.getName();

            Map<Position, BlockData> batchChanges = new HashMap<>(entityState.size());

            for (PlacedLight placed : entityState.values()) {
                if (placed.pos().worldName().equals(worldName)) {
                    Location loc = new Location(world, placed.pos().x(), placed.pos().y(), placed.pos().z());
                    batchChanges.put(
                        Position.block(placed.pos().x(), placed.pos().y(), placed.pos().z()),
                        loc.getBlock().getBlockData()
                    );
                }
            }

            if (!batchChanges.isEmpty()) {
                player.sendMultiBlockChange(batchChanges);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Delay clearing state so that chunk data is fully sent to the client first.
        // Without this, fake light blocks sent immediately get overwritten by real chunk
        // data, and subsequent render ticks skip re-sending because state looks unchanged.
        UUID playerId = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                playerEntityState.remove(playerId);
            }
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerEntityState.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        clearPlayer(event.getPlayer());
    }
}
