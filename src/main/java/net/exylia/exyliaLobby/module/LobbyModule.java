package net.exylia.exyliaLobby.module;

import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Configs;
import net.exylia.lib.module.PluginModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import revxrsal.commands.orphan.OrphanCommand;
import revxrsal.commands.orphan.Orphans;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * One switchable part of the lobby.
 *
 * <p>What every module gets from here:
 * <ul>
 *   <li>{@link #settings} and {@link #messages}: its record files, {@code modules/<id>/config.yml} and
 *   {@code lang/<language>/modules/<id>/messages.yml}, both editable in game through ConfigMenus.</li>
 *   <li>{@link #listen}: listeners removed again when the module stops.</li>
 *   <li>{@link #enter} and {@link #leave}: called on the player's own thread whenever they cross into or out of
 *   the lobby, and also for everyone already inside when the module is switched on ({@link AreaCause#MODULE})
 *   or off ({@link AreaCause#DISABLE}), so a module never has to track that itself. Whatever {@code enter}
 *   changes on a player, {@code leave} gives back.</li>
 *   <li>{@link #openAdmin}: the module's own admin screen, reached from its card in {@code /lobbyadmin}.</li>
 * </ul>
 */
public abstract class LobbyModule extends PluginModule {

    protected final LobbyContext context;
    private final List<Listener> listeners = new ArrayList<>();

    protected LobbyModule(@NotNull String id, @NotNull LobbyContext context) {
        super(id);
        this.context = context;
    }

    /** The icon of this module's card in the Modules screen. */
    public abstract @NotNull Material icon();

    /** The path of this module's settings file as given to Configs, for opening it straight from its card. */
    public final @NotNull String settingsPath() {
        return "modules/" + getId() + "/config";
    }

    protected final <T extends Record> @NotNull ConfigFile<T> settings(@NotNull Class<T> schema) {
        return settings(schema, UnaryOperator.identity());
    }

    protected final <T extends Record> @NotNull ConfigFile<T> settings(@NotNull Class<T> schema,
                                                                       @NotNull UnaryOperator<Configs.Builder<T>> layout) {
        return layout.apply(config(settingsPath(), schema)).load();
    }

    protected final <T extends Record> @NotNull ConfigFile<T> messages(@NotNull Class<T> schema) {
        return messages(schema, UnaryOperator.identity());
    }

    protected final <T extends Record> @NotNull ConfigFile<T> messages(@NotNull Class<T> schema,
                                                                       @NotNull UnaryOperator<Configs.Builder<T>> layout) {
        return layout.apply(config("modules/" + getId() + "/messages", schema).translated()).load();
    }

    protected final void listen(@NotNull Listener listener) {
        plugin().getServer().getPluginManager().registerEvents(listener, plugin());
        listeners.add(listener);
    }

    /** A player command under the labels a module's settings name; nothing when the list is empty. */
    protected final @NotNull List<Object> command(@NotNull List<String> labels, @NotNull OrphanCommand handler) {
        return labels.isEmpty() ? List.of() : List.of(Orphans.path(labels.toArray(String[]::new)).handler(handler));
    }

    /** A subcommand group under {@code /lobbyadmin}. */
    protected final @NotNull List<Object> adminCommand(@NotNull OrphanCommand handler) {
        return command(context.config().get().adminCommand(), handler);
    }

    /** Whether the player is in the lobby now. */
    protected final boolean inLobby(@NotNull Player player) {
        return context.areas().contains(player);
    }

    /** The player crossed into the lobby, or was inside when this module started. Player's own thread. */
    protected void enter(@NotNull Player player, @NotNull AreaCause cause) {
    }

    /** The player crossed out of the lobby, quit, or this module is stopping. Player's own thread. */
    protected void leave(@NotNull Player player, @NotNull AreaCause cause) {
    }

    /** Whether this module has an admin screen of its own; its card then opens it. */
    public boolean hasAdmin() {
        return false;
    }

    /** Opens this module's admin screen. {@code back} reopens the screen the admin came from. */
    public void openAdmin(@NotNull Player viewer, @NotNull Runnable back) {
    }

    /** Called by the core for a crossing; never call it yourself. */
    public final void crossing(@NotNull Player player, boolean entered, @NotNull AreaCause cause) {
        if (!isEnabled()) return;
        if (entered) enter(player, cause);
        else leave(player, cause);
    }

    @Override
    protected final void onEnable() {
        start();
        for (UUID id : context.areas().players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) context.tasks().runAtEntity(player, () -> {
                if (isEnabled() && inLobby(player)) enter(player, AreaCause.MODULE);
            });
        }
    }

    @Override
    protected final void onDisable() {
        for (UUID id : context.areas().players()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            if (context.stopping()) safeLeave(player);
            else context.tasks().runAtEntity(player, () -> safeLeave(player));
        }
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        stop();
    }

    private void safeLeave(@Nullable Player player) {
        if (player == null) return;
        try {
            leave(player, AreaCause.DISABLE);
        } catch (RuntimeException failed) {
            context.debug().error("Module " + getId() + " failed to give " + player.getName() + " their state back", failed);
        }
    }

    /** Builds the module's services; settings and messages are already loaded. */
    protected abstract void start();

    /** Stops what {@link #start} built. Players inside already got {@link #leave}. */
    protected void stop() {
    }
}
