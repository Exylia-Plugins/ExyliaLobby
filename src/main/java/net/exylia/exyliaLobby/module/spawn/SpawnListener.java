package net.exylia.exyliaLobby.module.spawn;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/** Void return: below the configured height, or by void damage, inside the lobby. */
final class SpawnListener implements Listener {

    private final SpawnModule module;

    SpawnListener(SpawnModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || !module.settings().get().voidReturn()) return;
        if (event.getTo().getY() >= module.settings().get().voidY()) return;
        Player player = event.getPlayer();
        if (module.context().areas().contains(player)) module.rescued(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID || !(event.getEntity() instanceof Player player)) return;
        if (!module.settings().get().voidReturn() || !module.context().areas().contains(player)) return;
        event.setCancelled(true);
        module.rescued(player);
    }
}
