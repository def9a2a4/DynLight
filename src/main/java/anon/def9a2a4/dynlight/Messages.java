package anon.def9a2a4.dynlight;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Player-facing messages, loaded from messages.yml (MiniMessage format).
 * Keys missing from the server's file fall back to the bundled defaults, and an empty message is not sent.
 */
public class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    // volatile: PlaceholderAPI can read messages off the main thread while /dynlight reload swaps them
    private volatile YamlConfiguration messages;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read default messages: " + e.getMessage());
        }
        messages = loaded;
    }

    /** Raw MiniMessage string for a key (the key itself if it's missing everywhere). */
    public String raw(String key) {
        // One-argument getString: the two-argument form skips the bundled defaults
        String value = messages.getString(key);
        return value != null ? value : key;
    }

    public Component get(String key, TagResolver... placeholders) {
        return MINI.deserialize(raw(key), placeholders);
    }

    /** Sends the message, unless it's been set to "". */
    public void send(CommandSender sender, String key, TagResolver... placeholders) {
        String raw = raw(key);
        if (raw.isEmpty()) return;
        sender.sendMessage(MINI.deserialize(raw, placeholders));
    }

    /** A placeholder whose value is inserted as plain text (so player names can't inject formatting). */
    public static TagResolver arg(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }
}
