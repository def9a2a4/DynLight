package anon.def9a2a4.dynlight;

import anon.def9a2a4.dynlight.engine.PlayerPreferences;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * PlaceholderAPI placeholders:
 *   %dynlight_status%  - "enabled"/"disabled" text from messages.yml (placeholder.*)
 *   %dynlight_enabled% - true/false
 *
 * Only load this class when PlaceholderAPI is installed: it extends a PlaceholderAPI type.
 */
public class DynLightExpansion extends PlaceholderExpansion {

    // Section-sign legacy text with hex colours (the plain legacySection() serializer drops them)
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('\u00a7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final JavaPlugin plugin;
    private final PlayerPreferences preferences;
    private final Messages messages;

    private DynLightExpansion(JavaPlugin plugin, PlayerPreferences preferences, Messages messages) {
        this.plugin = plugin;
        this.preferences = preferences;
        this.messages = messages;
    }

    public static void register(JavaPlugin plugin, PlayerPreferences preferences, Messages messages) {
        new DynLightExpansion(plugin, preferences, messages).register();
    }

    @Override
    public String getIdentifier() {
        return "dynlight";
    }

    @Override
    public String getAuthor() {
        return "def9a2a4";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        // Owned by DynLight, so it must survive /papi reload
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        boolean enabled = preferences.isEnabled(player.getUniqueId());

        return switch (params.toLowerCase(Locale.ROOT)) {
            case "status" -> LEGACY.serialize(
                    messages.get(enabled ? "placeholder.enabled" : "placeholder.disabled"));
            case "enabled" -> String.valueOf(enabled);
            default -> null;
        };
    }
}
