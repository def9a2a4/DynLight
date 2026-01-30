package anon.def9a2a4.dynlight.detection;

import anon.def9a2a4.dynlight.DynLightConfig;
import anon.def9a2a4.dynlight.api.DynLightAPI;
import anon.def9a2a4.dynlight.api.EntityLightDetector;
import anon.def9a2a4.dynlight.api.LightSourceInfo;
import anon.def9a2a4.dynlight.detection.util.WaterUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Event-driven listener for dropped item light sources.
 * Registers light sources when items spawn and removes them on pickup/despawn.
 * Also provides a detector for chunk-load scanning.
 */
public class ItemLightListener implements Listener, EntityLightDetector {

    private final DynLightConfig config;
    private final DynLightAPI api;

    public ItemLightListener(DynLightConfig config, DynLightAPI api) {
        this.config = config;
        this.api = api;
        // Register this listener as a detector for chunk-load scanning
        api.registerDetector(this);
    }

    @Override
    public LightSourceInfo detect(Entity entity) {
        if (!config.droppedItemsEnabled) {
            return null;
        }
        if (!(entity instanceof Item item)) {
            return null;
        }
        boolean isUnderwater = WaterUtil.isUnderwater(item);
        int level = calculateLightLevel(item.getItemStack(), isUnderwater);
        return level > 0 ? LightSourceInfo.of(level) : null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        // Use detector for immediate registration on spawn
        LightSourceInfo info = detect(event.getEntity());
        if (info != null) {
            api.addLightSource(event.getEntity(), info);
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
        int level = config.getItemLightLevel(stack.getType(), isUnderwater);

        // Check for enchantments (weak glow) - not water-sensitive
        if (config.enchantedItemsEnabled) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && (meta.hasEnchants()
                    || (meta instanceof EnchantmentStorageMeta esm && esm.hasStoredEnchants()))) {
                level = Math.max(level, config.enchantedItemsLightLevel);
            }
        }

        return level;
    }
}
