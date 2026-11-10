package net.exylia.exyliaLobby.admin;

import net.exylia.exyliaLobby.ExyliaLobby;
import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.config.LobbyConfig;
import net.exylia.exyliaLobby.config.LobbyMessages;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.config.ConfigMenus;
import net.exylia.lib.text.Text;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Switching a module live and opening its settings: the two clicks of a module card. */
final class ModuleSwitches {

    private ModuleSwitches() {
    }

    static void toggle(@NotNull ExyliaLobby lobby, @NotNull Player viewer, @NotNull LobbyModule module) {
        boolean on = !module.isEnabled();
        lobby.getConfig().update(config -> new LobbyConfig(config.language(), config.modules().with(module.getId(), on),
                config.areas(), config.adminCommand(), config.debug()));
        // TODO(lib): switch live through PluginModules once ExyliaLib ships it.
        LobbyMessages.Modules text = lobby.getMessages().get().modules();
        Text.from(lobby.getPlugin(), on ? text.switchedOn() : text.switchedOff())
                .withFormatted("module", lobby.getNames().get().of(module.getId()).name()).send(viewer);
    }

    static void openSettings(@NotNull ExyliaLobby lobby, @NotNull Player viewer, @NotNull LobbyModule module,
                             @NotNull Runnable back) {
        // TODO(lib): open module.settingsPath() straight away once ConfigMenus can open one file.
        ConfigMenus.of(lobby.getPlugin()).permission(Permissions.ADMIN).back(player -> back.run()).open(viewer);
    }
}
