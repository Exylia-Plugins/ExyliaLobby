package net.exylia.exyliaLobby.admin;

import net.exylia.exyliaLobby.ExyliaLobby;
import net.exylia.exyliaLobby.config.LobbyConfig;
import net.exylia.exyliaLobby.config.LobbyMessages;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.module.ModuleToggle;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

/** Switching a module live: the left click of a module card. */
public final class ModuleSwitches {

    private ModuleSwitches() {
    }

    /**
     * Switches a module on the global thread (where the module registry is driven), writes the switch asked for to
     * config.yml so the next start agrees, tells the admin and redraws their screen.
     */
    public static void toggle(@NotNull ExyliaLobby lobby, @NotNull CommandSender viewer, @NotNull LobbyModule module,
                              @Nullable Boolean wanted, @NotNull Runnable redraw) {
        boolean on = wanted != null ? wanted : !module.isEnabled();
        lobby.getTasks().run(() -> {
            if (on && !module.handedBack().isDone()) {
                // Switched off a moment ago and still giving players their things back: switch on once that is done.
                module.handedBack().thenRun(() -> toggle(lobby, viewer, module, true, redraw));
                return;
            }
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
            PluginMessages.of(lobby.getPlugin()).send(viewer, line, Values.of(
                    "module", lobby.getNames().get().of(module.getId()).name()).put("required", missing));
            redraw.run();
        });
    }
}
