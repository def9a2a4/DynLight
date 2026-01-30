package anon.def9a2a4.dynlight;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Event-driven listener for dropped item light sources.
 * Registers light sources when items spawn and removes them on pickup/despawn.
 */
public class ItemLightListener implements Listener {

    private final DynLightConfig config;
    private final LightSourceManager sourceManager;
    private final Map<Material, Integer> itemLightLevels;

    public ItemLightListener(DynLightConfig config, LightSourceManager sourceManager) {
        this.config = config;
        this.sourceManager = sourceManager;
        this.itemLightLevels = config.itemLightLevels;
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!config.droppedItemsEnabled) {
            return;
        }

        Item item = event.getEntity();
        int level = calculateLightLevel(item.getItemStack());
        if (level > 0) {
            sourceManager.addLightSource(item, level);
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        sourceManager.removeLightSource(event.getItem());
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        sourceManager.removeLightSource(event.getEntity());
    }

    private int calculateLightLevel(ItemStack stack) {
        // Check material-based light first (torch, lantern, etc.)
        Integer level = itemLightLevels.get(stack.getType());
        if (level != null) {
            return level;
        }

        // Check for enchantments (weak glow)
        if (config.enchantedItemsEnabled && !stack.getEnchantments().isEmpty()) {
            return config.enchantedItemsLightLevel;
        }

        return 0;
    }
}
