package net.exylia.exyliaLobby.module.gadgets;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** The events the gadgets module reacts to; every decision lives in {@link GadgetsModule}. */
final class GadgetsListener implements Listener {

    private final GadgetsModule module;

    GadgetsListener(GadgetsModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = module.gadgetOf(event.getItem());
        if (id == null) return;
        if (id.equals("grapple")) {
            if (!module.cast(event.getPlayer())) event.setCancelled(true);
            return;
        }
        // The vanilla use (a thrown pearl or snowball, a placed block) never happens; the gadget does its own.
        event.setCancelled(true);
        module.use(event.getPlayer(), id);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        module.fished(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            if (module.stick(attacker, victim)) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player
                && module.safe(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectile(ProjectileHitEvent event) {
        module.hit(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && module.riding(event.getPlayer())) {
            // The ride ends where the pearl lands; the pearl never moves the rider and never hurts them.
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (module.gadgetOf(event.getItemDrop().getItemStack()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (module.gadgetOf(event.getMainHandItem()) != null || module.gadgetOf(event.getOffHandItem()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        // The number key moves the hotbar item under it, which is neither the clicked item nor the cursor.
        ItemStack keyed = event.getHotbarButton() >= 0 ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        ItemStack offHand = event.getClick() == ClickType.SWAP_OFFHAND ? event.getWhoClicked().getInventory().getItemInOffHand() : null;
        if (module.gadgetOf(event.getCurrentItem()) != null || module.gadgetOf(event.getCursor()) != null
                || module.gadgetOf(keyed) != null || module.gadgetOf(offHand) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (module.gadgetOf(event.getOldCursor()) != null) event.setCancelled(true);
    }
}
