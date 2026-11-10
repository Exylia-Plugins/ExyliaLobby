package net.exylia.exyliaLobby.module.pvp;

import org.bukkit.entity.Player;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.orphan.OrphanCommand;

/** {@code /pvptop}, under the labels the settings name. */
final class PvpCommand implements OrphanCommand {

    private final PvpModule module;

    PvpCommand(PvpModule module) {
        this.module = module;
    }

    @CommandPlaceholder
    public void top(Player player) {
        module.openTop(player);
    }
}
