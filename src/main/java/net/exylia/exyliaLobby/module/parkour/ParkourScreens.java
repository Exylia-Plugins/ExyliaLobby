package net.exylia.exyliaLobby.module.parkour;

import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.Languages;
import net.exylia.lib.input.Inputs;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.Menus;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The player screens: the course list, a course's detail and the leaderboard. Everything a lambda reads comes from
 * memory ({@link ParkourData} caches and the config files), never from the database.
 */
final class ParkourScreens {

    static final String LIST = "parkour/courses";
    static final String COURSE = "parkour/course";
    static final String TOP = "parkour/top";
    private static final String SUCCESS = "{success}";
    private static final String SUCCESS_LIGHT = "{success_light}";
    private static final String INFO = "{info}";
    private static final String INFO_LIGHT = "{info_light}";
    private static final String HIGHLIGHT = "{highlight}";
    private static final String HIGHLIGHT_LIGHT = "{warning_light}";
    private static final int SPLITS_SHOWN = 6;

    /** What a viewer's leaderboard shows, and where Back goes. */
    private record View(Board board, String title, String backName, Runnable back) {
    }

    private final ParkourModule module;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();

    ParkourScreens(ParkourModule module) {
        this.module = module;
    }

    void register() {
        Languages.refresh(module.ctx().plugin(), ParkourModule.class, "menus/parkour");
        for (String id : List.of(LIST, COURSE, TOP)) load(id);
    }

    private void load(String id) {
        module.ctx().hub().menu(id);
    }

    void clear() {
        views.clear();
    }

    void registerActions(PluginActions actions) {
        actions.registerSync("parkour_open", (action, args) -> {
            openList(action.player());
            return ActionResult.success();
        });
        actions.registerSync("parkour_play", (action, args) -> {
            action.player().closeInventory();
            module.play(action.player(), args.string(0, ""));
            return ActionResult.success();
        });
        actions.registerSync("parkour_details", (action, args) -> {
            String id = args.string(0, "");
            if (EndlessMode.parse(id).isPresent()) openTop(action.player(), new Board(id, Board.Period.ALL), () -> openList(action.player()), "Courses");
            else openCourse(action.player(), id);
            return ActionResult.success();
        });
        actions.registerSync("parkour_top", (action, args) -> {
            String id = args.string(0, "");
            openTop(action.player(), new Board(id, Board.Period.ALL), () -> openCourse(action.player(), id),
                    module.courseName(id));
            return ActionResult.success();
        });
        actions.registerSync("parkour_period", (action, args) -> {
            Player player = action.player();
            View view = views.get(player.getUniqueId());
            if (view == null) return ActionResult.success();
            Board.Period period = Board.Period.valueOf(args.string(0, "all").toUpperCase());
            views.put(player.getUniqueId(), new View(new Board(view.board().name(), period), view.title(), view.backName(), view.back()));
            Menus.of(module.ctx().plugin()).update(player, TOP, Map.of());
            return ActionResult.success();
        });
        actions.registerSync("parkour_list", (action, args) -> {
            openList(action.player());
            return ActionResult.success();
        });
        actions.registerSync("parkour_top_back", (action, args) -> {
            View view = views.get(action.player().getUniqueId());
            if (view != null) view.back().run();
            else openList(action.player());
            return ActionResult.success();
        });
        actions.registerSync("parkour_reset", (action, args) -> {
            reset(action.player(), args.string(0, ""));
            return ActionResult.success();
        });
    }

    // ======================== list ========================

    void openList(Player player) {
        UUID id = player.getUniqueId();
        Menus.of(module.ctx().plugin()).open(player, LIST, Map.of(
                "done", (Supplier<String>) () -> String.valueOf(finished(id)),
                "total", (Supplier<String>) () -> String.valueOf(playable().size()),
                "medals", (Supplier<String>) () -> String.valueOf(medals(id))), () -> listRows(player));
    }

    private List<Map.Entry<String, ParkourCourses.Course>> playable() {
        return module.catalogue().courses().entrySet().stream()
                .filter(entry -> entry.getValue().enabled() && entry.getValue().ready()).toList();
    }

    private int finished(UUID id) {
        return (int) playable().stream().filter(entry -> best(id, entry.getKey()).isPresent()).count();
    }

    private int medals(UUID id) {
        return (int) playable().stream().filter(entry -> best(id, entry.getKey())
                .map(record -> entry.getValue().medals().of(record.value())).isPresent()).count();
    }

    private Optional<ParkourRecord> best(UUID id, String name) {
        return module.data().best(id, new Board(name, Board.Period.ALL));
    }

    private List<UiEntry> listRows(Player player) {
        UUID id = player.getUniqueId();
        ParkourMessages text = module.text();
        ParkourMessages.Screens t = text.screens();
        List<UiEntry> rows = new ArrayList<>();
        for (Map.Entry<String, ParkourCourses.Course> entry : playable()) {
            String name = entry.getKey();
            ParkourCourses.Course course = entry.getValue();
            Optional<ParkourRecord> best = best(id, name);
            boolean done = best.isPresent();
            Values tone = tone(done ? SUCCESS : INFO, done ? SUCCESS_LIGHT : INFO_LIGHT);
            String[] colours = done ? new String[]{SUCCESS, SUCCESS_LIGHT} : new String[]{INFO, INFO_LIGHT};
            List<String> stats = new ArrayList<>();
            stats.add(stat(t.statDifficulty(), course.difficulty()));
            stats.add(stat(t.statBest(), best.map(record -> Times.format(record.value())).orElse(t.none())));
            stats.add(stat(t.statMedal(), best.map(record -> medal(text, course.medals().of(record.value()))).orElse(t.none())));
            stats.add(stat(t.statFinishes(), String.valueOf(module.data().completions(id, name))));
            rows.add(row(name, course.icon(), t.nameCourse(), subject(t, course.name(), colours), done ? t.subtitleCourseDone() : t.subtitleCourse(),
                    t.aboutCourse(), stats, tone.apply(t.clickCourse()), done, tone)
                    .with("row_kind", "course").withFormatted("row_title", course.name()).with("row_detail", course.difficulty())
                    .with("row_best", best.map(record -> Times.format(record.value())).orElse(""))
                    .withFormatted("row_medal", best.map(record -> medal(text, course.medals().of(record.value()))).orElse(""))
                    .with("row_runs", String.valueOf(module.data().completions(id, name))).build());
        }
        ParkourSettings.Modes modes = module.rules().modes();
        for (EndlessMode mode : EndlessMode.values()) {
            if (!mode.enabled(modes)) continue;
            Board board = new Board(mode.id(), Board.Period.ALL);
            Optional<ParkourRecord> best = module.data().best(id, board);
            boolean done = best.isPresent();
            Values tone = tone(done ? SUCCESS : INFO, done ? SUCCESS_LIGHT : INFO_LIGHT);
            String[] colours = done ? new String[]{SUCCESS, SUCCESS_LIGHT} : new String[]{INFO, INFO_LIGHT};
            List<String> stats = new ArrayList<>();
            stats.add(stat(t.statScore(), best.map(record -> String.valueOf(record.value())).orElse(t.none())));
            if (mode == EndlessMode.DAILY) {
                int rank = module.data().rank(id, board);
                stats.add(Values.of("label", t.labelToday()).put("value", rank == 0 ? t.none() : "#" + rank).apply(t.statRank()));
            }
            stats.add(stat(t.statFinishes(), String.valueOf(module.data().completions(id, mode.id()))));
            rows.add(row(mode.id(), modeIcon(mode), t.nameMode(), subject(t, mode.name(text), colours), switch (mode) {
                case ENDLESS -> t.subtitleEndless();
                case SPRINT -> t.subtitleSprint();
                case DAILY -> t.subtitleDaily();
            }, Values.of("seconds", modes.sprintSeconds()).apply(switch (mode) {
                case ENDLESS -> t.aboutEndless();
                case SPRINT -> t.aboutSprint();
                case DAILY -> t.aboutDaily();
            }), stats, tone.apply(t.clickMode()), done, tone)
                    .with("row_kind", "mode").withFormatted("row_title", mode.name(text))
                    .with("row_detail", mode == EndlessMode.SPRINT ? modes.sprintSeconds() + "s" : "")
                    .with("row_best", best.map(record -> String.valueOf(record.value())).orElse(""))
                    .withFormatted("row_medal", "")
                    .with("row_runs", String.valueOf(module.data().completions(id, mode.id()))).build());
        }
        return rows;
    }

    private static String modeIcon(EndlessMode mode) {
        return switch (mode) {
            case ENDLESS -> "SLIME_BLOCK";
            case SPRINT -> "SUGAR";
            case DAILY -> "CLOCK";
        };
    }

    /** The chest's card for a row, plus {@code row_state}; the caller adds the raw values a screen draws on its own. */
    private UiEntry.Builder row(String id, String icon, String nameTemplate, String subject, String subtitle, String about,
                                List<String> stats, String clicks, boolean glow, Values tone) {
        ParkourMessages.Screens t = module.text().screens();
        return UiEntry.of(id)
                .with("row_state", glow ? "done" : "new")
                .with("row_id", id)
                .with("row_icon", icon)
                .withFormatted("row_name", Values.of("subject", subject).apply(nameTemplate))
                .withFormatted("row_subtitle", subtitle)
                .withFormatted("row_label_info", tone.apply(t.labelInfo()))
                .withFormatted("row_about", about)
                .withFormatted("row_label_status", tone.apply(t.labelStatus()))
                .withFormatted("row_stats", String.join("<nl>", stats))
                .withFormatted("row_clicks", clicks)
                .with("row_glow", String.valueOf(glow));
    }

    // ======================== course detail ========================

    void openCourse(Player player, String id) {
        if (!module.catalogue().courses().containsKey(id)) {
            openList(player);
            return;
        }
        UUID uuid = player.getUniqueId();
        ParkourMessages text = module.text();
        ParkourMessages.Screens t = text.screens();
        Supplier<Optional<ParkourRecord>> best = () -> best(uuid, id);
        Menus.of(module.ctx().plugin()).open(player, COURSE, Map.of(
                "course_id", id,
                "course_name", (Supplier<String>) () -> module.courseName(id),
                "course_difficulty", (Supplier<String>) () -> course(id).difficulty(),
                "course_best", (Supplier<String>) () -> best.get().map(record -> Times.format(record.value())).orElse(t.none()),
                "course_medal", (Supplier<String>) () -> best.get().map(record -> medal(text, course(id).medals().of(record.value()))).orElse(t.none()),
                "course_finishes", (Supplier<String>) () -> String.valueOf(module.data().completions(uuid, id)),
                "course_splits", (Supplier<String>) () -> splits(id, best.get())));
    }

    private ParkourCourses.Course course(String id) {
        ParkourCourses.Course course = module.catalogue().courses().get(id);
        return course == null ? new ParkourCourses.Course() : course;
    }

    /** Your checkpoint times of your best run, then the finish against the gold medal. */
    private String splits(String id, Optional<ParkourRecord> best) {
        ParkourMessages text = module.text();
        ParkourMessages.Screens t = text.screens();
        if (best.isEmpty()) return t.splitsNone();
        ParkourRecord record = best.get();
        ParkourCourses.Course course = course(id);
        List<String> lines = new ArrayList<>();
        List<Long> splits = record.splits() == null ? List.of() : record.splits();
        for (int i = 0; i < Math.min(splits.size(), SPLITS_SHOWN); i++) {
            lines.add(Values.of("number", i + 1).put("time", Times.format(splits.get(i))).apply(t.splitLine()));
        }
        if (splits.size() > SPLITS_SHOWN) lines.add(Values.of("more", splits.size() - SPLITS_SHOWN).apply(t.splitMore()));
        lines.add(Values.of("time", Times.format(record.value()))
                .put("medal", medal(text, course.medals().of(record.value()))).apply(t.splitFinish()));
        double gold = course.medals().gold();
        if (gold > 0) {
            long delta = record.value() - Math.round(gold * 1000);
            lines.add(Values.of("time", Times.format(Math.round(gold * 1000)))
                    .put("delta", (delta <= 0 ? SUCCESS : "{error}") + Times.delta(delta)).apply(t.splitGold()));
        }
        return String.join("<nl>", lines);
    }

    private void reset(Player player, String id) {
        ParkourCourses.Course course = module.catalogue().courses().get(id);
        if (course == null) return;
        player.closeInventory();
        var inputs = Inputs.of(module.ctx().plugin());
        Runnable reopen = () -> openCourse(player, id);
        inputs.answered(player, inputs.confirm(player, Values.of("course", module.courseName(id))
                .apply(module.text().screens().confirmReset())).dangerous().open(), yes -> {
            if (!yes) {
                reopen.run();
                return;
            }
            module.data().reset(player.getUniqueId(), id).whenComplete((done, failed) -> module.ctx().tasks().runAtEntity(player, () -> {
                PluginMessages.of(module.ctx().plugin()).send(player, module.text().timeReset(), Values.of("course", module.courseName(id)));
                reopen.run();
            }));
        }, reopen);
    }

    // ======================== leaderboard ========================

    /** @param backName the screen Back returns to, shown on its button */
    void openTop(Player player, Board board, Runnable back, String backName) {
        UUID id = player.getUniqueId();
        ParkourMessages text = module.text();
        ParkourMessages.Screens t = text.screens();
        String title = board.isMode() ? EndlessMode.parse(board.name()).map(mode -> mode.name(text)).orElse(board.name()) : module.courseName(board.name());
        views.put(id, new View(board, title, backName, back));
        Map<String, Object> context = new java.util.HashMap<>();
        context.put("board_title", title);
        context.put("back_name", backName);
        context.put("period_name", (Supplier<String>) () -> t.periods().of(board(id).period()));
        for (Board.Period period : Board.Period.values()) {
            context.put("glow_" + period.id(), (Supplier<String>) () -> String.valueOf(board(id).period() == period));
        }
        context.put("my_rank", (Supplier<String>) () -> {
            int rank = module.data().rank(id, board(id));
            return rank == 0 ? text.unranked() : "#" + rank;
        });
        context.put("my_best", (Supplier<String>) () -> module.data().best(id, board(id))
                .map(record -> ParkourData.show(board(id), record.value())).orElse(t.none()));
        Menus.of(module.ctx().plugin()).open(player, TOP, context, () -> topRows(player));
    }

    private Board board(UUID id) {
        View view = views.get(id);
        return view == null ? new Board(Board.ENDLESS, Board.Period.ALL) : view.board();
    }

    private List<UiEntry> topRows(Player player) {
        UUID id = player.getUniqueId();
        ParkourMessages.Screens t = module.text().screens();
        View view = views.get(id);
        if (view == null) return List.of();
        Board board = view.board();
        List<UiEntry> rows = new ArrayList<>();
        List<ParkourRecord> top = module.data().top(board);
        for (int i = 0; i < top.size(); i++) {
            ParkourRecord record = top.get(i);
            boolean mine = record.player().equals(id);
            Values tone = mine ? tone(SUCCESS, SUCCESS_LIGHT) : tone(HIGHLIGHT, HIGHLIGHT_LIGHT);
            String stat = stat(board.isMode() ? t.statScore() : t.statBest(), ParkourData.show(board, record.value()));
            String rank = Values.of("label", t.labelRank()).put("value", "#" + (i + 1)).apply(t.statRank());
            rows.add(UiEntry.of(record.player().toString())
                    .with("row_icon", "playerhead-" + record.name())
                    .withFormatted("row_name", Values.of("place", i + 1).put("subject", tone.apply(t.gradient())
                            .replace("%subject%", record.name())).apply(t.nameRow()))
                    .withFormatted("row_subtitle", Values.of("board", view.title()).apply(t.subtitleTop()))
                    .withFormatted("row_label_status", tone.apply(t.labelStatus()))
                    .withFormatted("row_stats", rank + "<nl>" + stat)
                    .with("row_glow", String.valueOf(mine))
                    .build());
        }
        return rows;
    }

    // ======================== helpers ========================

    private static Values tone(String tone, String light) {
        return Values.of("tone", tone).put("light", light);
    }

    private static String stat(String template, String value) {
        return Values.of("value", value).apply(template);
    }

    /** A plain name takes the card's gradient; one that carries its own colours keeps them. */
    private static String subject(ParkourMessages.Screens t, String name, String[] colours) {
        boolean plain = name.indexOf('<') < 0 && name.indexOf('{') < 0 && name.indexOf('&') < 0;
        return plain ? Values.of("subject", name).put("tone", colours[0]).put("light", colours[1]).apply(t.gradient()) : name;
    }

    static String medal(ParkourMessages text, ParkourCourses.Medal medal) {
        if (medal == null) return text.screens().none();
        return switch (medal) {
            case GOLD -> text.medalGold();
            case SILVER -> text.medalSilver();
            case BRONZE -> text.medalBronze();
        };
    }
}
