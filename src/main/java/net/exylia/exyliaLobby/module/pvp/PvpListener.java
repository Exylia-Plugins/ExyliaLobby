package net.exylia.exyliaLobby.module.pvp;

import net.exylia.lib.region.PlayerRegionChangeEvent;
import net.exylia.lib.region.RegionSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** The events the PvP module reacts to; every decision lives in {@link PvpModule}. */
final class PvpListener implements Listener {

    private final PvpModule module;

    PvpListener(PvpModule module) {
        this.module = module;
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        if (module.held(event.getPlayer(), event.getNewSlot())) event.setCancelled(true);
    }

    /** Records who hit whom, so the tag and the quit rule know the opponent. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = event.getDamager() instanceof Player direct ? direct
                : event.getDamager() instanceof Projectile shot && shot.getShooter() instanceof Player shooter ? shooter : null;
        if (attacker != null && attacker != victim) module.hit(attacker, victim);
    }

    /** Only when the protection module is off: nothing but a hit between two fighters may hurt a lobby player. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !module.guardsDamage()) return;
        Player attacker = event instanceof EntityDamageByEntityEvent byEntity
                ? byEntity.getDamager() instanceof Player direct ? direct
                : byEntity.getDamager() instanceof Projectile shot && shot.getShooter() instanceof Player shooter ? shooter : null
                : null;
        if (module.inLobbyNow(victim) && (attacker == null || attacker == victim || !module.canHit(attacker, victim))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        module.death(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (module.keeps(event.getPlayer(), event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (module.fighting(event.getPlayer()) || module.isSword(event.getMainHandItem())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (module.isSword(event.getCurrentItem()) || module.isSword(event.getCursor())
                || (module.fighting(player) && event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        module.joined(event.getPlayer());
    }

    @EventHandler
    public void onRegionChange(PlayerRegionChangeEvent event) {
        for (RegionSnapshot region : event.entered(module.regions())) module.zoneEntered(event.getPlayer());
    }
}
