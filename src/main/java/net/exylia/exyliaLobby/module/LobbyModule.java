package net.exylia.exyliaLobby.module;

import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Configs;
import net.exylia.lib.module.PluginModule;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.Cooldowns;
import net.exylia.lib.util.PluginCooldowns;
import net.exylia.lib.util.TimeFormats;
import net.exylia.lib.util.wizard.Setup;
import net.exylia.lib.util.wizard.Wizards;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import revxrsal.commands.orphan.OrphanCommand;
import revxrsal.commands.orphan.Orphans;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    /** Players still waiting for {@link #leave} after a switch-off, and the end of that switch-off. */
    private final Set<UUID> handingBack = ConcurrentHashMap.newKeySet();
    private static final Duration REFUSAL = Duration.ofSeconds(1);
    private volatile CompletableFuture<Void> stopped = CompletableFuture.completedFuture(null);

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

    private final java.util.Set<Class<?>> commandTypes = java.util.concurrent.ConcurrentHashMap.newKeySet();

    protected final void listen(@NotNull Listener listener) {
        plugin().getServer().getPluginManager().registerEvents(listener, plugin());
        listeners.add(listener);
    }

    /** A player command under the labels a module's settings name; nothing when the list is empty. */
    protected final @NotNull List<Object> command(@NotNull List<String> labels, @NotNull OrphanCommand handler) {
        if (labels.isEmpty()) return List.of();
        commandTypes.add(handler.getClass());
        return List.of(Orphans.path(labels.toArray(String[]::new)).handler(handler));
    }

    /**
     * Whether a command method belongs to this module. ExyliaLib's own owner lookup only sees the Lamp wrapper of an
     * orphan command, never the handler class inside it, so the core asks the modules itself.
     */
    public final boolean ownsCommand(@NotNull Class<?> declaring) {
        for (Class<?> type = declaring; type != null; type = type.getEnclosingClass()) {
            if (commandTypes.contains(type)) return true;
        }
        return false;
    }

    /** A subcommand group under {@code /lobbyadmin}. */
    protected final @NotNull List<Object> adminCommand(@NotNull OrphanCommand handler) {
        return command(context.config().get().adminCommand(), handler);
    }

    /**
     * The module's Lamp handlers. Registered once at start for every module, running or not (a module switched on
     * later needs its commands), and refused while the module is off. Never touch what {@link #start} builds here:
     * resolve it when the command runs.
     */
    protected @NotNull List<Object> commands() {
        return List.of();
    }

    @Override
    protected final @NotNull List<Object> commandHandlers() {
        // An off module never loaded its files, and its labels live there; registerConfigs is safe to repeat.
        if (!isEnabled()) registerConfigs();
        return commands();
    }

    /**
     * Sets one place in game: the admin walks there and confirms with the gesture the thing needs. {@code back} reopens
     * the screen they came from when they cancel; {@code null} for a flow started from a command.
     */
    public final @NotNull Setup setup(@NotNull Player admin, @Nullable Runnable back) {
        return back == null ? Wizards.of(plugin()).setup(admin) : Wizards.of(plugin()).setup(admin, back);
    }

    /** This plugin's cooldowns. Keys start with the module id ({@code gadgets_grapple}). */
    protected final @NotNull PluginCooldowns cooldowns() {
        return Cooldowns.forPlugin(plugin());
    }

    /**
     * Whether a refusal about {@code key} may be said now: at most once a second, so holding a click on something
     * that is cooling down does not flood the chat.
     */
    protected final boolean refusalDue(@NotNull Player player, @NotNull String key) {
        return cooldowns().tryStart(player, "refusal:" + key, REFUSAL);
    }

    /**
     * Sends the "wait" line of a running cooldown, at most once a second, with {@code %time%} ("0.4s", "1m 30s")
     * and its older spelling {@code %seconds%}.
     */
    protected final void refuseCooling(@NotNull Player player, @NotNull String key, @NotNull String line, @NotNull Values values) {
        if (!refusalDue(player, key)) return;
        PluginMessages.of(plugin()).send(player, line, withTime(values, cooldowns().remainingSeconds(player, key)));
    }

    /**
     * Puts a length of time in {@code values}: {@code %time%} with its unit ("0.4s", "3.3s", "1m 30s") and
     * {@code %seconds%}, the bare number older message files wrote as {@code %seconds%s}.
     */
    public static @NotNull Values withTime(@NotNull Values values, double seconds) {
        return values.put("time", TimeFormats.render(seconds, TimeFormats.Style.PRECISE))
                .put("seconds", TimeFormats.render(seconds, TimeFormats.Style.AUTO));
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
        if (!isEnabled()) {
            // Switched off, but this player had not been handed back yet: leaving (a quit) is the last chance.
            if (!entered) handBack(player);
            return;
        }
        if (entered) enter(player, cause);
        else leave(player, cause);
    }

    @Override
    protected final void onEnable() {
        if (!stopped.isDone()) throw new IllegalStateException("Module " + getId() + " is still handing players back");
        start();
        for (UUID id : context.areas().players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) context.tasks().runAtEntity(player, () -> {
                if (isEnabled() && inLobby(player)) enter(player, AreaCause.MODULE);
            });
        }
    }

    /**
     * Gives everyone inside their state back, then stops. A player on another thread (Folia, a live switch from the
     * global thread) gets {@link #leave} on their own thread, and {@link #stop} waits for all of them, since it
     * clears the very state {@code leave} gives back.
     */
    @Override
    protected final void onDisable() {
        if (!stopped.isDone()) return; // an enable refused while a switch-off was still pending
        List<CompletableFuture<Void>> waits = new ArrayList<>();
        for (UUID id : context.areas().players()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            if (context.stopping() || context.tasks().isOwnedBy(player)) {
                safeLeave(player);
                continue;
            }
            handingBack.add(id);
            CompletableFuture<Void> done = new CompletableFuture<>();
            waits.add(done);
            context.tasks().runAtEntity(player, () -> {
                handBack(player);
                done.complete(null);
            }, () -> {
                // ponytail: removed before their turn, after their quit crossing already handed them back (or never
                // reached it); a removed player is not touched.
                handingBack.remove(id);
                done.complete(null);
            });
        }
        CompletableFuture<Void> pending = new CompletableFuture<>();
        stopped = pending;
        if (waits.isEmpty()) {
            finish(pending);
            return;
        }
        CompletableFuture.allOf(waits.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> context.tasks().run(() -> finish(pending)));
    }

    /**
     * Completes once a switch-off has handed every player back and {@link #stop} ran. Switching the module on
     * before that is refused, so wait for it.
     */
    public final @NotNull CompletableFuture<Void> handedBack() {
        return stopped;
    }

    /** At shutdown: hands back whoever is still waiting, here and now, and stops. Called by the core. */
    public final void finishPendingStop() {
        CompletableFuture<Void> pending = stopped;
        if (pending.isDone()) return;
        for (UUID id : List.copyOf(handingBack)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) handBack(player);
        }
        finish(pending);
    }

    private void handBack(Player player) {
        if (handingBack.remove(player.getUniqueId())) safeLeave(player);
    }

    private synchronized void finish(CompletableFuture<Void> pending) {
        if (pending.isDone()) return;
        try {
            listeners.forEach(HandlerList::unregisterAll);
            listeners.clear();
            stop();
        } catch (RuntimeException failed) {
            context.debug().error("Module " + getId() + " failed to stop cleanly", failed);
        } finally {
            pending.complete(null);
        }
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

    /** Stops what {@link #start} built. Players inside already got {@link #leave}, each on their own thread. */
    protected void stop() {
    }
}
