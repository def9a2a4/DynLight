package anon.def9a2a4.dynlight;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration wrapper for DynLight settings.
 * Loads all config values once at construction time.
 */
public class DynLightConfig {

    public final int updateInterval;
    public final int renderDistance;

    // Detection toggles
    public final boolean heldItemsEnabled;
    public final boolean droppedItemsEnabled;
    public final boolean burningEntitiesEnabled;
    public final boolean flamingArrowsEnabled;
    public final boolean enchantedArmorEnabled;
    public final boolean enchantedItemsEnabled;
    public final boolean alwaysLitEntitiesEnabled;

    // Light levels for player equipment
    public final int enchantedArmorLightLevel;
    public final int enchantedItemsLightLevel;

    // Item light levels
    public final Map<Material, Integer> itemLightLevels;

    // Entity configurations (fire, base light, radius)
    private final EntityLightConfig defaultEntityConfig;
    private final Map<EntityType, EntityLightConfig> entityConfigs;

    public DynLightConfig(FileConfiguration config) {
        // General settings
        this.updateInterval = config.getInt("update-interval", 4);
        this.renderDistance = config.getInt("render-distance", 64);

        // Detection toggles
        this.heldItemsEnabled = config.getBoolean("detection.held-items", true);
        this.droppedItemsEnabled = config.getBoolean("detection.dropped-items", true);
        this.burningEntitiesEnabled = config.getBoolean("detection.burning-entities", true);
        this.flamingArrowsEnabled = config.getBoolean("detection.flaming-arrows", true);
        this.enchantedArmorEnabled = config.getBoolean("detection.enchanted-armor", true);
        this.enchantedItemsEnabled = config.getBoolean("detection.enchanted-items", true);
        this.alwaysLitEntitiesEnabled = config.getBoolean("detection.always-lit-entities", true);

        // Light levels for player equipment
        this.enchantedArmorLightLevel = config.getInt("light-levels.enchanted-armor", 10);
        this.enchantedItemsLightLevel = config.getInt("light-levels.enchanted-items", 8);

        // Item light levels
        this.itemLightLevels = loadItemLightLevels(config);

        // Entity configurations
        this.defaultEntityConfig = loadEntityConfig(
                config.getConfigurationSection("entities.default"),
                EntityLightConfig.DEFAULT
        );
        this.entityConfigs = loadEntityConfigs(config);
    }

    private Map<Material, Integer> loadItemLightLevels(FileConfiguration config) {
        Map<Material, Integer> levels = new EnumMap<>(Material.class);

        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());
                    int level = items.getInt(key);
                    if (level > 0 && level <= 15) {
                        levels.put(material, level);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid material name, skip
                }
            }
        }

        return levels;
    }

    private EntityLightConfig loadEntityConfig(ConfigurationSection section, EntityLightConfig fallback) {
        if (section == null) {
            return fallback;
        }
        int baseLight = section.getInt("base-light", fallback.baseLight());
        int fireLight = section.getInt("fire-light", fallback.fireLight());
        int horizontalRadius = section.getInt("horizontal-radius", fallback.horizontalRadius());
        int height = section.getInt("height", fallback.height());
        return new EntityLightConfig(baseLight, fireLight, horizontalRadius, height);
    }

    private Map<EntityType, EntityLightConfig> loadEntityConfigs(FileConfiguration config) {
        Map<EntityType, EntityLightConfig> configs = new EnumMap<>(EntityType.class);

        ConfigurationSection entitiesSection = config.getConfigurationSection("entities");
        if (entitiesSection != null) {
            for (String key : entitiesSection.getKeys(false)) {
                if (key.equals("default")) continue;
                try {
                    EntityType type = EntityType.valueOf(key.toUpperCase());
                    ConfigurationSection entitySection = entitiesSection.getConfigurationSection(key);
                    if (entitySection != null) {
                        configs.put(type, loadEntityConfig(entitySection, defaultEntityConfig));
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid entity type, skip
                }
            }
        }

        return configs;
    }

    public EntityLightConfig getEntityConfig(EntityType entityType) {
        return entityConfigs.getOrDefault(entityType, defaultEntityConfig);
    }
}
