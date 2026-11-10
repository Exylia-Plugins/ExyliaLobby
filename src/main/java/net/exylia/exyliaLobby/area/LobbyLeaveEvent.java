package net.exylia.exyliaLobby.area;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A player is no longer inside a lobby area: they walked or were moved out, quit, or the area or the plugin went
 * away. Fired on the player's own thread, once per crossing; modules give back what they changed.
 */
public final class LobbyLeaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final AreaCause cause;

    public LobbyLeaveEvent(@NotNull Player player, @NotNull AreaCause cause) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.player = player;
        this.cause = cause;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull AreaCause getCause() {
        return cause;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
