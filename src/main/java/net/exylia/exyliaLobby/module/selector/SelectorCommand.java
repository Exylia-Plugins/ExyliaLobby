package net.exylia.exyliaLobby.module.selector;

import org.bukkit.entity.Player;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

/** {@code /servers} or {@code /lobbies}, under the labels the settings name. */
@CommandPermission(SelectorModule.PERMISSION)
final class SelectorCommand implements OrphanCommand {

    private final SelectorModule module;
    private final boolean lobbies;

    SelectorCommand(SelectorModule module, boolean lobbies) {
        this.module = module;
        this.lobbies = lobbies;
    }

    @CommandPlaceholder
    public void open(Player player) {
        if (lobbies) module.openLobbies(player);
        else module.openServers(player);
    }
}
