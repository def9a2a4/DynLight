package anon.def9a2a4.dynlight;

import anon.def9a2a4.dynlight.command.DynLightCommand;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DynLightPlugin extends JavaPlugin {

    private static volatile DynLightPlugin instance;

    private DynLightConfig config;
    private LightSourceManager sourceManager;
    private LightRenderer renderer;
    private PlayerLightDetector playerDetector;
    private PlayerPreferences playerPreferences;
    private BurningEntityListener burningEntityListener;
    private ProjectileLightListener projectileLightListener;
    private BukkitTask updateTask;
    private BukkitTask fireSweepTask;

    // Pending updates computed by async thread, to be applied on next sync tick
    private final AtomicReference<ComputedUpdate> pendingUpdate = new AtomicReference<>();

    // Flag to prevent async task overlap when computation is slow
    private final AtomicBoolean asyncRunning = new AtomicBoolean(false);

    // Holds the computed updates and the source data needed for application
    private record ComputedUpdate(
            Map<UUID, PlayerLightUpdate> updates,
            List<LightSnapshot> lightSources
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
        this.playerDetector = new PlayerLightDetector(config);

        // Create event listeners
        this.burningEntityListener = new BurningEntityListener(config, sourceManager);
        this.projectileLightListener = new ProjectileLightListener(config, sourceManager);

        // Register event listeners
        getServer().getPluginManager().registerEvents(renderer, this);
        getServer().getPluginManager().registerEvents(new ItemLightListener(config, sourceManager), this);
        getServer().getPluginManager().registerEvents(new EntityLightListener(config, sourceManager), this);
        getServer().getPluginManager().registerEvents(burningEntityListener, this);
        getServer().getPluginManager().registerEvents(projectileLightListener, this);

        // Start consolidated fire sweep task
        long fireSweepInterval = config.fireSweepInterval;
        fireSweepTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            burningEntityListener.checkFireExpiration();
            projectileLightListener.checkFireExpiration();
        }, fireSweepInterval, fireSweepInterval);

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
                    this, () -> config, sourceManager, renderer, playerPreferences, this::reloadConfiguration
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
            // 1. Apply pending updates from last async cycle
            ComputedUpdate computed = pendingUpdate.getAndSet(null);
            if (computed != null) {
                renderer.applyUpdates(computed.updates(), computed.lightSources());
            }

            // Skip if previous async task is still running (prevents task queue buildup)
            // Use compareAndSet for atomic check-and-set semantics
            if (!asyncRunning.compareAndSet(false, true)) {
                return;
            }

            // 2. Capture fresh snapshots on main thread
            // Player lights are polled (no reliable events for held item changes)
            List<LightSnapshot> playerSnapshots = playerDetector.capturePlayerLights();
            // All other entities are event-driven and stored in sourceManager
            List<LightSnapshot> entitySnapshots = sourceManager.getApiSnapshots();

            // Merge player snapshots with entity snapshots (player takes priority if both exist)
            List<LightSnapshot> allSnapshots = mergeSnapshots(playerSnapshots, entitySnapshots);

            // Capture player positions for distance calculations
            List<PlayerSnapshot> playerPositions = capturePlayerSnapshots();

            // 3. Snapshot entity state BEFORE async (prevents race condition)
            var stateSnapshot = renderer.snapshotEntityState();

            // 4. Kick off async computation with the snapshot
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    Map<UUID, PlayerLightUpdate> updates = renderer.computeUpdates(allSnapshots, playerPositions, stateSnapshot);
                    pendingUpdate.set(new ComputedUpdate(updates, allSnapshots));
                } catch (Exception e) {
                    getLogger().severe("Error computing light updates: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    asyncRunning.set(false);
                }
            });
        } catch (Exception e) {
            getLogger().severe("Error in light update cycle: " + e.getMessage());
            e.printStackTrace();
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

        // Build set of player entity IDs
        java.util.Set<UUID> playerIds = new java.util.HashSet<>(players.size());
        for (LightSnapshot snapshot : players) {
            playerIds.add(snapshot.entityId());
        }

        // Pre-size merged list
        List<LightSnapshot> merged = new ArrayList<>(players.size() + entities.size());
        merged.addAll(players);

        // Add entity snapshots that don't have a player override
        for (LightSnapshot snapshot : entities) {
            if (!playerIds.contains(snapshot.entityId())) {
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
     * Reload configuration from disk.
     * Called by /dynlight reload command.
     */
    private void reloadConfiguration() {
        reloadConfig();
        this.config = new DynLightConfig(getConfig());
        // Update renderer with new config (for render distance changes)
        this.renderer.updateConfig(config);
        this.playerDetector = new PlayerLightDetector(config);

        // Reschedule fire sweep task with new interval
        if (fireSweepTask != null) {
            fireSweepTask.cancel();
        }
        long interval = config.fireSweepInterval;
        fireSweepTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            burningEntityListener.checkFireExpiration();
            projectileLightListener.checkFireExpiration();
        }, interval, interval);

        getLogger().info("Configuration reloaded");
    }

    @Override
    public void onDisable() {
        // Stop scheduled tasks
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
