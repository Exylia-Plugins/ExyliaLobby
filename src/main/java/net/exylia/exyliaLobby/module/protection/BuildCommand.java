package net.exylia.exyliaLobby.module.protection;

import net.exylia.exyliaLobby.Permissions;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

/** {@code /lobbyadmin build}: the admin's switch for ignoring the protection. */
final class BuildCommand implements OrphanCommand {

    private final ProtectionModule module;

    BuildCommand(ProtectionModule module) {
        this.module = module;
    }

    @Subcommand("build")
    @CommandPermission(Permissions.BUILD)
    public void build(Player player) {
        module.toggleBuild(player);
    }
}
