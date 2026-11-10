package net.exylia.exyliaLobby.module;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * A module that holds players in an activity of its own (a parkour run, a lobby PvP fight): other modules leave
 * those players alone (no double jump, no launch pads, no gadgets, no hotbar swap).
 *
 * <p>Ask every running module with {@link #any(LobbyContext, Player)} rather than naming one.
 */
public interface Busy {

    /** Whether this module holds the player now. Safe from any thread. */
    boolean busy(@NotNull Player player);

    /** Whether any running module holds the player now. */
    static boolean any(@NotNull LobbyContext context, @NotNull Player player) {
        for (String id : java.util.List.of("parkour", "pvp")) {
            if (context.modules().getModule(id).filter(module -> module instanceof Busy busy && busy.busy(player)).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
