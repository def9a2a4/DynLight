package anon.def9a2a4.dynlight.detection.cache;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches player equipment state to avoid checking enchantments every tick.
 * Updates cache on inventory events instead of polling.
 */
public class PlayerEquipmentCache implements Listener {

    private final Plugin plugin;

    // Player UUID -> has enchanted armor
    private final Map<UUID, Boolean> enchantedArmorCache = new ConcurrentHashMap<>();

    // Pending updates batched into a single task
    private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();
    private volatile BukkitTask batchTask;

    public PlayerEquipmentCache(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if a player has enchanted armor (cached).
     *
     * @param playerId The player's UUID
     * @return true if player has at least one enchanted armor piece
     */
    public boolean hasEnchantedArmor(UUID playerId) {
        return enchantedArmorCache.getOrDefault(playerId, false);
    }

    /**
     * Recalculate and cache the enchanted armor state for a player.
     *
     * @param player The player to update
     */
    public void updatePlayer(Player player) {
        boolean hasEnchanted = calculateHasEnchantedArmor(player);
        enchantedArmorCache.put(player.getUniqueId(), hasEnchanted);
    }

    /**
     * Remove a player from the cache.
     *
     * @param playerId The player's UUID
     */
    public void removePlayer(UUID playerId) {
        enchantedArmorCache.remove(playerId);
    }

    private boolean calculateHasEnchantedArmor(Player player) {
        PlayerInventory inv = player.getInventory();
        return isEnchanted(inv.getHelmet())
                || isEnchanted(inv.getChestplate())
                || isEnchanted(inv.getLeggings())
                || isEnchanted(inv.getBoots());
    }

    private boolean isEnchanted(ItemStack item) {
        if (item == null) {
            return false;
        }
        // Cache ItemMeta to avoid race condition between hasItemMeta() and getItemMeta()
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasEnchants();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Check if the click affects armor slots
        int slot = event.getRawSlot();
        InventoryType.SlotType slotType = event.getSlotType();

        // Armor slots are 36-39 in player inventory, or SlotType.ARMOR
        boolean isArmorSlot = slotType == InventoryType.SlotType.ARMOR
                || (slot >= 36 && slot <= 39 && event.getClickedInventory() instanceof PlayerInventory);

        // Also check for shift-click which could move items to armor slots
        boolean isShiftClick = event.isShiftClick();

        if (isArmorSlot || isShiftClick) {
            // Batch updates: add to pending set, schedule single task if not already scheduled
            scheduleUpdate(player.getUniqueId());
        }
    }

    /**
     * Schedule a batched update for a player. Multiple rapid clicks are coalesced into one update.
     */
    private void scheduleUpdate(UUID playerId) {
        pendingUpdates.add(playerId);

        // Only schedule a new task if one isn't already pending
        if (batchTask == null || batchTask.isCancelled()) {
            batchTask = plugin.getServer().getScheduler().runTask(plugin, this::processPendingUpdates);
        }
    }

    private void processPendingUpdates() {
        batchTask = null;
        for (UUID playerId : pendingUpdates) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                updatePlayer(player);
            }
        }
        pendingUpdates.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updatePlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer().getUniqueId());
    }
}
