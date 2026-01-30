package anon.def9a2a4.dynlight.engine;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages persistent player preferences for dynamic lights.
 * Players are enabled by default; this stores the set of disabled players.
 */
public class PlayerPreferences {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

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
        save();
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
     * Save preferences to disk.
     */
    public void save() {
        try {
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
