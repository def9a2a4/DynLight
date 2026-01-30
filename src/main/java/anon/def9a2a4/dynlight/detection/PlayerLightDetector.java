package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.EntityLightConfig;
import anon.def9a2a4.dynlight.detection.cache.PlayerEquipmentCache;
import anon.def9a2a4.dynlight.detection.util.FireStateUtil;
import anon.def9a2a4.dynlight.engine.data.LightSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Detects player light sources: held items, enchanted items, enchanted armor, fire.
 * Players are still polled each tick since there's no reliable event for held item changes.
 * Enchanted armor state is cached via PlayerEquipmentCache to reduce enchantment checks.
 */
public class PlayerLightDetector {

    private final DynLightConfig config;
    private final Map<Material, Integer> itemLightLevels;
    private final PlayerEquipmentCache equipmentCache;

    public PlayerLightDetector(DynLightConfig config, PlayerEquipmentCache equipmentCache) {
        this.config = config;
        this.itemLightLevels = config.itemLightLevels;
        this.equipmentCache = equipmentCache;
    }

    /**
     * Capture snapshots of all player light sources.
     * Iterates players directly instead of worlds to skip empty worlds.
     * Called on main thread, returns immutable data for async processing.
     *
     * @return List of light source snapshots for players
     */
    public List<LightSnapshot> capturePlayerLights() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        List<LightSnapshot> snapshots = new ArrayList<>(onlinePlayers.size());

        for (Player player : onlinePlayers) {
            capturePlayer(player, snapshots);
        }

        return snapshots;
    }

    private void capturePlayer(Player player, List<LightSnapshot> snapshots) {
        // Use direct accessors to avoid Location object allocation
        World world = player.getWorld();
        if (world == null) {
            return; // Player in invalid state
        }

        int level = 0;

        // Single inventory pass for both held items and armor
        if (config.heldItemsEnabled || config.enchantedItemsEnabled || config.enchantedArmorEnabled) {
            level = Math.max(level, getInventoryLight(player));
        }

        // Player on fire or base light
        EntityLightConfig entityConfig = config.getEntityConfig(player.getType());
        boolean isPlayerOnFire = FireStateUtil.isOnFire(player);
        if (config.burningEntitiesEnabled || config.alwaysLitEntitiesEnabled) {
            int entityLight = 0;
            if (config.alwaysLitEntitiesEnabled) {
                entityLight = entityConfig.baseLight();
            }
            if (config.burningEntitiesEnabled && isPlayerOnFire) {
                entityLight = Math.max(entityLight, entityConfig.fireLight());
            }
            level = Math.max(level, entityLight);
        }

        if (level > 0) {
            snapshots.add(new LightSnapshot(
                    player.getUniqueId(),
                    player.getType(),
                    world.getName(),
                    player.getX(), player.getY(), player.getZ(),
                    level
            ));
        }
    }

    /**
     * Single-pass inventory check for held items and armor.
     * Gets inventory once and checks all relevant slots.
     * Armor enchantment state is retrieved from cache (updated on inventory events).
     */
    private int getInventoryLight(Player player) {
        PlayerInventory inv = player.getInventory();
        int level = 0;

        // Check held items
        if (config.heldItemsEnabled || config.enchantedItemsEnabled) {
            level = Math.max(level, getItemLight(inv.getItemInMainHand()));
            level = Math.max(level, getItemLight(inv.getItemInOffHand()));
        }

        // Check armor for enchantments (cached - no per-tick enchantment checks)
        if (config.enchantedArmorEnabled && equipmentCache.hasEnchantedArmor(player.getUniqueId())) {
            level = Math.max(level, config.enchantedArmorLightLevel);
        }

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
        if (config.enchantedItemsEnabled) {
            // Cache ItemMeta to avoid race condition between hasItemMeta() and getItemMeta()
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasEnchants()) {
                level = Math.max(level, config.enchantedItemsLightLevel);
            }
        }

        return level;
    }
}
