package net.exylia.exyliaLobby.module;

import net.exylia.exyliaLobby.module.chat.ChatModule;
import net.exylia.exyliaLobby.module.gadgets.GadgetsModule;
import net.exylia.exyliaLobby.module.games.GamesModule;
import net.exylia.exyliaLobby.module.holograms.HologramsModule;
import net.exylia.exyliaLobby.module.hotbar.HotbarModule;
import net.exylia.exyliaLobby.module.hud.HudModule;
import net.exylia.exyliaLobby.module.join.JoinModule;
import net.exylia.exyliaLobby.module.movement.MovementModule;
import net.exylia.exyliaLobby.module.onboarding.OnboardingModule;
import net.exylia.exyliaLobby.module.parkour.ParkourModule;
import net.exylia.exyliaLobby.module.protection.ProtectionModule;
import net.exylia.exyliaLobby.module.pvp.PvpModule;
import net.exylia.exyliaLobby.module.spawn.SpawnModule;
import net.exylia.exyliaLobby.module.selector.SelectorModule;
import net.exylia.exyliaLobby.module.state.StateModule;
import net.exylia.exyliaLobby.module.visibility.VisibilityModule;
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
                new JoinModule(context),
                new ChatModule(context),
                new OnboardingModule(context),
                new HotbarModule(context),
                new HologramsModule(context),
                new ParkourModule(context),
                new MovementModule(context),
                new VisibilityModule(context),
                new SelectorModule(context),
                new GamesModule(context),
                new HudModule(context),
                new PvpModule(context),
                new GadgetsModule(context)
        );
    }
}
