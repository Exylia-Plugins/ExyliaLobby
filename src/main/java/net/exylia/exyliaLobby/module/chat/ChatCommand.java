package net.exylia.exyliaLobby.module.chat;

import net.exylia.exyliaLobby.Permissions;
import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

/** {@code /lobbyadmin chat lock|slow <seconds>|clear}. */
@CommandPermission(Permissions.ADMIN)
final class ChatCommand implements OrphanCommand {

    private final ChatModule module;

    ChatCommand(ChatModule module) {
        this.module = module;
    }

    @Subcommand("chat lock")
    public void lock(CommandSender sender) {
        module.toggleLock(sender);
    }

    @Subcommand("chat slow")
    public void slow(CommandSender sender, int seconds) {
        module.slow(sender, seconds);
    }

    @Subcommand("chat clear")
    public void clear(CommandSender sender) {
        module.clear(sender);
    }
}
