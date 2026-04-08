package anon.def9a2a4.dynlight;

import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.detection.BurningEntityListener;
import anon.def9a2a4.dynlight.detection.ChunkLoadListener;
import anon.def9a2a4.dynlight.detection.EntityLightListener;
import anon.def9a2a4.dynlight.detection.ItemLightListener;
import anon.def9a2a4.dynlight.detection.PlayerLightDetector;
import anon.def9a2a4.dynlight.detection.ProjectileLightListener;
import anon.def9a2a4.dynlight.detection.cache.PlayerEquipmentCache;
import anon.def9a2a4.dynlight.engine.InvalidationTracker;
import anon.def9a2a4.dynlight.engine.LightRenderer;
import anon.def9a2a4.dynlight.engine.LightSourceManager;
import anon.def9a2a4.dynlight.engine.PlayerPreferences;
import anon.def9a2a4.dynlight.engine.command.DynLightCommand;
import anon.def9a2a4.dynlight.engine.data.LightSnapshot;
import anon.def9a2a4.dynlight.engine.data.PlayerLightUpdate;
import anon.def9a2a4.dynlight.engine.data.PlayerSnapshot;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class DynLightPlugin extends JavaPlugin {

    private static volatile DynLightPlugin instance;

    private DynLightConfig config;
    private LightSourceManager sourceManager;
    private LightRenderer renderer;
    private PlayerLightDetector playerDetector;
    private PlayerPreferences playerPreferences;
    private PlayerEquipmentCache equipmentCache;
    private BurningEntityListener burningEntityListener;
    private ProjectileLightListener projectileLightListener;
    private BukkitTask updateTask;
    private BukkitTask fireSweepTask;
    private BukkitTask cleanupTask;

    // Tracks invalidated light sources to prevent stale async results from re-adding removed lights
    private final InvalidationTracker invalidationTracker = new InvalidationTracker();

    // Async state: null = idle, COMPUTING = async in progress, ComputedUpdate = result ready
    // Using a single atomic eliminates race conditions between separate flags
    private final AtomicReference<Object> asyncState = new AtomicReference<>();
    private static final Object COMPUTING = new Object();

    // Reusable set for mergeSnapshots() to avoid allocation each tick
    private final java.util.Set<UUID> mergePlayerIds = new java.util.HashSet<>();

    // Holds the computed updates and the source data needed for application
    private record ComputedUpdate(
            Map<UUID, PlayerLightUpdate> updates,
            List<LightSnapshot> lightSources,
            long generation
    ) {}

    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        this.config = new DynLightConfig(getConfig());

        // Initialize components
        this.sourceManager = new LightSourceManager();
        this.playerPreferences = new PlayerPreferences(this);
        this.renderer = new LightRenderer(config, playerPreferences);
        this.equipmentCache = new PlayerEquipmentCache(this);
        this.playerDetector = new PlayerLightDetector(config, equipmentCache);

        // Create event listeners (pass sourceManager which implements DynLightAPI)
        // Each listener registers itself as a detector for chunk-load scanning
        this.burningEntityListener = new BurningEntityListener(config, sourceManager);
        this.projectileLightListener = new ProjectileLightListener(config, sourceManager);

        // Register event listeners
        getServer().getPluginManager().registerEvents(renderer, this);
        getServer().getPluginManager().registerEvents(equipmentCache, this);
        getServer().getPluginManager().registerEvents(new ItemLightListener(config, sourceManager), this);
        getServer().getPluginManager().registerEvents(new EntityLightListener(config, sourceManager), this);
        getServer().getPluginManager().registerEvents(burningEntityListener, this);
        getServer().getPluginManager().registerEvents(projectileLightListener, this);
        // Centralized chunk load handler - scans entities using registered detectors
        getServer().getPluginManager().registerEvents(new ChunkLoadListener(sourceManager, this), this);

        // Start consolidated fire sweep task
        long fireSweepInterval = config.fireSweepInterval;
        fireSweepTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            burningEntityListener.checkFireExpiration();
            projectileLightListener.checkFireExpiration();
            projectileLightListener.checkFireIgnition();
        }, fireSweepInterval, fireSweepInterval);

        // Periodic cleanup task to remove stale light sources (every 200 ticks = 10 seconds)
        cleanupTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            int removed = sourceManager.cleanup();
            if (removed > 0) {
                getLogger().fine("Cleaned up " + removed + " stale light sources");
            }
        }, 200L, 200L);

        // Start update loop with async/sync coordination
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                onSyncTick();
            }
        }.runTaskTimer(this, 0L, config.updateInterval);

        new Metrics(this, 29162);

        // Register command
        PluginCommand cmd = getCommand("dynlight");
        if (cmd != null) {
            DynLightCommand commandHandler = new DynLightCommand(
                    this, () -> config, sourceManager, renderer, playerPreferences,
                    this::reloadConfiguration, this::regenerateLightSources
            );
            cmd.setExecutor(commandHandler);
            cmd.setTabCompleter(commandHandler);
        }

        // Set instance last to ensure all components are initialized
        instance = this;
        getLogger().info("DynLight plugin enabled!");
    }

    /**
     * Main sync tick handler. Applies pending async results and kicks off new async computation.
     */
    private void onSyncTick() {
        try {
            // 1. Check for and apply pending result from async computation
            Object state = asyncState.get();
            if (state instanceof ComputedUpdate computed) {
                // Atomically clear the result and apply it
                if (asyncState.compareAndSet(computed, null)) {
                    // Pass invalidation tracker and generation to filter out stale sources
                    renderer.applyUpdates(computed.updates(), computed.lightSources(),
                                          invalidationTracker, computed.generation());
                }
                // If CAS failed, another tick is handling it - we'll try again next tick
            }

            // 2. Try to start new async computation (only if idle)
            if (!asyncState.compareAndSet(null, COMPUTING)) {
                return; // Either still computing or result pending - skip this tick
            }

            // 3. Start a new generation for invalidation tracking
            long generation = invalidationTracker.startGeneration();

            // 4. Capture fresh snapshots on main thread
            // Player lights are polled (no reliable events for held item changes)
            List<LightSnapshot> playerSnapshots = playerDetector.capturePlayerLights();
            // All other entities are event-driven and stored in sourceManager
            List<LightSnapshot> entitySnapshots = sourceManager.getApiSnapshots();

            // Merge player snapshots with entity snapshots (player takes priority if both exist)
            List<LightSnapshot> allSnapshots = mergeSnapshots(playerSnapshots, entitySnapshots);

            // Capture player positions for distance calculations
            List<PlayerSnapshot> playerPositions = capturePlayerSnapshots();

            // 5. Snapshot entity state BEFORE async (prevents race condition)
            Map<UUID, Map<UUID, LightRenderer.PlacedLight>> stateSnapshot = renderer.snapshotEntityState();

            // 6. Kick off async computation - result stored atomically when done
            final long gen = generation;
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    Map<UUID, PlayerLightUpdate> updates = renderer.computeUpdates(allSnapshots, playerPositions, stateSnapshot);
                    // Single atomic set of result - no separate "running" flag needed
                    asyncState.set(new ComputedUpdate(updates, allSnapshots, gen));
                } catch (Exception e) {
                    getLogger().severe("Error computing light updates: " + e.getMessage());
                    getLogger().severe(e.toString());
                    for (StackTraceElement element : e.getStackTrace()) {
                        getLogger().severe("  at " + element);
                    }
                    // Set empty result to unblock next computation
                    asyncState.set(new ComputedUpdate(new java.util.HashMap<>(), new ArrayList<>(), gen));
                }
            });
        } catch (Exception e) {
            getLogger().severe("Error in light update cycle: " + e.getMessage());
            getLogger().severe(e.toString());
            for (StackTraceElement element : e.getStackTrace()) {
                getLogger().severe("  at " + element);
            }
            // Reset state to allow recovery on next tick
            asyncState.set(null);
        }
    }

    /**
     * Merge player and entity snapshots, with player snapshots taking priority.
     */
    private List<LightSnapshot> mergeSnapshots(List<LightSnapshot> players, List<LightSnapshot> entities) {
        // Fast path: no player overrides (unlikely but possible)
        if (players.isEmpty()) {
            return entities;
        }

        // Reuse set to avoid allocation (cleared and refilled each tick)
        mergePlayerIds.clear();
        for (LightSnapshot snapshot : players) {
            mergePlayerIds.add(snapshot.entityId());
        }

        // Pre-size merged list
        List<LightSnapshot> merged = new ArrayList<>(players.size() + entities.size());
        merged.addAll(players);

        // Add entity snapshots that don't have a player override
        for (LightSnapshot snapshot : entities) {
            if (!mergePlayerIds.contains(snapshot.entityId())) {
                merged.add(snapshot);
            }
        }

        return merged;
    }

    /**
     * Capture player positions for async distance calculations.
     */
    private List<PlayerSnapshot> capturePlayerSnapshots() {
        var onlinePlayers = Bukkit.getOnlinePlayers();
        List<PlayerSnapshot> snapshots = new ArrayList<>(onlinePlayers.size());

        for (Player player : onlinePlayers) {
            Location loc = player.getLocation();
            if (loc.getWorld() == null) {
                continue; // Player in invalid state (logging out, etc.)
            }
            snapshots.add(new PlayerSnapshot(
                    player.getUniqueId(),
                    loc.getWorld().getName(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ()
            ));
        }

        return snapshots;
    }

    /**
     * Regenerate all light sources: clears all rendered lights and the source registry,
     * then rescans all loaded entities. Used by /dynlight regen and after config reload.
     */
    private void regenerateLightSources() {
        renderer.clearAllPlayers();
        sourceManager.clearAll();
        asyncState.set(null);
        rescanAllEntities();
    }

    /**
     * Rescan all entities in all loaded chunks across all worlds.
     * Registers any detected light sources in the source manager.
     */
    private void rescanAllEntities() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                sourceManager.scanEntities(List.of(chunk.getEntities()));
            }
        }
    }

    /**
     * Reload configuration from disk.
     * Called by /dynlight reload command.
     */
    private void reloadConfiguration() {
        reloadConfig();
        this.config = new DynLightConfig(getConfig());
        // Update renderer with new config (for render distance changes)
        this.renderer.updateConfig(config);
        this.playerDetector = new PlayerLightDetector(config, equipmentCache);

        // Reschedule update task with new interval
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                onSyncTick();
            }
        }.runTaskTimer(this, 0L, config.updateInterval);

        // Reschedule fire sweep task with new interval
        if (fireSweepTask != null) {
            fireSweepTask.cancel();
        }
        long interval = config.fireSweepInterval;
        fireSweepTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            burningEntityListener.checkFireExpiration();
            projectileLightListener.checkFireExpiration();
            projectileLightListener.checkFireIgnition();
        }, interval, interval);

        regenerateLightSources();
        getLogger().info("Configuration reloaded");
    }

    @Override
    public void onDisable() {
        // Stop scheduled tasks
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        if (fireSweepTask != null) {
            fireSweepTask.cancel();
        }
        if (updateTask != null) {
            updateTask.cancel();
        }
        if (renderer != null) {
            renderer.clearAllPlayers();
        }
        if (playerPreferences != null) {
            playerPreferences.save();
        }
        getLogger().info("DynLight plugin disabled!");
    }

    /**
     * Get the DynLight API for other plugins to register light sources.
     *
     * @return The DynLightAPI instance
     * @throws IllegalStateException if called before the plugin is fully initialized
     */
    public static DynLightAPI getAPI() {
        if (instance == null) {
            throw new IllegalStateException("DynLight plugin is not initialized yet");
        }
        return instance.sourceManager;
    }
}
