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
import net.exylia.lib.log.Logs;
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
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.exylia.exyliaLobby.module.parkour.endless.Bounds;
import net.exylia.exyliaLobby.module.parkour.endless.Cell;
import net.exylia.exyliaLobby.module.parkour.endless.PathGenerator;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
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
    static final String LOG_TOO_FAST = "parkour-too-fast";
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
    private final RunGuard guard = new RunGuard(this);

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
        Logs.of(context.plugin()).action(LOG_TOO_FAST, "FEATHER");
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

    /** The anti-cheat listener; register it next to this one. */
    Listener guard() {
        return guard;
    }

    /** The player's run, read on their own thread. */
    Run runOf(Player player) {
        return runs.get(player.getUniqueId());
    }

    /**
     * Sends the player onto a new endless path of a mode; tells them when it cannot.
     */
    void startEndless(@NotNull Player player, @NotNull EndlessMode mode) {
        startEndless(player, mode, null);
    }

    /** @param origin where the runner goes back to at the end; null keeps the current run's, or where they stand */
    void startEndless(Player player, EndlessMode mode, @Nullable Location origin) {
        ParkourSettings rules = settings.get();
        ParkourMessages text = messages.get();
        if (!mode.enabled(rules.modes())) {
            send(player, text.modeOff(), Values.of());
            return;
        }
        List<ParkourSettings.Area> areas = List.copyOf(rules.endless().areas().values());
        ParkourSettings.Area area = areas.isEmpty() ? null
                : areas.get(mode == EndlessMode.DAILY ? 0 : ThreadLocalRandom.current().nextInt(areas.size()));
        World world = area == null ? null : Bukkit.getWorld(area.world());
        Cell min = area == null ? null : cell(area.min());
        Cell max = area == null ? null : cell(area.max());
        Cell start = area == null ? null : cell(area.start());
        if (world == null || min == null || max == null || start == null) {
            if (area != null) context.debug().warn("A parkour endless area has no loaded world or a bad min, max or start");
            send(player, text.noArea(), Values.of());
            return;
        }
        long seed = mode == EndlessMode.DAILY ? ("daily|" + clock.get().key(PeriodClock.Period.DAILY)).hashCode()
                : ThreadLocalRandom.current().nextLong();
        List<ParkourThemes.Theme> palette = themes.get().themes().values().stream().filter(ParkourThemes.Theme::enabled).toList();
        if (palette.isEmpty()) palette = List.of(new ParkourThemes.Theme());
        List<ParkourThemes.Theme> chosen = palette;
        int offset = (int) Math.floorMod(seed, (long) palette.size());
        Location at = new Location(world, start.x() + 0.5, start.y() + 1, start.z() + 0.5, Math.floorMod(area.heading(), 4) * 90f - 90f, 0);
        PathGenerator generator = new PathGenerator(seed, new Bounds(min.x(), min.y(), min.z(), max.x(), max.y(), max.z()),
                start, area.heading(), rules.endless().tuning().tuning());
        onPlayer(player, () -> {
            Run old = runs.get(player.getUniqueId());
            Location home = origin != null ? origin : old instanceof EndlessRun endless ? endless.origin : player.getLocation();
            if (old != null) close(old, RunEnd.REPLACED);
            long best = data.best(player.getUniqueId(), new Board(mode.id(), Board.Period.ALL)).map(ParkourRecord::value).orElse(0L);
            teleport(player, at).thenAccept(result -> {
                if (result.isSuccess()) onPlayer(player, () -> {
                    if (player.isOnline()) open(new EndlessRun(this, player, mode, world, home, at, generator, chosen, offset, best));
                });
            });
        });
    }

    /** {@code x y z} → the block there; null when it is not three numbers. */
    static @Nullable Cell cell(String written) {
        String[] parts = written == null ? new String[0] : written.trim().split("[\\s,]+");
        if (parts.length != 3) return null;
        try {
            return new Cell((int) Math.floor(Double.parseDouble(parts[0])), (int) Math.floor(Double.parseDouble(parts[1])),
                    (int) Math.floor(Double.parseDouble(parts[2])));
        } catch (NumberFormatException bad) {
            return null;
        }
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
        hotbars.register(HotbarSet.of(EndlessRun.HOTBAR, List.of(restart, leave)));
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
        guard.protect(run);
        if (settings.get().runItems() && Hotbars.isAvailable()) {
            run.hotbarBefore = hotbars.showing(player).orElse("");
            hotbars.show(player, run.hotbar());
        }
        run.timer = context.tasks().runAtEntityTimer(player, 1, 1, () -> {
            if (!run.ended) run.tick();
        });
        context.visibility().refresh(player);
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
        guard.release(run);
        if (run.hotbarBefore != null) {
            if (run.hotbarBefore.isEmpty()) hotbars.hide(player);
            else hotbars.show(player, run.hotbarBefore);
        }
        try {
            run.ended(end);
        } catch (RuntimeException failed) {
            context.debug().error("Could not end the parkour run of " + player.getName(), failed);
        }
        context.visibility().refresh(player);
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
            case UNCONFIRMED -> reasons.unconfirmed();
            default -> reasons.teleport();
        };
    }

    /** A course run reached its finish with every checkpoint. Player's thread. */
    void finish(CourseRun run) {
        Player player = run.player;
        long millis = run.millis();
        List<Long> splits = List.copyOf(run.splits);
        close(run, RunEnd.FINISHED);
        double minimum = run.course.minimumTime();
        if (settings.get().antiCheat().minimumTime() && minimum > 0 && millis < minimum * 1000) {
            send(player, messages.get().tooFast(), run.values());
            String detail = run.id + " in " + Times.format(millis) + " (minimum " + Times.format((long) (minimum * 1000)) + ")";
            Logs.of(context.plugin()).record(LOG_TOO_FAST, player, detail);
            context.debug().warn("Refused a parkour finish of " + player.getName() + ": " + detail);
            return;
        }
        sound(player, settings.get().sounds().finish());
        data.record(player, run.id, millis, splits).whenComplete((outcome, failed) -> {
            if (failed != null) {
                context.debug().error("Could not record the parkour finish of " + player.getName() + " on " + run.id, failed);
                notSaved(player);
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

    /** An endless run ended by a fall or the sprint clock: record it and show the summary. Player's thread. */
    void scored(EndlessRun run) {
        Player player = run.player;
        ParkourMessages text = messages.get();
        Values values = run.values().put("retry", Values.of("command", label()).put("mode_id", run.mode.id()).apply(text.retry()));
        sound(player, settings.get().sounds().fail());
        Board board = new Board(run.mode.id(), Board.Period.ALL);
        if (run.score <= 0) {
            long best = data.best(player.getUniqueId(), board).map(ParkourRecord::value).orElse(0L);
            int rank = data.rank(player.getUniqueId(), board);
            send(player, text.endlessEnded(), values.put("record", text.noRecord()).put("best", best)
                    .put("rank", rank > 0 ? String.valueOf(rank) : text.unranked()));
            return;
        }
        data.record(player, run.mode.id(), run.score, List.of()).whenComplete((outcome, failed) -> {
            if (failed != null) {
                context.debug().error("Could not record the parkour " + run.mode.id() + " score of " + player.getName(), failed);
                notSaved(player);
                return;
            }
            onPlayer(player, () -> {
                if (!player.isOnline()) return;
                send(player, text.endlessEnded(), values.put("record", outcome.improved() ? text.newRecord() : text.noRecord())
                        .put("best", outcome.best().value())
                        .put("rank", outcome.rank() > 0 ? String.valueOf(outcome.rank()) : text.unranked()));
                if (outcome.improved() && outcome.previous() != null) sound(player, settings.get().sounds().record());
            });
        });
    }

    /** Nothing was recorded or rewarded: tell the runner. Any thread. */
    private void notSaved(Player player) {
        onPlayer(player, () -> {
            if (player.isOnline()) send(player, messages.get().notSaved(), Values.of());
        });
    }

    /** The first label of the player command, for the retry button. */
    private String label() {
        List<String> labels = settings.get().command();
        return labels.isEmpty() ? "parkour" : labels.get(0);
    }

    /** Sends a runner back where they were before an endless run. */
    void home(Player player, Location origin) {
        if (player.isOnline() && !context.stopping()) teleport(player, origin);
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

    /**
     * An endless runner stands on blocks only their client has: the server must not kick them for flying while
     * they are on the path. Away from it, the kick goes through.
     */
    @EventHandler(ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        if (event.getCause() == PlayerKickEvent.Cause.FLYING_PLAYER && runs.get(event.getPlayer().getUniqueId()) instanceof EndlessRun run
                && run.near(event.getPlayer().getLocation())) {
            event.setCancelled(true);
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
