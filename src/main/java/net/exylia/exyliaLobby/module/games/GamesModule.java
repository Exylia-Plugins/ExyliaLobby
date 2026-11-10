package net.exylia.exyliaLobby.module.games;

import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.modes.Modes;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Every game the Exylia plugins offer, on ExyliaLib's modes screen. Games register themselves there; this module
 * is only the way in: the {@code games_open} action (the hotbar button) and the {@code /games} command.
 */
public final class GamesModule extends LobbyModule {

    public static final String ID = "games";

    private ConfigFile<GamesSettings> settings;
    private ConfigFile<GamesMessages> messages;

    public GamesModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.NETHER_STAR;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(GamesSettings.class);
        messages = messages(GamesMessages.class);
    }

    @Override
    protected List<Object> commands() {
        return command(settings.get().command(), new GamesCommand(this));
    }

    @Override
    protected void start() {
    }

    @Override
    protected void actions(PluginActions actions) {
        actions.registerSync("games_open", (action, args) -> {
            open(action.player());
            return ActionResult.success();
        });
    }

    /** Opens the games screen; says so in chat when nothing is registered. */
    public void open(@NotNull Player player) {
        if (Modes.modes().isEmpty()) {
            PluginMessages.of(plugin()).send(player, messages.get().empty(), Values.of());
            return;
        }
        Modes.open(player);
    }
}
