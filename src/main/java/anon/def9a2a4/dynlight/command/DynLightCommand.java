package anon.def9a2a4.dynlight.command;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.LightSourceManager;
import anon.def9a2a4.dynlight.LightRenderer;
import anon.def9a2a4.dynlight.PlayerPreferences;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Command handler for /dynlight commands.
 * Provides help, enable, disable, info, reload, stats, and debug subcommands.
 */
public class DynLightCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final Supplier<DynLightConfig> configSupplier;
    private final LightSourceManager sourceManager;
    private final LightRenderer renderer;
    private final PlayerPreferences preferences;
    private final Runnable reloadCallback;

    private boolean debugEnabled = false;

    public DynLightCommand(JavaPlugin plugin, Supplier<DynLightConfig> configSupplier,
                           LightSourceManager sourceManager, LightRenderer renderer,
                           PlayerPreferences preferences, Runnable reloadCallback) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.sourceManager = sourceManager;
        this.renderer = renderer;
        this.preferences = preferences;
        this.reloadCallback = reloadCallback;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "help" -> sendHelp(sender);
            case "enable" -> handleEnable(sender);
            case "disable" -> handleDisable(sender);
            case "info" -> handleInfo(sender);
            case "reload" -> handleReload(sender);
            case "stats" -> handleStats(sender);
            case "debug" -> handleDebug(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleEnable(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return;
        }

        if (!sender.hasPermission("dynlight.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        if (preferences.isEnabled(player.getUniqueId())) {
            sender.sendMessage(ChatColor.YELLOW + "Dynamic lights are already enabled for you.");
        } else {
            preferences.setEnabled(player.getUniqueId(), true);
            sender.sendMessage(ChatColor.GREEN + "Dynamic lights enabled!");
        }
    }

    private void handleDisable(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return;
        }

        if (!sender.hasPermission("dynlight.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        if (!preferences.isEnabled(player.getUniqueId())) {
            sender.sendMessage(ChatColor.YELLOW + "Dynamic lights are already disabled for you.");
        } else {
            preferences.setEnabled(player.getUniqueId(), false);
            // Clear any existing lights for this player
            renderer.clearPlayer(player);
            sender.sendMessage(ChatColor.YELLOW + "Dynamic lights disabled.");
        }
    }

    private void handleInfo(CommandSender sender) {
        if (!sender.hasPermission("dynlight.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        int totalSources = sourceManager.getTotalLightSourceCount();
        Set<UUID> disabledPlayers = preferences.getDisabledPlayers();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        // Count online players with lights enabled/disabled
        int enabledCount = 0;
        int disabledCount = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (preferences.isEnabled(player.getUniqueId())) {
                enabledCount++;
            } else {
                disabledCount++;
            }
        }

        sender.sendMessage(ChatColor.GOLD + "=== DynLight Info ===");
        sender.sendMessage(ChatColor.YELLOW + "Active light sources: " + ChatColor.WHITE + totalSources);
        sender.sendMessage(ChatColor.YELLOW + "Online players: " + ChatColor.WHITE + onlinePlayers +
                ChatColor.GRAY + " (" + ChatColor.GREEN + enabledCount + " enabled" +
                ChatColor.GRAY + ", " + ChatColor.RED + disabledCount + " disabled" + ChatColor.GRAY + ")");

        if (!disabledPlayers.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Players with lights disabled:");
            for (UUID uuid : disabledPlayers) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                String name = offlinePlayer.getName();
                boolean online = offlinePlayer.isOnline();
                sender.sendMessage(ChatColor.GRAY + "  - " +
                        (online ? ChatColor.WHITE : ChatColor.DARK_GRAY) +
                        (name != null ? name : uuid.toString()) +
                        (online ? "" : ChatColor.DARK_GRAY + " (offline)"));
            }
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("dynlight.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        try {
            // Reload config
            plugin.reloadConfig();

            // Clear all player light states
            renderer.clearAllPlayers();

            // Trigger callback to reinitialize components
            reloadCallback.run();

            sender.sendMessage(ChatColor.GREEN + "DynLight configuration reloaded!");
            plugin.getLogger().info("Configuration reloaded by " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading configuration: " + e.getMessage());
            plugin.getLogger().severe("Error reloading config: " + e.getMessage());
        }
    }

    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("dynlight.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        DynLightConfig config = configSupplier.get();
        int totalSources = sourceManager.getTotalLightSourceCount();
        java.util.Map<String, Integer> worldCounts = sourceManager.getLightSourceCountsByWorld();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        sender.sendMessage(ChatColor.GOLD + "=== DynLight Stats ===");
        sender.sendMessage(ChatColor.YELLOW + "Light sources: " + ChatColor.WHITE + totalSources);
        if (worldCounts.size() > 1) {
            for (java.util.Map.Entry<String, Integer> entry : worldCounts.entrySet()) {
                sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " + ChatColor.WHITE + entry.getValue());
            }
        }
        sender.sendMessage(ChatColor.YELLOW + "Online players: " + ChatColor.WHITE + onlinePlayers);
        sender.sendMessage(ChatColor.YELLOW + "Update interval: " + ChatColor.WHITE + config.updateInterval + " ticks");
        sender.sendMessage(ChatColor.YELLOW + "Render distance: " + ChatColor.WHITE + config.renderDistance + " blocks");
        sender.sendMessage(ChatColor.YELLOW + "Debug mode: " + ChatColor.WHITE + (debugEnabled ? "ON" : "OFF"));

        // Feature toggles
        sender.sendMessage(ChatColor.GOLD + "=== Features ===");
        sender.sendMessage(formatToggle("Held items", config.heldItemsEnabled));
        sender.sendMessage(formatToggle("Dropped items", config.droppedItemsEnabled));
        sender.sendMessage(formatToggle("Burning entities", config.burningEntitiesEnabled));
        sender.sendMessage(formatToggle("Flaming arrows", config.flamingArrowsEnabled));
        sender.sendMessage(formatToggle("Enchanted armor", config.enchantedArmorEnabled));
        sender.sendMessage(formatToggle("Enchanted items", config.enchantedItemsEnabled));
        sender.sendMessage(formatToggle("Always-lit entities", config.alwaysLitEntitiesEnabled));
    }

    private String formatToggle(String name, boolean enabled) {
        return ChatColor.GRAY + "  " + name + ": " +
                (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF");
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynlight.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Debug mode is currently: " +
                    (debugEnabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            sender.sendMessage(ChatColor.GRAY + "Usage: /dynlight debug <on|off>");
            return;
        }

        String toggle = args[1].toLowerCase();
        if (toggle.equals("on")) {
            debugEnabled = true;
            sender.sendMessage(ChatColor.GREEN + "Debug mode enabled.");
        } else if (toggle.equals("off")) {
            debugEnabled = false;
            sender.sendMessage(ChatColor.YELLOW + "Debug mode disabled.");
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /dynlight debug <on|off>");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== DynLight Commands ===");

        // Show user commands if they have permission
        if (sender.hasPermission("dynlight.use")) {
            sender.sendMessage(ChatColor.YELLOW + "/dynlight enable " + ChatColor.GRAY + "- Enable dynamic lights for yourself");
            sender.sendMessage(ChatColor.YELLOW + "/dynlight disable " + ChatColor.GRAY + "- Disable dynamic lights for yourself");
        }

        // Show admin commands if they have permission
        if (sender.hasPermission("dynlight.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/dynlight info " + ChatColor.GRAY + "- Show light info and player status");
            sender.sendMessage(ChatColor.YELLOW + "/dynlight reload " + ChatColor.GRAY + "- Reload configuration");
            sender.sendMessage(ChatColor.YELLOW + "/dynlight stats " + ChatColor.GRAY + "- Show plugin statistics");
            sender.sendMessage(ChatColor.YELLOW + "/dynlight debug <on|off> " + ChatColor.GRAY + "- Toggle debug mode");
        }

        // If they have no permissions at all, show a message
        if (!sender.hasPermission("dynlight.use") && !sender.hasPermission("dynlight.admin")) {
            sender.sendMessage(ChatColor.GRAY + "You don't have permission to use any DynLight commands.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Add user commands if they have permission
            if (sender.hasPermission("dynlight.use")) {
                for (String sub : List.of("enable", "disable")) {
                    if (sub.startsWith(partial)) {
                        completions.add(sub);
                    }
                }
            }

            // Add admin commands if they have permission
            if (sender.hasPermission("dynlight.admin")) {
                for (String sub : List.of("info", "reload", "stats", "debug")) {
                    if (sub.startsWith(partial)) {
                        completions.add(sub);
                    }
                }
            }

            // Always show help
            if ("help".startsWith(partial)) {
                completions.add("help");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            if (sender.hasPermission("dynlight.admin")) {
                String partial = args[1].toLowerCase();
                for (String opt : List.of("on", "off")) {
                    if (opt.startsWith(partial)) {
                        completions.add(opt);
                    }
                }
            }
        }

        return completions;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }
}
