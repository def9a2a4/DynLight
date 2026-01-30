package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.detection.util.WaterUtil;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Event-driven listener for dropped item light sources.
 * Registers light sources when items spawn and removes them on pickup/despawn.
 */
public class ItemLightListener implements Listener {

    private final DynLightConfig config;
    private final DynLightAPI api;

    public ItemLightListener(DynLightConfig config, DynLightAPI api) {
        this.config = config;
        this.api = api;
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!config.droppedItemsEnabled) {
            return;
        }

        Item item = event.getEntity();
        boolean isUnderwater = WaterUtil.isUnderwater(item);
        int level = calculateLightLevel(item.getItemStack(), isUnderwater);
        if (level > 0) {
            api.addLightSource(item, level);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        api.removeLightSource(event.getItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        api.removeLightSource(event.getEntity());
    }

    private int calculateLightLevel(ItemStack stack, boolean isUnderwater) {
        // Check material-based light first (with water sensitivity)
        int level = config.getItemLightLevel(stack.getType(), isUnderwater);
        if (level > 0) {
            return level;
        }

        // Check for enchantments (weak glow) - not water-sensitive
        if (config.enchantedItemsEnabled) {
            // Cache ItemMeta to avoid race condition between hasItemMeta() and getItemMeta()
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasEnchants()) {
                return config.enchantedItemsLightLevel;
            }
        }

        return 0;
    }
}
