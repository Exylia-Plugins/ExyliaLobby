package net.exylia.exyliaLobby.admin;

import net.exylia.exyliaLobby.ExyliaLobby;
import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.config.LobbyConfig;
import net.exylia.exyliaLobby.config.LobbyMessages;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.config.ConfigMenus;
import net.exylia.lib.module.ModuleToggle;
import net.exylia.lib.text.Text;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

/** Switching a module live and opening its settings: the two clicks of a module card. */
final class ModuleSwitches {

    private ModuleSwitches() {
    }

    /**
     * Switches a module on the global thread (where the module registry is driven), writes the switch asked for to
     * config.yml so the next start agrees, tells the admin and redraws their screen.
     */
    static void toggle(@NotNull ExyliaLobby lobby, @NotNull Player viewer, @NotNull LobbyModule module, @NotNull Runnable redraw) {
        boolean on = !module.isEnabled();
        lobby.getTasks().run(() -> {
            ModuleToggle result = lobby.getModules().setEnabled(module.getId(), on);
            if (result.succeeded()) {
                lobby.getConfig().update(config -> new LobbyConfig(config.language(),
                        config.modules().with(module.getId(), on), config.areas(), config.adminCommand(), config.debug()));
            }
            LobbyMessages.Modules text = lobby.getMessages().get().modules();
            String line = switch (result.status()) {
                case ENABLED, UNCHANGED -> on ? text.switchedOn() : text.switchedOff();
                case DISABLED -> text.switchedOff();
                case BLOCKED -> text.blockedBy();
                default -> text.failed();
            };
            String missing = result.missing().stream().map(id -> lobby.getNames().get().of(id).name())
                    .collect(Collectors.joining(", "));
            Text.from(lobby.getPlugin(), line)
                    .withFormatted("module", lobby.getNames().get().of(module.getId()).name())
                    .withFormatted("required", missing)
                    .send(viewer);
            redraw.run();
        });
    }

    static void openSettings(@NotNull ExyliaLobby lobby, @NotNull Player viewer, @NotNull LobbyModule module,
                             @NotNull Runnable back) {
        ConfigMenus.of(lobby.getPlugin()).permission(Permissions.ADMIN).back(player -> back.run())
                .file(module.settingsPath()).open(viewer);
    }
}
