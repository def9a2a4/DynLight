package anon.def9a2a4.dynlight;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/**
 * Detects built-in light sources: held items, dropped items, burning entities,
 * always-lit entities, enchanted armor/items.
 */
public class BuiltInDetector {

    private final LightSourceManager manager;
    private final DynLightConfig config;
    private final Map<Material, Integer> itemLightLevels;

    public BuiltInDetector(LightSourceManager manager, DynLightConfig config) {
        this.manager = manager;
        this.config = config;
        this.itemLightLevels = config.itemLightLevels;
    }

    /**
     * Detect all built-in light sources across all worlds.
     * Called each tick cycle.
     */
    public void detectBuiltInSources() {
        manager.clearBuiltInSources();

        for (World world : Bukkit.getWorlds()) {
            detectInWorld(world);
        }
    }

    private void detectInWorld(World world) {
        // Players: held items, enchanted items, and enchanted armor
        for (Player player : world.getPlayers()) {
            int level = 0;

            // Held items (check both light-emitting and enchanted)
            if (config.heldItemsEnabled || config.enchantedItemsEnabled) {
                level = Math.max(level, getHeldItemLight(player));
            }

            // Enchanted armor
            if (config.enchantedArmorEnabled && hasEnchantedArmor(player)) {
                level = Math.max(level, config.enchantedArmorLightLevel);
            }

            // Player on fire or base light
            EntityLightConfig entityConfig = config.getEntityConfig(player.getType());
            boolean isOnFire = player.getFireTicks() > 0 || player.isVisualFire();
            if (config.burningEntitiesEnabled || config.alwaysLitEntitiesEnabled) {
                int entityLight = 0;
                if (config.alwaysLitEntitiesEnabled) {
                    entityLight = entityConfig.baseLight();
                }
                if (config.burningEntitiesEnabled && isOnFire) {
                    entityLight = Math.max(entityLight, entityConfig.fireLight());
                }
                level = Math.max(level, entityLight);
            }

            if (level > 0) {
                manager.setBuiltInSource(player.getUniqueId(), level);
            }
        }

        // Dropped items
        if (config.droppedItemsEnabled) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                Integer level = itemLightLevels.get(item.getItemStack().getType());
                if (level != null) {
                    manager.setBuiltInSource(item.getUniqueId(), level);
                }
            }
        }

        // All other entities: check for fire and base light
        for (Entity entity : world.getEntities()) {
            // Skip players (handled above) and arrows (handled separately)
            if (entity instanceof Player || entity instanceof Arrow || entity instanceof Item) {
                continue;
            }

            EntityLightConfig entityConfig = config.getEntityConfig(entity.getType());
            boolean isOnFire = entity.getFireTicks() > 0 || entity.isVisualFire();

            int level = 0;

            // Always-lit entities (base light)
            if (config.alwaysLitEntitiesEnabled && entityConfig.baseLight() > 0) {
                level = entityConfig.baseLight();
            }

            // Burning entities
            if (config.burningEntitiesEnabled && isOnFire) {
                level = Math.max(level, entityConfig.fireLight());
            }

            if (level > 0) {
                // Don't override if already has a higher light level (from API)
                int currentLevel = manager.getLightLevel(entity);
                if (currentLevel < level) {
                    manager.setBuiltInSource(entity.getUniqueId(), level);
                }
            }
        }

        // Flaming arrows (separate toggle)
        if (config.flamingArrowsEnabled) {
            for (Arrow arrow : world.getEntitiesByClass(Arrow.class)) {
                if (arrow.getFireTicks() > 0) {
                    EntityLightConfig arrowConfig = config.getEntityConfig(arrow.getType());
                    manager.setBuiltInSource(arrow.getUniqueId(), arrowConfig.fireLight());
                }
            }
        }
    }

    private int getHeldItemLight(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        int level = 0;
        level = Math.max(level, getItemLight(mainHand));
        level = Math.max(level, getItemLight(offHand));
        return level;
    }

    private int getItemLight(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }

        int level = 0;

        // Light from item type
        if (config.heldItemsEnabled) {
            level = Math.max(level, itemLightLevels.getOrDefault(item.getType(), 0));
        }

        // Light from enchantments (if item is enchanted)
        if (config.enchantedItemsEnabled && !item.getEnchantments().isEmpty()) {
            level = Math.max(level, config.enchantedItemsLightLevel);
        }

        return level;
    }

    private boolean hasEnchantedArmor(Player player) {
        PlayerInventory inv = player.getInventory();
        return isEnchanted(inv.getHelmet())
                || isEnchanted(inv.getChestplate())
                || isEnchanted(inv.getLeggings())
                || isEnchanted(inv.getBoots());
    }

    private boolean isEnchanted(ItemStack item) {
        return item != null && !item.getEnchantments().isEmpty();
    }
}
