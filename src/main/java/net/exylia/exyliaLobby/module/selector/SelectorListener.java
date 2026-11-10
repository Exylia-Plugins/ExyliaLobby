package net.exylia.exyliaLobby.module.selector;

import net.exylia.lib.region.PlayerRegionChangeEvent;
import net.exylia.lib.region.RegionSnapshot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Walking into a portal box sends the player to its server. */
final class SelectorListener implements Listener {

    private final SelectorModule module;

    SelectorListener(SelectorModule module) {
        this.module = module;
    }

    @EventHandler
    public void onRegionChange(PlayerRegionChangeEvent event) {
        for (RegionSnapshot region : event.entered(module.regions())) module.entered(event.getPlayer(), region);
    }
}
