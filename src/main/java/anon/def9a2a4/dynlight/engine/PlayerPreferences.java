package anon.def9a2a4.dynlight.engine;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Manages persistent player preferences for dynamic lights.
 * Players are enabled by default; this stores the set of disabled players.
 * Uses debounced async saving to avoid blocking the main thread on every toggle.
 */
public class PlayerPreferences {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    // Debounced save: dirty flag and scheduled task
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private BukkitTask saveTask;
    private static final long SAVE_DELAY_TICKS = 100L; // 5 seconds

    public PlayerPreferences(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    /**
     * Check if a player has dynamic lights enabled.
     *
     * @param playerId The player's UUID
     * @return true if enabled (default), false if disabled
     */
    public boolean isEnabled(UUID playerId) {
        return !disabledPlayers.contains(playerId);
    }

    /**
     * Set whether a player has dynamic lights enabled.
     * Saves are debounced to avoid excessive disk I/O.
     *
     * @param playerId The player's UUID
     * @param enabled  true to enable, false to disable
     */
    public void setEnabled(UUID playerId, boolean enabled) {
        if (enabled) {
            disabledPlayers.remove(playerId);
        } else {
            disabledPlayers.add(playerId);
        }
        scheduleSave();
    }

    /**
     * Schedule a debounced save operation.
     * Multiple changes within the delay window will be batched into a single save.
     */
    private void scheduleSave() {
        // Mark as dirty - if already scheduled, the scheduled task will handle it
        if (dirty.compareAndSet(false, true)) {
            // Schedule async save after delay
            saveTask = plugin.getServer().getScheduler().runTaskLaterAsynchronously(
                    plugin,
                    this::saveNow,
                    SAVE_DELAY_TICKS
            );
        }
    }

    /**
     * Perform the actual save operation (called from scheduled task).
     */
    private void saveNow() {
        // Clear dirty flag before saving so new changes can schedule another save
        dirty.set(false);
        saveTask = null;

        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }
            YamlConfiguration yaml = new YamlConfiguration();
            List<String> disabled = disabledPlayers.stream()
                    .map(UUID::toString)
                    .toList();
            yaml.set("disabled", disabled);
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save player preferences", e);
        }
    }

    /**
     * Get an unmodifiable view of disabled player UUIDs.
     *
     * @return Set of disabled player UUIDs
     */
    public Set<UUID> getDisabledPlayers() {
        return Collections.unmodifiableSet(disabledPlayers);
    }

    /**
     * Load preferences from disk.
     */
    private void load() {
        if (!dataFile.exists()) {
            return;
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
            List<String> disabled = yaml.getStringList("disabled");
            for (String uuidStr : disabled) {
                try {
                    disabledPlayers.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in players.yml: " + uuidStr);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load player preferences", e);
        }
    }

    /**
     * Save preferences to disk immediately (synchronously).
     * Called on plugin disable to ensure data is persisted.
     */
    public void save() {
        // Cancel any pending async save
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        dirty.set(false);

        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }
            YamlConfiguration yaml = new YamlConfiguration();
            List<String> disabled = disabledPlayers.stream()
                    .map(UUID::toString)
                    .toList();
            yaml.set("disabled", disabled);
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save player preferences", e);
        }
    }
}
