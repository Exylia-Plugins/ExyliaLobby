package net.exylia.exyliaLobby.module.games;

import org.bukkit.entity.Player;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.orphan.OrphanCommand;

/** {@code /games}, under the labels the settings name. */
final class GamesCommand implements OrphanCommand {

    private final GamesModule module;

    GamesCommand(GamesModule module) {
        this.module = module;
    }

    @CommandPlaceholder
    public void open(Player player) {
        module.open(player);
    }
}
