package anon.def9a2a4.dynlight.engine.command;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.Messages;
import anon.def9a2a4.dynlight.engine.LightSourceManager;
import anon.def9a2a4.dynlight.engine.LightRenderer;
import anon.def9a2a4.dynlight.engine.PlayerPreferences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static anon.def9a2a4.dynlight.Messages.arg;

/**
 * Command handler for /dynlight commands.
 * Provides help, enable, disable, info, reload, regen, and stats subcommands.
 * All player-facing text comes from messages.yml.
 */
public class DynLightCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final Supplier<DynLightConfig> configSupplier;
    private final LightSourceManager sourceManager;
    private final LightRenderer renderer;
    private final PlayerPreferences preferences;
    private final Messages messages;
    private final Runnable reloadCallback;
    private final Runnable regenCallback;

    private static final String GITHUB_URL = "https://github.com/def9a2a4/DynLight";
    private static final String DISCORD_URL = "https://def9a2a4.github.io/discord.html";

    public DynLightCommand(JavaPlugin plugin, Supplier<DynLightConfig> configSupplier,
                           LightSourceManager sourceManager, LightRenderer renderer,
                           PlayerPreferences preferences, Messages messages,
                           Runnable reloadCallback, Runnable regenCallback) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.sourceManager = sourceManager;
        this.renderer = renderer;
        this.preferences = preferences;
        this.messages = messages;
        this.reloadCallback = reloadCallback;
        this.regenCallback = regenCallback;
    }

    private boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            messages.send(sender, "general.players-only");
            return false;
        }
        return true;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "general.no-permission");
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);

        switch (subcommand) {
            case "help" -> sendHelp(sender);
            case "enable" -> handleToggle(sender, args, true);
            case "disable" -> handleToggle(sender, args, false);
            case "info" -> handleInfo(sender);
            case "reload" -> handleReload(sender);
            case "regen" -> handleRegen(sender);
            case "stats" -> handleStats(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    /** /dynlight enable|disable [player] */
    private void handleToggle(CommandSender sender, String[] args, boolean enable) {
        if (args.length >= 2) {
            handleToggleOther(sender, args[1], enable);
            return;
        }
        if (!requirePlayer(sender) || !requirePermission(sender, "dynlight.use")) return;
        Player player = (Player) sender;

        if (preferences.isEnabled(player.getUniqueId()) == enable) {
            messages.send(sender, enable ? "toggle.already-enabled" : "toggle.already-disabled");
            return;
        }
        setEnabled(player, enable);
        messages.send(sender, enable ? "toggle.enabled" : "toggle.disabled");
    }

    private void handleToggleOther(CommandSender sender, String targetName, boolean enable) {
        if (!requirePermission(sender, "dynlight.others")) return;

        // Offline players work too: preferences are stored by UUID
        OfflinePlayer target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(targetName);
        }
        if (target == null) {
            messages.send(sender, "general.player-not-found", arg("player", targetName));
            return;
        }
        String name = target.getName() != null ? target.getName() : targetName;

        if (preferences.isEnabled(target.getUniqueId()) == enable) {
            messages.send(sender, enable ? "toggle.already-enabled-other" : "toggle.already-disabled-other",
                    arg("player", name));
            return;
        }
        setEnabled(target, enable);
        messages.send(sender, enable ? "toggle.enabled-other" : "toggle.disabled-other", arg("player", name));

        Player online = target.getPlayer();
        if (online != null && online != sender) {
            messages.send(online, enable ? "toggle.enabled-by" : "toggle.disabled-by", arg("sender", sender.getName()));
        }
    }

    private void setEnabled(OfflinePlayer player, boolean enable) {
        preferences.setEnabled(player.getUniqueId(), enable);
        Player online = player.getPlayer();
        if (!enable && online != null) {
            // Clear any existing lights for this player
            renderer.clearPlayer(online);
        }
    }

    private void handleInfo(CommandSender sender) {
        if (!requirePermission(sender, "dynlight.admin")) return;

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

        messages.send(sender, "info.header");
        messages.send(sender, "info.sources", arg("count", totalSources));
        messages.send(sender, "info.players", arg("online", onlinePlayers),
                arg("enabled", enabledCount), arg("disabled", disabledCount));

        if (!disabledPlayers.isEmpty()) {
            messages.send(sender, "info.disabled-header");
            for (UUID uuid : disabledPlayers) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                String name = offlinePlayer.getName();
                messages.send(sender, offlinePlayer.isOnline() ? "info.disabled-online" : "info.disabled-offline",
                        arg("player", name != null ? name : uuid.toString()));
            }
        }
    }

    private void handleReload(CommandSender sender) {
        if (!requirePermission(sender, "dynlight.admin")) return;

        try {
            reloadCallback.run();

            messages.send(sender, "reload.success");
            plugin.getLogger().info("Configuration reloaded by " + sender.getName());
        } catch (Exception e) {
            messages.send(sender, "reload.error", arg("error", e.getMessage()));
            plugin.getLogger().severe("Error reloading config: " + e.getMessage());
        }
    }

    private void handleRegen(CommandSender sender) {
        if (!requirePermission(sender, "dynlight.admin")) return;

        try {
            regenCallback.run();

            int totalSources = sourceManager.getTotalLightSourceCount();
            messages.send(sender, "regen.success", arg("count", totalSources));
            plugin.getLogger().info("Light sources regenerated by " + sender.getName());
        } catch (Exception e) {
            messages.send(sender, "regen.error", arg("error", e.getMessage()));
            plugin.getLogger().severe("Error regenerating light sources: " + e.getMessage());
        }
    }

    private void handleStats(CommandSender sender) {
        if (!requirePermission(sender, "dynlight.admin")) return;

        DynLightConfig config = configSupplier.get();
        int totalSources = sourceManager.getTotalLightSourceCount();
        Map<String, Integer> worldCounts = sourceManager.getLightSourceCountsByWorld();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        messages.send(sender, "stats.header");
        messages.send(sender, "stats.sources", arg("count", totalSources));
        if (worldCounts.size() > 1) {
            for (Map.Entry<String, Integer> entry : worldCounts.entrySet()) {
                messages.send(sender, "stats.world", arg("world", entry.getKey()), arg("count", entry.getValue()));
            }
        }
        messages.send(sender, "stats.players", arg("online", onlinePlayers));
        messages.send(sender, "stats.update-interval", arg("ticks", config.updateInterval));
        messages.send(sender, "stats.render-distance", arg("blocks", config.renderDistance));

        // Feature toggles
        messages.send(sender, "stats.features-header");
        sendToggle(sender, "held-items", config.heldItemsEnabled);
        sendToggle(sender, "dropped-items", config.droppedItemsEnabled);
        sendToggle(sender, "burning-entities", config.burningEntitiesEnabled);
        sendToggle(sender, "flaming-arrows", config.flamingArrowsEnabled);
        sendToggle(sender, "enchanted-armor", config.enchantedArmorEnabled);
        sendToggle(sender, "enchanted-items", config.enchantedItemsEnabled);
        sendToggle(sender, "always-lit-entities", config.alwaysLitEntitiesEnabled);
    }

    private void sendToggle(CommandSender sender, String feature, boolean enabled) {
        messages.send(sender, enabled ? "stats.feature-on" : "stats.feature-off",
                Placeholder.component("feature", messages.get("stats.features." + feature)));
    }

    private void sendHelp(CommandSender sender) {
        messages.send(sender, "help.header", arg("version", plugin.getPluginMeta().getVersion()));
        messages.send(sender, "help.description");
        messages.send(sender, "help.help");

        // Show user commands if they have permission
        if (sender.hasPermission("dynlight.use")) {
            messages.send(sender, "help.enable");
            messages.send(sender, "help.disable");
        }

        if (sender.hasPermission("dynlight.others")) {
            messages.send(sender, "help.enable-other");
            messages.send(sender, "help.disable-other");
        }

        // Show admin commands if they have permission
        if (sender.hasPermission("dynlight.admin")) {
            messages.send(sender, "help.info");
            messages.send(sender, "help.reload");
            messages.send(sender, "help.regen");
            messages.send(sender, "help.stats");
        }

        // If they have no permissions at all, show a message
        if (!sender.hasPermission("dynlight.use") && !sender.hasPermission("dynlight.others")
                && !sender.hasPermission("dynlight.admin")) {
            messages.send(sender, "help.no-commands");
        }

        sender.sendMessage(link("GitHub", GITHUB_URL));
        sender.sendMessage(link("Discord", DISCORD_URL));
    }

    private static Component link(String label, String url) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(url, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);

            // Add user commands if they have permission
            if (sender.hasPermission("dynlight.use") || sender.hasPermission("dynlight.others")) {
                for (String sub : List.of("enable", "disable")) {
                    if (sub.startsWith(partial)) {
                        completions.add(sub);
                    }
                }
            }

            // Add admin commands if they have permission
            if (sender.hasPermission("dynlight.admin")) {
                for (String sub : List.of("info", "reload", "regen", "stats")) {
                    if (sub.startsWith(partial)) {
                        completions.add(sub);
                    }
                }
            }

            // Always show help
            if ("help".startsWith(partial)) {
                completions.add("help");
            }
        } else if (args.length == 2 && sender.hasPermission("dynlight.others")
                && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean visible = !(sender instanceof Player viewer) || viewer.canSee(player);
                if (visible && player.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
