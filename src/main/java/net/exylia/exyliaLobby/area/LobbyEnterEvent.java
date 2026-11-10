package net.exylia.exyliaLobby.area;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A player is now inside a lobby area: they joined inside one, walked or were moved into one, or an area was
 * created around them. Fired on the player's own thread, once per crossing.
 */
public final class LobbyEnterEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final AreaCause cause;

    public LobbyEnterEvent(@NotNull Player player, @NotNull AreaCause cause) {
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
