package net.exylia.exyliaLobby.module;

import net.exylia.exyliaLobby.module.join.JoinModule;
import net.exylia.exyliaLobby.module.protection.ProtectionModule;
import net.exylia.exyliaLobby.module.spawn.SpawnModule;
import net.exylia.exyliaLobby.module.state.StateModule;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Every module, in the order they start (and the reverse order they stop and hand players back).
 *
 * <p>A module that needs another one comes after it. One line per module: add yours here.
 */
public final class Modules {

    private Modules() {
    }

    public static @NotNull List<LobbyModule> all(@NotNull LobbyContext context) {
        return List.of(
                new SpawnModule(context),
                new StateModule(context),
                new ProtectionModule(context),
                new JoinModule(context)
        );
    }
}
