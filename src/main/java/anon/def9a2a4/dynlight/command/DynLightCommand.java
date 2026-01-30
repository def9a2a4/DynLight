package anon.def9a2a4.dynlight.command;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.LightSourceManager;
import anon.def9a2a4.dynlight.LightRenderer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Command handler for /dynlight admin commands.
 * Provides reload, stats, and debug subcommands.
 */
public class DynLightCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final Supplier<DynLightConfig> configSupplier;
    private final LightSourceManager sourceManager;
    private final LightRenderer renderer;
    private final Runnable reloadCallback;

    private boolean debugEnabled = false;

    public DynLightCommand(JavaPlugin plugin, Supplier<DynLightConfig> configSupplier,
                           LightSourceManager sourceManager, LightRenderer renderer,
                           Runnable reloadCallback) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.sourceManager = sourceManager;
        this.renderer = renderer;
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
            case "reload" -> handleReload(sender);
            case "stats" -> handleStats(sender);
            case "debug" -> handleDebug(sender, args);
            default -> sendHelp(sender);
        }

        return true;
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
        int totalSources = sourceManager.getAllLightSources().size();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        sender.sendMessage(ChatColor.GOLD + "=== DynLight Stats ===");
        sender.sendMessage(ChatColor.YELLOW + "Light sources: " + ChatColor.WHITE + totalSources);
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
        sender.sendMessage(ChatColor.YELLOW + "/dynlight reload " + ChatColor.GRAY + "- Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/dynlight stats " + ChatColor.GRAY + "- Show plugin statistics");
        sender.sendMessage(ChatColor.YELLOW + "/dynlight debug <on|off> " + ChatColor.GRAY + "- Toggle debug mode");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("reload", "stats", "debug")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            String partial = args[1].toLowerCase();
            for (String opt : List.of("on", "off")) {
                if (opt.startsWith(partial)) {
                    completions.add(opt);
                }
            }
        }

        return completions;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }
}
