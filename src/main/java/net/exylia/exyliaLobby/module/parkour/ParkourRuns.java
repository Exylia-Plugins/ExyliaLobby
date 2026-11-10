package net.exylia.exyliaLobby.module.parkour;

import net.exylia.exyliaLobby.module.Busy;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.Actions;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.effect.Effects;
import net.exylia.lib.guide.BeaconStyle;
import net.exylia.lib.guide.GuideBeacon;
import net.exylia.lib.guide.Guides;
import net.exylia.lib.hotbar.HotbarItem;
import net.exylia.lib.hotbar.HotbarSet;
import net.exylia.lib.hotbar.Hotbars;
import net.exylia.lib.item.Item;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.Cooldowns;
import net.exylia.lib.util.PeriodClock;
import net.exylia.lib.util.PluginCooldowns;
import net.exylia.lib.util.reward.OverflowPolicy;
import net.exylia.lib.util.reward.PendingRewards;
import net.exylia.lib.util.reward.PluginRewards;
import net.exylia.lib.util.reward.RewardEntry;
import net.exylia.lib.util.reward.Rewards;
import net.exylia.lib.util.teleport.TeleportResult;
import net.exylia.lib.util.teleport.Teleports;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * Every parkour run on this server, one per player.
 *
 * <p>The map is shared; a run's own state is touched only on its player's thread ({@link #onPlayer} hops there),
 * so the public methods are safe from any thread. Register it as a listener; {@link #close} when the module stops.
 */
final class ParkourRuns implements Listener {

    static final String ACTION_BACK = "parkour_run_checkpoint";
    static final String ACTION_RESTART = "parkour_run_restart";
    static final String ACTION_LEAVE = "parkour_run_leave";
    private static final Title.Times TITLE_TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(400));

    private final LobbyContext context;
    private final ConfigFile<ParkourSettings> settings;
    private final ConfigFile<ParkourCourses> courses;
    private final ConfigFile<ParkourThemes> themes;
    private final ConfigFile<ParkourMessages> messages;
    private final ParkourData data;
    private final Supplier<PeriodClock> clock;
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();
    private final PluginActions actions;
    private final Hotbars hotbars;

    ParkourRuns(LobbyContext context, ConfigFile<ParkourSettings> settings, ConfigFile<ParkourCourses> courses,
                ConfigFile<ParkourThemes> themes, ConfigFile<ParkourMessages> messages, ParkourData data,
                Supplier<PeriodClock> clock) {
        this.context = context;
        this.settings = settings;
        this.courses = courses;
        this.themes = themes;
        this.messages = messages;
        this.data = data;
        this.clock = clock;
        this.actions = Actions.of(context.plugin());
        actions.registerSync(ACTION_BACK, (action, args) -> {
            back(action.player());
            return ActionResult.success();
        });
        actions.registerSync(ACTION_RESTART, (action, args) -> {
            restart(action.player());
            return ActionResult.success();
        });
        actions.registerSync(ACTION_LEAVE, (action, args) -> {
            stop(action.player(), RunEnd.LEFT);
            return ActionResult.success();
        });
        this.hotbars = Hotbars.of(context.plugin(), actions);
        reload();
        PluginRewards rewards = Rewards.of(context.plugin());
        if (rewards.pending() == null) {
            rewards.overflow(OverflowPolicy.QUEUE).pending(PendingRewards.database(context.plugin())).claimOnJoin();
        }
    }

    // ======================== the API the module wires ========================

    /** Whether the player is in a run now. Safe from any thread. */
    boolean isRunning(@NotNull Player player) {
        return runs.containsKey(player.getUniqueId());
    }

    /**
     * Sends the player to a course's start and begins a run there; tells them when it cannot.
     *
     * @param typed the course id as typed
     */
    void startCourse(@NotNull Player player, @NotNull String typed) {
        ParkourMessages text = messages.get();
        String id = ParkourCourses.id(typed);
        ParkourCourses.Course course = courses.get().courses().get(id);
        World world = course == null ? null : Bukkit.getWorld(course.world());
        if (course == null || !course.ready() || world == null) {
            send(player, text.unknownCourse(), Values.of("course", typed));
            return;
        }
        if (!course.enabled()) {
            send(player, text.courseClosed(), Values.of("course", course.name()));
            return;
        }
        onPlayer(player, () -> {
            Run old = runs.get(player.getUniqueId());
            if (old != null) close(old, RunEnd.REPLACED);
            teleport(player, location(world, course.start())).thenAccept(result -> {
                if (result.isSuccess()) onPlayer(player, () -> openCourse(player, id, course, world));
            });
        });
    }

    /** Ends the player's run, if any. Safe from any thread. */
    void stop(@NotNull Player player, @NotNull RunEnd end) {
        Run run = runs.get(player.getUniqueId());
        if (run != null) onPlayer(player, () -> close(run, end));
    }

    /** Ends every run: the module is stopping. */
    void stopAll(@NotNull RunEnd end) {
        for (Run run : List.copyOf(runs.values())) onPlayer(run.player, () -> close(run, end));
    }

    /** The spawn module's void rule: a course runner goes back to their checkpoint. Player's thread. */
    @NotNull Optional<Location> rescue(@NotNull Player player) {
        Run run = runs.get(player.getUniqueId());
        return run == null ? Optional.empty() : run.rescue();
    }

    /** The back to checkpoint item. */
    void back(@NotNull Player player) {
        Run run = runs.get(player.getUniqueId());
        if (run != null) onPlayer(player, run::back);
    }

    /** The restart item. */
    void restart(@NotNull Player player) {
        Run run = runs.get(player.getUniqueId());
        if (run != null) onPlayer(player, run::restart);
    }

    /** Draws the run items again from the messages: after a reload. */
    void reload() {
        ParkourMessages.RunItems items = messages.get().items();
        String ns = actions.namespace() + ":";
        HotbarItem back = button("checkpoint", 3, items.checkpoint(), ns + ACTION_BACK);
        HotbarItem restart = button("restart", 4, items.restart(), ns + ACTION_RESTART);
        HotbarItem leave = button("leave", 5, items.leave(), ns + ACTION_LEAVE);
        hotbars.register(HotbarSet.of(CourseRun.HOTBAR, List.of(back, restart, leave)));
    }

    /** Ends every run and lets go of the run items' actions. */
    void close() {
        stopAll(RunEnd.DISABLED);
        actions.unregister(ACTION_BACK);
        actions.unregister(ACTION_RESTART);
        actions.unregister(ACTION_LEAVE);
    }

    private static HotbarItem button(String id, int slot, ParkourMessages.RunItem look, String action) {
        Item item = Item.of(look.icon()).name(look.name()).lore(look.lore()).build();
        return HotbarItem.of(id, slot, item).withActions(List.of(action)).withCooldown(Duration.ofMillis(400));
    }

    // ======================== runs ========================

    private void openCourse(Player player, String id, ParkourCourses.Course course, World world) {
        if (!player.isOnline()) return;
        List<Long> best = data.best(player.getUniqueId(), new Board(id, Board.Period.ALL))
                .map(ParkourRecord::splits).orElse(null);
        open(new CourseRun(this, player, id, course, world, best));
    }

    /** Registers a run and draws what every run has. Player's thread. */
    void open(Run run) {
        Player player = run.player;
        Run old = runs.get(player.getUniqueId());
        if (old != null) close(old, RunEnd.REPLACED);
        runs.put(player.getUniqueId(), run);
        run.bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        player.showBossBar(run.bar);
        if (settings.get().runItems() && Hotbars.isAvailable()) {
            run.hotbarBefore = hotbars.showing(player).orElse("");
            hotbars.show(player, run.hotbar());
        }
        run.timer = context.tasks().runAtEntityTimer(player, 1, 1, () -> {
            if (!run.ended) run.tick();
        });
        run.begin();
    }

    /** Ends a run and takes down everything it drew. Player's thread. */
    void close(Run run, RunEnd end) {
        if (run.ended) return;
        run.ended = true;
        Player player = run.player;
        runs.remove(player.getUniqueId(), run);
        if (run.timer != null) run.timer.cancel();
        if (run.bar != null) player.hideBossBar(run.bar);
        if (run.hotbarBefore != null) {
            if (run.hotbarBefore.isEmpty()) hotbars.hide(player);
            else hotbars.show(player, run.hotbarBefore);
        }
        try {
            run.ended(end);
        } catch (RuntimeException failed) {
            context.debug().error("Could not end the parkour run of " + player.getName(), failed);
        }
        if (end == RunEnd.LEFT) send(player, messages.get().left(), Values.of());
        else if (end.cheat()) send(player, messages.get().cheated(), Values.of("reason", reason(end)));
    }

    private String reason(RunEnd end) {
        ParkourMessages.Reasons reasons = messages.get().reasons();
        return switch (end) {
            case FLIGHT -> reasons.flight();
            case ELYTRA -> reasons.elytra();
            case RIPTIDE -> reasons.riptide();
            case PEARL -> reasons.pearl();
            case VEHICLE -> reasons.vehicle();
            case GAME_MODE -> reasons.gameMode();
            default -> reasons.teleport();
        };
    }

    /** A course run reached its finish with every checkpoint. Player's thread. */
    void finish(CourseRun run) {
        Player player = run.player;
        long millis = run.millis();
        List<Long> splits = List.copyOf(run.splits);
        close(run, RunEnd.FINISHED);
        sound(player, settings.get().sounds().finish());
        data.record(player, run.id, millis, splits).whenComplete((outcome, failed) -> {
            if (failed != null) {
                context.debug().error("Could not record the parkour finish of " + player.getName() + " on " + run.id, failed);
                return;
            }
            onPlayer(player, () -> finished(player, run, millis, outcome));
        });
    }

    private void finished(Player player, CourseRun run, long millis, ParkourData.Outcome outcome) {
        if (!player.isOnline()) return;
        ParkourMessages text = messages.get();
        ParkourCourses.Medal medal = run.course.medals().of(millis);
        String medalLine = medal == null ? "" : switch (medal) {
            case GOLD -> text.medalGold();
            case SILVER -> text.medalSilver();
            case BRONZE -> text.medalBronze();
        };
        boolean record = outcome.improved() && outcome.previous() != null;
        send(player, text.finished(), run.values().put("time", Times.format(millis))
                .put("record", outcome.improved() ? text.newRecord() : text.noRecord())
                .put("best", Times.format(outcome.best().value()))
                .put("rank", outcome.rank() > 0 ? String.valueOf(outcome.rank()) : text.unranked())
                .put("medal", medalLine));
        if (record) sound(player, settings.get().sounds().record());

        ParkourCourses.Course course = run.course;
        PluginCooldowns cooldowns = Cooldowns.forPlugin(context.plugin());
        String key = "parkour-reward-" + run.id;
        Duration cooldown = course.repeatCooldown() == null ? Duration.ZERO : course.repeatCooldown();
        if (outcome.first() && !course.firstRewards().isEmpty()) {
            reward(player, course.firstRewards(), "courses." + run.id + ".first-rewards");
            if (!cooldown.isZero()) cooldowns.start(player, key, cooldown);
        } else if (!course.repeatRewards().isEmpty() && (cooldown.isZero() || cooldowns.tryStart(player, key, cooldown))) {
            reward(player, course.repeatRewards(), "courses." + run.id + ".repeat-rewards");
        }
    }

    /** Gives a reward list written in a config file; what does not fit waits in the reward queue. Player's thread. */
    void reward(Player player, List<Object> lines, String where) {
        if (lines == null || lines.isEmpty()) return;
        YamlConfiguration holder = new YamlConfiguration();
        holder.set("rewards", lines);
        List<RewardEntry> list = Rewards.read(holder, "rewards",
                (at, problem) -> context.debug().warn("parkour " + where + " " + at + ": " + problem));
        if (!list.isEmpty()) Rewards.of(context.plugin()).give(player, list);
    }

    // ======================== events ========================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        Player player = event.getPlayer();
        Run run = runs.get(player.getUniqueId());
        if (run != null) {
            if (!run.ended) run.moved(event.getFrom(), event.getTo());
            return;
        }
        Location to = event.getTo();
        Location from = event.getFrom();
        String world = to.getWorld().getName();
        for (Map.Entry<String, ParkourCourses.Course> entry : courses.get().courses().entrySet()) {
            ParkourCourses.Course course = entry.getValue();
            ParkourCourses.Spot start = course.start();
            if (!course.enabled() || !course.ready() || !course.world().equals(world)) continue;
            if (!start.contains(to.getX(), to.getY(), to.getZ()) || start.contains(from.getX(), from.getY(), from.getZ())) continue;
            if (Busy.any(context, player)) return;
            openCourse(player, entry.getKey(), course, to.getWorld());
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Run run = runs.get(event.getPlayer().getUniqueId());
        if (run != null) close(run, RunEnd.QUIT);
    }

    // ======================== helpers for runs ========================

    ParkourSettings settings() {
        return settings.get();
    }

    ParkourMessages messages() {
        return messages.get();
    }

    ParkourThemes themes() {
        return themes.get();
    }

    ParkourData data() {
        return data;
    }

    PeriodClock clock() {
        return clock.get();
    }

    LobbyContext context() {
        return context;
    }

    /** Runs on the player's thread: now when already there (or while the plugin stops), else scheduled. */
    void onPlayer(Player player, Runnable work) {
        if (context.stopping() || context.tasks().isOwnedBy(player)) work.run();
        else context.tasks().runAtEntity(player, work);
    }

    /** Moves a runner; the guard lets it through. Player's thread. */
    CompletableFuture<TeleportResult> move(Run run, Location to) {
        run.expected = to.clone();
        return teleport(run.player, to);
    }

    private CompletableFuture<TeleportResult> teleport(Player player, Location to) {
        return Teleports.of(context.plugin()).to(player, to).warmup(0).start().future();
    }

    static Location location(World world, ParkourCourses.Spot spot) {
        return new Location(world, spot.x(), spot.y(), spot.z(), (float) spot.yaw(), (float) spot.pitch());
    }

    void send(Player player, String line, Values values) {
        if (line != null && !line.isBlank()) PluginMessages.of(context.plugin()).send(player, line, values);
    }

    void send(Player player, List<String> lines, Values values) {
        if (lines != null && !lines.isEmpty()) PluginMessages.of(context.plugin()).send(player, lines, values);
    }

    Component component(Player player, String line, Values values) {
        return PluginMessages.of(context.plugin()).text(line, values).forPlayer(player).build();
    }

    void title(Player player, String title, String subtitle, Values values) {
        player.showTitle(Title.title(component(player, title, values), component(player, subtitle, values), TITLE_TIMES));
    }

    void sound(Player player, String line) {
        if (line != null && !line.isBlank()) Effects.soundFrom(line).show(player);
    }

    GuideBeacon beacon(Player player, Location at, DoubleFunction<Component> label) {
        return Guides.of(context.plugin()).beacon(player, at,
                BeaconStyle.of(Material.PURPLE_STAINED_GLASS.createBlockData(), 0x8A51C4), label);
    }
}
