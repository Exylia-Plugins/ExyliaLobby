package net.exylia.exyliaLobby.module.pvp;

import net.exylia.exyliaLobby.Permissions;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

/** {@code /lobbyadmin pvp setspawn} and {@code /lobbyadmin pvp setboard}. */
final class PvpAdminCommand implements OrphanCommand {

    private final PvpModule module;

    PvpAdminCommand(PvpModule module) {
        this.module = module;
    }

    @Subcommand("pvp setspawn")
    @CommandPermission(Permissions.ADMIN)
    public void setSpawn(Player player) {
        module.setRespawn(player);
    }

    @Subcommand("pvp setboard")
    @CommandPermission(Permissions.ADMIN)
    public void setBoard(Player player) {
        module.setBoard(player);
    }
}
