package net.exylia.exyliaLobby.module.gadgets;

import org.bukkit.entity.Player;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.orphan.OrphanCommand;

/** {@code /gadgets}, under the labels the settings name. */
final class GadgetsCommand implements OrphanCommand {

    private final GadgetsModule module;

    GadgetsCommand(GadgetsModule module) {
        this.module = module;
    }

    @CommandPlaceholder
    public void open(Player player) {
        module.open(player);
    }
}
