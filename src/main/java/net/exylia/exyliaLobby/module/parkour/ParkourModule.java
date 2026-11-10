package net.exylia.exyliaLobby.module.parkour;

import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.module.Busy;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.exyliaLobby.module.holograms.HologramsModule;
import net.exylia.exyliaLobby.module.spawn.SpawnModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.input.Inputs;
import net.exylia.lib.region.Regions;
import net.exylia.lib.task.TaskHandle;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.PeriodClock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Parkour courses and endless modes: runs, bests, leaderboards, anti-cheat and the staff tools that place them.
 *
 * <p>Everything a run does lives in {@link ParkourRuns}; this class wires it to the lobby and holds the commands'
 * logic.
 */
public final class ParkourModule extends LobbyModule implements Busy {

    public static final String ID = "parkour";
    public static final String PERMISSION = "exylialobby.parkour";
    private static final String RULE = "parkour";
    private static final String BOARD_PREFIX = "parkour-";
    private static final int BOARD_ROWS = 10;

    private ConfigFile<ParkourSettings> settings;
    private ConfigFile<ParkourMessages> messages;
    private ConfigFile<ParkourCourses> courses;
    private ConfigFile<ParkourThemes> themes;
    private final ParkourScreens screens = new ParkourScreens(this);
    private final ParkourAdminScreens adminScreens = new ParkourAdminScreens(this);
    private volatile ParkourRuns runs;
    private volatile ParkourData data;
    private volatile PeriodClock clock;
    private Runnable rescueHandle;
    private TaskHandle refresher;
    private final Set<String> boards = ConcurrentHashMap.newKeySet();

    public ParkourModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.FEATHER;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(ParkourSettings.class);
        messages = messages(ParkourMessages.class);
        courses = config("modules/parkour/courses", ParkourCourses.class).load();
        themes = config("modules/parkour/themes", ParkourThemes.class).load();
    }

    @Override
    protected void start() {
        clock = buildClock();
        data = ParkourData.create(context.plugin(), context.debug(), () -> clock, () -> settings.get().tops().size());
        data.register(context.plugin());
        screens.register();
        adminScreens.register();
        runs = new ParkourRuns(context, settings, courses, themes, messages, data, () -> clock);
        listen(runs);
        listen(runs.guard());
        context.visibility().add(RULE, (viewer, target) ->
                !settings.get().hideRunners() || viewer.equals(target) || !isRunning(target));
        context.registered(SpawnModule.ID, SpawnModule.class).ifPresent(spawn -> rescueHandle = spawn.rescue(100, runs::rescue));
        settings.onReload(reloaded -> {
            if (!isEnabled()) return;
            clock = buildClock();
            runs.reload();
            placeBoards();
        });
        long period = Math.max(5, settings.get().tops().refreshSeconds()) * 20L;
        refresher = context.tasks().runTimer(20L, period, this::refreshTops);
        placeBoards();
    }

    @Override
    protected void stop() {
        if (refresher != null) refresher.cancel();
        refresher = null;
        if (rescueHandle != null) rescueHandle.run();
        rescueHandle = null;
        context.visibility().remove(RULE);
        screens.clear();
        adminScreens.clear();
        clearBoards();
        if (runs != null) runs.close();
        runs = null;
    }

    private PeriodClock buildClock() {
        ParkourSettings.Periods periods = settings.get().periods();
        DayOfWeek start;
        try {
            start = DayOfWeek.valueOf(periods.weekStart().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException bad) {
            start = DayOfWeek.MONDAY;
        }
        return PeriodClock.of(periods.timezone(), periods.resetHour(), start);
    }

    private void refreshTops() {
        data.refreshAll(courses.get().courses().keySet());
    }

    @Override
    protected void enter(@NotNull Player player, @NotNull AreaCause cause) {
        data.load(player.getUniqueId());
    }

    @Override
    protected void leave(@NotNull Player player, @NotNull AreaCause cause) {
        ParkourRuns current = runs; // null once the module stopped: its runs were closed then
        if (current != null) current.stop(player, cause == AreaCause.DISABLE ? RunEnd.DISABLED : RunEnd.LOBBY);
        data.forget(player.getUniqueId());
    }

    // ======================== screens ========================

    @Override
    protected void actions(net.exylia.lib.action.PluginActions actions) {
        screens.registerActions(actions);
        adminScreens.registerActions(actions);
    }

    @Override
    public boolean hasAdmin() {
        return true;
    }

    @Override
    public void openAdmin(@NotNull Player viewer, @NotNull Runnable back) {
        adminScreens.openHub(viewer, back);
    }

    LobbyContext ctx() {
        return context;
    }

    ParkourScreens screens() {
        return screens;
    }

    ParkourAdminScreens adminScreens() {
        return adminScreens;
    }

    ParkourData data() {
        return data;
    }

    ParkourMessages text() {
        return messages.get();
    }

    ParkourSettings rules() {
        return settings.get();
    }

    ParkourCourses catalogue() {
        return courses.get();
    }

    ParkourThemes themeSet() {
        return themes.get();
    }

    ConfigFile<ParkourCourses> coursesFile() {
        return courses;
    }

    ConfigFile<ParkourThemes> themesFile() {
        return themes;
    }

    ConfigFile<ParkourSettings> settingsFile() {
        return settings;
    }

    /** The shown name of a course, its id when unknown. */
    String courseName(String id) {
        ParkourCourses.Course course = courses.get().courses().get(id);
        return course == null ? id : course.name();
    }

    // ======================== public ========================

    /** Whether the player is on a run now. Safe from any thread. */
    public boolean isRunning(@NotNull Player player) {
        ParkourRuns current = runs;
        return current != null && current.isRunning(player);
    }

    @Override
    public boolean busy(@NotNull Player player) {
        return isRunning(player);
    }

    // ======================== player commands ========================

    @Override
    protected @NotNull List<Object> commands() {
        return List.of(
                        command(settings.get().command(), new ParkourCommand(this)),
                        adminCommand(new ParkourAdminCommand(this))).stream()
                .flatMap(List::stream).toList();
    }

    void openCourses(Player player) {
        screens.openList(player);
    }

    void chatCourses(Player player) {
        List<String> ids = new ArrayList<>();
        courses.get().courses().forEach((id, course) -> {
            if (course.enabled() && course.ready()) ids.add("{highlight}" + id);
        });
        runs.send(player, messages.get().usage(), Values.of("courses", String.join("{letters_black}, ", ids))
                .put("command", label()));
    }

    /** Starts a mode (their ids come first) or a course. */
    void play(Player player, String typed) {
        if (!inLobby(player)) {
            runs.send(player, messages.get().notInLobby(), Values.of());
            return;
        }
        // A runner may start another run over theirs; anything else holding them (a fight) comes first.
        if (!runs.isRunning(player) && Busy.any(context, player)) {
            runs.send(player, messages.get().busy(), Values.of());
            return;
        }
        EndlessMode.parse(typed).ifPresentOrElse(mode -> runs.startEndless(player, mode),
                () -> runs.startCourse(player, typed));
    }

    void leaveRun(Player player) {
        if (runs.isRunning(player)) runs.stop(player, RunEnd.LEFT);
        else runs.send(player, messages.get().notRunning(), Values.of());
    }

    /** A player gets the leaderboard screen; the console gets chat lines. */
    void top(org.bukkit.command.CommandSender sender, String typed) {
        Board board = Board.parse(typed);
        ParkourMessages.Tools text = messages.get().tools();
        PluginMessages chat = PluginMessages.of(plugin());
        if (!board.isMode() && !courses.get().courses().containsKey(board.name())) {
            chat.send(sender, text.unknownBoard(), Values.of("board", typed));
            return;
        }
        if (sender instanceof Player player) {
            screens.openTop(player, board, () -> screens.openList(player), messages.get().screens().backList());
            return;
        }
        List<ParkourRecord> top = data.top(board);
        chat.send(sender, text.topTitle(), Values.of("board", board.id()));
        if (top.isEmpty()) chat.send(sender, text.topEmpty(), Values.of());
        for (int i = 0; i < Math.min(top.size(), BOARD_ROWS); i++) {
            chat.send(sender, text.topLine(), row(i, top.get(i), board));
        }
    }

    private static Values row(int index, ParkourRecord record, Board board) {
        return Values.of("place", index + 1).put("name", record.name())
                .put("value", board.isMode() ? Long.toString(record.value()) : Times.format(record.value()));
    }

    private String label() {
        List<String> labels = settings.get().command();
        return labels.isEmpty() ? "parkour" : labels.get(0);
    }

    // ======================== staff: courses ========================

    void create(Player admin, String typed) {
        String id = ParkourCourses.id(typed);
        if (id.isEmpty()) return;
        if (courses.get().courses().containsKey(id)) {
            send(admin, messages.get().courseExists(), Values.of("course", id));
            return;
        }
        ParkourCourses.Course course = new ParkourCourses.Course().inWorld(admin.getWorld().getName()).withStart(spot(admin));
        put(id, new ParkourCourses.Course(id, course.enabled(), course.difficulty(), course.icon(), course.world(), course.start(),
                course.checkpoints(), course.finish(), course.killBlocks(), course.timeLimit(), course.medals(),
                course.minimumTime(), course.firstRewards(), course.repeatRewards(), course.repeatCooldown()));
        send(admin, messages.get().courseCreated(), Values.of("course", id));
        refreshTops();
    }

    void setStart(Player admin, String id) {
        edit(admin, id, course -> course.withStart(spot(admin)), messages.get().startSet(), Values.of("course", id));
    }

    void addCheckpoint(Player admin, String id) {
        ParkourCourses.Course course = courses.get().courses().get(ParkourCourses.id(id));
        int number = course == null ? 1 : course.checkpoints().size() + 1;
        edit(admin, id, found -> found.withCheckpoint(spot(admin)), messages.get().checkpointAdded(),
                Values.of("course", id).put("checkpoint", number));
    }

    void setFinish(Player admin, String id) {
        edit(admin, id, course -> course.withFinish(spot(admin)), messages.get().finishSet(), Values.of("course", id));
    }

    void addKill(Player admin, String id) {
        Block block = admin.getTargetBlockExact(6);
        if (block == null) block = admin.getLocation().subtract(0, 1, 0).getBlock();
        if (block.getType().isAir()) {
            send(admin, messages.get().tools().killNoBlock(), Values.of());
            return;
        }
        String type = block.getType().name();
        edit(admin, id, course -> {
            List<String> kills = new ArrayList<>(course.killBlocks());
            if (!kills.contains(type)) kills.add(type);
            return withKills(course, kills);
        }, messages.get().tools().killAdded(), Values.of("course", id).put("block", type));
    }

    void setMedals(Player admin, String id, double gold, double silver, double bronze) {
        edit(admin, id, course -> withMedals(course, new ParkourCourses.Medals(gold, silver, bronze)),
                messages.get().tools().medalsSet(), Values.of("course", id));
    }

    void delete(Player admin, String typed) {
        delete(admin, typed, deleted -> {
        });
    }

    /** Asks first; {@code done} gets whether the course was deleted (false on no or cancel). */
    void delete(Player admin, String typed, java.util.function.Consumer<Boolean> done) {
        String id = ParkourCourses.id(typed);
        if (!courses.get().courses().containsKey(id)) {
            send(admin, messages.get().unknownCourse(), Values.of("course", typed));
            done.accept(false);
            return;
        }
        var inputs = Inputs.of(plugin());
        inputs.answered(admin, inputs.confirm(admin, Values.of("course", id).apply(messages.get().tools().confirmDelete()))
                .dangerous().open(), yes -> {
            if (!yes) {
                done.accept(false);
                return;
            }
            courses.update(current -> {
                Map<String, ParkourCourses.Course> map = new LinkedHashMap<>(current.courses());
                map.remove(id);
                return new ParkourCourses(map);
            });
            send(admin, messages.get().courseDeleted(), Values.of("course", id));
            done.accept(true);
        }, () -> done.accept(false));
    }

    void setDifficulty(Player admin, String id, String difficulty) {
        ParkourCourses.Course course = courses.get().courses().get(id);
        if (course == null) return;
        put(id, course.withDifficulty(difficulty));
        send(admin, messages.get().screens().difficultySet(), Values.of("course", id).put("difficulty", difficulty));
    }

    void toggleCourse(Player admin, String id) {
        ParkourCourses.Course course = courses.get().courses().get(id);
        if (course == null) return;
        put(id, course.withEnabled(!course.enabled()));
        ParkourMessages.Screens text = messages.get().screens();
        send(admin, text.enabledSet(), Values.of("course", id).put("state", course.enabled() ? text.off() : text.on()));
    }

    void toggleTheme(Player admin, String id) {
        ParkourThemes.Theme theme = themes.get().themes().get(id);
        if (theme == null) return;
        setThemes(map -> map.put(id, theme.withEnabled(!theme.enabled())));
        ParkourMessages.Screens text = messages.get().screens();
        send(admin, text.themeToggled(), Values.of("theme", id).put("state", theme.enabled() ? text.off() : text.on()));
    }

    void enableAllThemes() {
        setThemes(map -> map.replaceAll((key, theme) -> theme.withEnabled(true)));
    }

    private void setThemes(java.util.function.Consumer<Map<String, ParkourThemes.Theme>> change) {
        themes.update(current -> {
            Map<String, ParkourThemes.Theme> map = new LinkedHashMap<>(current.themes());
            change.accept(map);
            return new ParkourThemes(map);
        });
    }

    void deleteArea(Player admin, String name, Runnable reopen) {
        var inputs = Inputs.of(plugin());
        inputs.answered(admin, inputs.confirm(admin, Values.of("area", name).apply(messages.get().screens().confirmDeleteArea()))
                .dangerous().open(), yes -> {
            if (yes) {
                settings.update(current -> {
                    Map<String, ParkourSettings.Area> map = new LinkedHashMap<>(current.endless().areas());
                    map.remove(name);
                    ParkourSettings.Endless e = current.endless();
                    return current.withEndless(new ParkourSettings.Endless(map, e.ahead(), e.behind(), e.themeEvery(),
                            e.milestones(), e.fallDepth(), e.bestMarker(), e.milestoneRewards(), e.milestonePeriod(), e.tuning()));
                });
                send(admin, messages.get().screens().areaDeleted(), Values.of("area", name));
            }
            reopen.run();
        }, reopen);
    }

    private void edit(Player admin, String typed, UnaryOperator<ParkourCourses.Course> change, String done, Values values) {
        String id = ParkourCourses.id(typed);
        ParkourCourses.Course course = courses.get().courses().get(id);
        if (course == null) {
            send(admin, messages.get().unknownCourse(), Values.of("course", typed));
            return;
        }
        if (!course.world().equals(admin.getWorld().getName())) {
            send(admin, messages.get().wrongWorld(), Values.of("course", id).put("world", course.world()));
            return;
        }
        put(id, change.apply(course));
        send(admin, done, values);
    }

    private void put(String id, ParkourCourses.Course course) {
        courses.update(current -> {
            Map<String, ParkourCourses.Course> map = new LinkedHashMap<>(current.courses());
            map.put(id, course);
            return new ParkourCourses(map);
        });
    }

    private static ParkourCourses.Spot spot(Player admin) {
        Location at = admin.getLocation();
        return new ParkourCourses.Spot(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch(), new ParkourCourses.Spot().radius());
    }

    private static ParkourCourses.Course withKills(ParkourCourses.Course c, List<String> kills) {
        return new ParkourCourses.Course(c.name(), c.enabled(), c.difficulty(), c.icon(), c.world(), c.start(), c.checkpoints(),
                c.finish(), List.copyOf(kills), c.timeLimit(), c.medals(), c.minimumTime(), c.firstRewards(), c.repeatRewards(),
                c.repeatCooldown());
    }

    private static ParkourCourses.Course withMedals(ParkourCourses.Course c, ParkourCourses.Medals medals) {
        return new ParkourCourses.Course(c.name(), c.enabled(), c.difficulty(), c.icon(), c.world(), c.start(), c.checkpoints(),
                c.finish(), c.killBlocks(), c.timeLimit(), medals, c.minimumTime(), c.firstRewards(), c.repeatRewards(),
                c.repeatCooldown());
    }

    // ======================== staff: boards and areas ========================

    /** Saves a leaderboard hologram at the admin's feet and draws it. */
    void board(Player admin, String typed) {
        Board board = Board.parse(typed);
        if (!board.isMode() && !courses.get().courses().containsKey(board.name())) {
            send(admin, messages.get().tools().unknownBoard(), Values.of("board", typed));
            return;
        }
        Location at = admin.getLocation().add(0, 2.2, 0);
        ParkourSettings.BoardSpot spot = new ParkourSettings.BoardSpot(at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
        settings.update(current -> {
            Map<String, ParkourSettings.BoardSpot> map = new LinkedHashMap<>(current.boards());
            map.put(board.id(), spot);
            return withBoards(current, map);
        });
        placeBoards();
        send(admin, messages.get().tools().boardPlaced(), Values.of("board", board.id()));
    }

    private void placeBoards() {
        clearBoards();
        context.registered(HologramsModule.ID, HologramsModule.class).ifPresent(holograms ->
                settings.get().boards().forEach((id, spot) -> {
                    org.bukkit.World world = org.bukkit.Bukkit.getWorld(spot.world());
                    if (world == null) return;
                    Board board = Board.parse(id);
                    holograms.place(BOARD_PREFIX + id, new Location(world, spot.x(), spot.y(), spot.z()), () -> lines(board));
                    boards.add(id);
                }));
    }

    private void clearBoards() {
        context.registered(HologramsModule.ID, HologramsModule.class).ifPresent(holograms ->
                boards.forEach(id -> holograms.remove(BOARD_PREFIX + id)));
        boards.clear();
    }

    private List<String> lines(Board board) {
        ParkourMessages.Tools text = messages.get().tools();
        List<String> lines = new ArrayList<>();
        lines.add(Values.of("board", board.id()).apply(text.boardTitle()));
        List<ParkourRecord> top = data.top(board);
        if (top.isEmpty()) lines.add(text.boardEmpty());
        for (int i = 0; i < Math.min(top.size(), BOARD_ROWS); i++) lines.add(row(i, top.get(i), board).apply(text.boardLine()));
        return lines;
    }

    private static ParkourSettings withBoards(ParkourSettings s, Map<String, ParkourSettings.BoardSpot> boards) {
        return new ParkourSettings(s.command(), s.modes(), s.endless(), s.hideRunners(), s.checkpointBeacon(), s.runItems(),
                s.courseFallDepth(), s.antiCheat(), s.tops(), s.periods(), s.sounds(), boards);
    }

    /** Saves an endless area from the admin's selection, with the run starting where they stand. */
    void endlessArea(Player admin, String typed) {
        String name = ParkourCourses.id(typed);
        if (name.isEmpty()) return;
        send(admin, messages.get().tools().selectArea(), Values.of());
        Regions.of(plugin()).beginSelection(admin).result().whenComplete((result, error) ->
                context.tasks().runAtEntity(admin, () -> {
                    if (error != null || result == null) {
                        send(admin, messages.get().tools().noSelection(), Values.of());
                        return;
                    }
                    var a = result.first();
                    var b = result.second();
                    Location at = admin.getLocation();
                    int heading = Math.floorMod(Math.round((at.getYaw() + 90f) / 90f), 4);
                    ParkourSettings.Area area = new ParkourSettings.Area(result.world().fallbackName(),
                            Math.min(a.x(), b.x()) + " " + Math.min(a.y(), b.y()) + " " + Math.min(a.z(), b.z()),
                            Math.max(a.x(), b.x()) + " " + Math.max(a.y(), b.y()) + " " + Math.max(a.z(), b.z()),
                            at.getBlockX() + " " + (at.getBlockY() - 1) + " " + at.getBlockZ(), heading);
                    settings.update(current -> {
                        Map<String, ParkourSettings.Area> map = new LinkedHashMap<>(current.endless().areas());
                        map.put(name, area);
                        ParkourSettings.Endless e = current.endless();
                        return new ParkourSettings(current.command(), current.modes(), new ParkourSettings.Endless(map, e.ahead(),
                                e.behind(), e.themeEvery(), e.milestones(), e.fallDepth(), e.bestMarker(), e.milestoneRewards(), e.milestonePeriod(),
                                e.tuning()), current.hideRunners(), current.checkpointBeacon(), current.runItems(),
                                current.courseFallDepth(), current.antiCheat(), current.tops(), current.periods(),
                                current.sounds(), current.boards());
                    });
                    send(admin, messages.get().tools().areaSaved(), Values.of("area", name));
                }));
    }

    private void send(Player to, String line, Values values) {
        PluginMessages.of(plugin()).send(to, line, values);
    }
}
