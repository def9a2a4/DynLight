package anon.def9a2a4.dynlight;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration wrapper for DynLight settings.
 * Loads all config values once at construction time.
 */
public class DynLightConfig {

    public final int updateInterval;
    public final int renderDistance;
    public final int fireSweepInterval;

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

    // Projectile trail settings
    public final int projectileTrailLength;
    public final double projectileTrailSpacing;

    // Item light configurations
    public final Map<Material, ItemLightConfig> itemConfigs;

    // Entity configurations (fire, base light, radius)
    private final EntityLightConfig defaultEntityConfig;
    private final Map<EntityType, EntityLightConfig> entityConfigs;

    public DynLightConfig(FileConfiguration config) {
        // General settings (with bounds validation)
        int rawInterval = config.getInt("update-interval", 4);
        if (rawInterval < 1) {
            Bukkit.getLogger().warning("[DynLight] update-interval value " + rawInterval + " is below minimum, using 1");
        }
        this.updateInterval = Math.max(1, rawInterval);
        this.renderDistance = clampWithWarning(config.getInt("render-distance", 64), 8, 256, "render-distance");
        this.fireSweepInterval = clampWithWarning(config.getInt("fire-sweep-interval", 10), 1, 100, "fire-sweep-interval");

        // Detection toggles
        this.heldItemsEnabled = config.getBoolean("detection.held-items", true);
        this.droppedItemsEnabled = config.getBoolean("detection.dropped-items", true);
        this.burningEntitiesEnabled = config.getBoolean("detection.burning-entities", true);
        this.flamingArrowsEnabled = config.getBoolean("detection.flaming-arrows", true);
        this.enchantedArmorEnabled = config.getBoolean("detection.enchanted-armor", true);
        this.enchantedItemsEnabled = config.getBoolean("detection.enchanted-items", true);
        this.alwaysLitEntitiesEnabled = config.getBoolean("detection.always-lit-entities", true);

        // Light levels for player equipment (clamped to 0-15)
        this.enchantedArmorLightLevel = clampWithWarning(config.getInt("light-levels.enchanted-armor", 10), 0, 15, "light-levels.enchanted-armor");
        this.enchantedItemsLightLevel = clampWithWarning(config.getInt("light-levels.enchanted-items", 8), 0, 15, "light-levels.enchanted-items");

        // Projectile trail settings (0 = disabled)
        this.projectileTrailLength = clampWithWarning(config.getInt("projectile-trail.length", 3), 0, 10, "projectile-trail.length");
        this.projectileTrailSpacing = clampDoubleWithWarning(config.getDouble("projectile-trail.spacing", 1.0), 0.5, 5.0, "projectile-trail.spacing");

        // Item light configurations
        this.itemConfigs = loadItemConfigs(config);

        // Entity configurations
        this.defaultEntityConfig = loadEntityConfig(
                config.getConfigurationSection("entities.default"),
                EntityLightConfig.DEFAULT,
                "default"
        );
        this.entityConfigs = loadEntityConfigs(config);
    }

    private Map<Material, ItemLightConfig> loadItemConfigs(FileConfiguration config) {
        Map<Material, ItemLightConfig> configs = new EnumMap<>(Material.class);

        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());

                    if (items.isInt(key)) {
                        // Simple format: TORCH: 10 (backwards compatible, not water-sensitive)
                        int level = items.getInt(key);
                        if (level > 0 && level <= 15) {
                            configs.put(material, ItemLightConfig.simple(level));
                        } else if (level < 0 || level > 15) {
                            Bukkit.getLogger().warning("[DynLight] Item " + key + " has invalid light level: " + level + " (must be 0-15)");
                        }
                        // level == 0 silently skips (disables the item)
                    } else if (items.isConfigurationSection(key)) {
                        // Extended format: TORCH: {light: 10, water-sensitive: true}
                        ConfigurationSection itemSection = items.getConfigurationSection(key);
                        int level = itemSection.getInt("light", 0);
                        boolean waterSensitive = itemSection.getBoolean("water-sensitive", false);

                        if (level > 0 && level <= 15) {
                            configs.put(material, new ItemLightConfig(level, waterSensitive));
                        } else if (level < 0 || level > 15) {
                            Bukkit.getLogger().warning("[DynLight] Item " + key + " has invalid light level: " + level + " (must be 0-15)");
                        }
                    } else {
                        Bukkit.getLogger().warning("[DynLight] Invalid item config format for: " + key);
                    }
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().warning("[DynLight] Invalid material in config: " + key);
                }
            }
        }

        // Wrap in unmodifiable to prevent external modification
        return Collections.unmodifiableMap(configs);
    }

    /**
     * Get light level for a material, accounting for water sensitivity.
     * Returns 0 if the item is water-sensitive and underwater.
     */
    public int getItemLightLevel(Material material, boolean isUnderwater) {
        ItemLightConfig config = itemConfigs.get(material);
        if (config == null) {
            return 0;
        }
        if (isUnderwater && config.waterSensitive()) {
            return 0;
        }
        return config.lightLevel();
    }

    /**
     * Get the base light level for a material (ignoring water sensitivity).
     */
    public int getItemLightLevel(Material material) {
        ItemLightConfig config = itemConfigs.get(material);
        return config != null ? config.lightLevel() : 0;
    }

    private EntityLightConfig loadEntityConfig(ConfigurationSection section, EntityLightConfig fallback, String entityKey) {
        if (section == null) {
            return fallback;
        }
        // Clamp values to valid ranges
        String prefix = "entities." + entityKey + ".";
        int baseLight = clampWithWarning(section.getInt("base-light", fallback.baseLight()), 0, 15, prefix + "base-light");
        int fireLight = clampWithWarning(section.getInt("fire-light", fallback.fireLight()), 0, 15, prefix + "fire-light");
        int horizontalRadius = clampWithWarning(section.getInt("horizontal-radius", fallback.horizontalRadius()), 0, 5, prefix + "horizontal-radius");
        int height = clampWithWarning(section.getInt("height", fallback.height()), 1, 10, prefix + "height");
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
                        configs.put(type, loadEntityConfig(entitySection, defaultEntityConfig, key));
                    }
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().warning("[DynLight] Invalid entity type in config: " + key);
                }
            }
        }

        // Wrap in unmodifiable to prevent external modification
        return Collections.unmodifiableMap(configs);
    }

    public EntityLightConfig getEntityConfig(EntityType entityType) {
        return entityConfigs.getOrDefault(entityType, defaultEntityConfig);
    }

    private int clampWithWarning(int value, int min, int max, String configKey) {
        if (value < min) {
            Bukkit.getLogger().warning("[DynLight] " + configKey + " value " + value + " is below minimum, using " + min);
            return min;
        }
        if (value > max) {
            Bukkit.getLogger().warning("[DynLight] " + configKey + " value " + value + " exceeds maximum, using " + max);
            return max;
        }
        return value;
    }

    private double clampDoubleWithWarning(double value, double min, double max, String configKey) {
        if (value < min) {
            Bukkit.getLogger().warning("[DynLight] " + configKey + " value " + value + " is below minimum, using " + min);
            return min;
        }
        if (value > max) {
            Bukkit.getLogger().warning("[DynLight] " + configKey + " value " + value + " exceeds maximum, using " + max);
            return max;
        }
        return value;
    }
}
