package net.exylia.exyliaLobby.module.spawn;

import org.bukkit.entity.Player;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

/** {@code /spawn}, under the labels the settings name. */
@CommandPermission(SpawnModule.PERMISSION)
final class SpawnCommand implements OrphanCommand {

    private final SpawnModule module;

    SpawnCommand(SpawnModule module) {
        this.module = module;
    }

    @CommandPlaceholder
    public void spawn(Player player) {
        module.playerSpawn(player);
    }
}
