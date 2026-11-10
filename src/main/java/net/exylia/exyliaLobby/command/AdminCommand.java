package net.exylia.exyliaLobby.command;

import net.exylia.exyliaLobby.ExyliaLobby;
import net.exylia.exyliaLobby.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

/** {@code /lobbyadmin}: the hub, the Modules screen and reload. Modules add their own subcommands. */
@CommandPermission(Permissions.ADMIN)
public final class AdminCommand implements OrphanCommand {

    private final ExyliaLobby lobby;

    public AdminCommand(ExyliaLobby lobby) {
        this.lobby = lobby;
    }

    @CommandPlaceholder
    public void hub(Player player) {
        lobby.getHub().open(player);
    }

    @Subcommand("modules")
    public void modules(Player player) {
        lobby.getHub().openModules(player);
    }

    @Subcommand("reload")
    public void reload(CommandSender sender) {
        lobby.getReloads().run(sender);
    }
}
