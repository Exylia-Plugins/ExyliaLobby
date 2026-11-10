package net.exylia.exyliaLobby.command;

import net.exylia.exyliaLobby.ExyliaLobby;
import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.admin.ModuleSwitches;
import net.exylia.exyliaLobby.module.LobbyModule;
import org.bukkit.command.CommandSender;
import java.util.stream.Collectors;
import revxrsal.commands.annotation.Single;
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

    @Subcommand("module")
    public void module(CommandSender sender, @Single String id) {
        switchModule(sender, id, null);
    }

    @Subcommand("module")
    public void module(CommandSender sender, @Single String id, @Single String state) {
        switch (state.toLowerCase()) {
            case "on", "true" -> switchModule(sender, id, true);
            case "off", "false" -> switchModule(sender, id, false);
            default -> sender.sendMessage("Usage: /lobbyadmin module <id> [on|off]");
        }
    }

    private void switchModule(CommandSender sender, String id, Boolean on) {
        lobby.getRegistered().stream().filter(module -> module.getId().equalsIgnoreCase(id)).findFirst().ifPresentOrElse(
                module -> ModuleSwitches.toggle(lobby, sender, module, on, () -> { }),
                () -> sender.sendMessage("Unknown module '" + id + "'. Ids: " + lobby.getRegistered().stream()
                        .map(LobbyModule::getId).collect(Collectors.joining(", "))));
    }

    @Subcommand("reload")
    public void reload(CommandSender sender) {
        lobby.getReloads().run(sender);
    }
}
