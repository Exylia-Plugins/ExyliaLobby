package net.exylia.exyliaLobby.module.parkour;

import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.module.shared.AdminScreens;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.input.Inputs;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.Menus;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The staff screens: a hub, the course list and editor, the endless areas and the themes. Every edit goes through
 * the same {@link ParkourModule} methods the {@code /lobbyadmin parkour} commands use.
 */
final class ParkourAdminScreens {

    static final String HUB = "admin/parkour/hub";
    static final String EDITOR = "admin/parkour/editor";

    private final ParkourModule module;
    private final Map<UUID, Runnable> backs = new ConcurrentHashMap<>();
    private AdminScreens courses;
    private AdminScreens areas;
    private AdminScreens themes;

    ParkourAdminScreens(ParkourModule module) {
        this.module = module;
    }

    void register() {
        module.ctx().hub().menu(HUB);
        module.ctx().hub().menu(EDITOR);
        courses = new AdminScreens(module.ctx(), "parkour", this::courseClick);
        courses.register();
        areas = new AdminScreens(module.ctx(), "parkour-areas", this::areaClick);
        areas.register();
        themes = new AdminScreens(module.ctx(), "parkour-themes", this::themeClick);
        themes.register();
    }

    void clear() {
        backs.clear();
        for (AdminScreens screen : new AdminScreens[]{courses, areas, themes}) {
            if (screen != null) screen.clear();
        }
    }

    void registerActions(PluginActions actions) {
        actions.registerSync("parkour_admin_open", Permissions.ADMIN, (action, args) -> {
            Player admin = action.player();
            switch (args.string(0, "")) {
                case "courses" -> openCourses(admin);
                case "areas" -> openAreas(admin);
                case "themes" -> openThemes(admin);
                default -> {
                }
            }
            return ActionResult.success();
        });
        actions.registerSync("parkour_admin_back", Permissions.ADMIN, (action, args) -> {
            backs.getOrDefault(action.player().getUniqueId(), () -> module.ctx().hub().open(action.player())).run();
            return ActionResult.success();
        });
        actions.registerSync("parkour_edit", Permissions.ADMIN, (action, args) -> {
            edit(action.player(), args.string(0, ""), args.string(1, ""));
            return ActionResult.success();
        });
    }

    // ======================== hub ========================

    void openHub(Player admin, Runnable back) {
        backs.put(admin.getUniqueId(), back);
        Menus.of(module.ctx().plugin()).open(admin, HUB, Map.of(
                "courses_total", (Supplier<String>) () -> String.valueOf(module.catalogue().courses().size()),
                "courses_ready", (Supplier<String>) () -> String.valueOf(module.catalogue().courses().values().stream()
                        .filter(ParkourCourses.Course::ready).count()),
                "areas_total", (Supplier<String>) () -> String.valueOf(module.rules().endless().areas().size()),
                "themes_on", (Supplier<String>) () -> String.valueOf(module.themeSet().themes().values().stream()
                        .filter(ParkourThemes.Theme::enabled).count())));
    }

    private void hub(Player admin) {
        openHub(admin, backs.getOrDefault(admin.getUniqueId(), () -> module.ctx().hub().open(admin)));
    }

    private void settings(Player admin, Runnable reopen) {
        module.ctx().hub().openSettings(admin, module.getId());
    }

    // ======================== courses ========================

    void openCourses(Player admin) {
        ParkourMessages.Staff t = module.text().staff();
        courses.open(admin, () -> hub(admin), t.back(), t.titleCourses(), t.kindCourse(), List.of(
                button("WRITABLE_BOOK", t.createCourse(), this::create),
                button("COMPARATOR", t.settingsCourses(), player -> settings(player, () -> openCourses(player)))),
                () -> courseRows());
    }

    private List<UiEntry> courseRows() {
        ParkourMessages.Screens t = module.text().screens();
        ParkourMessages.Staff staff = module.text().staff();
        List<UiEntry> rows = new ArrayList<>();
        module.catalogue().courses().forEach((id, course) -> rows.add(AdminScreens.row(id, icon(course.icon(), "FEATHER"), course.name(),
                course.ready() ? staff.subtitleCourseReady() : staff.subtitleCourseSetup(),
                List.of(AdminScreens.stat("star", staff.labelDifficulty(), "{info}" + course.difficulty()),
                        AdminScreens.stat("check", staff.labelCheckpoints(), "{info}" + course.checkpoints().size()),
                        AdminScreens.stat("redstone", staff.labelState(), course.enabled() ? t.on() : t.off())),
                staff.clicksCourse(), course.enabled())));
        return rows;
    }

    private void courseClick(Player admin, String click, String id) {
        switch (click) {
            case "left" -> openEditor(admin, id);
            case "right" -> {
                module.toggleCourse(admin, id);
                courses.update(admin);
            }
            case "shift" -> courses.confirm(admin, Values.of("course", id).apply(module.text().tools().confirmDelete()),
                    () -> module.delete(admin, id, deleted -> openCourses(admin)), () -> openCourses(admin));
            default -> {
            }
        }
    }

    private void create(Player admin) {
        admin.closeInventory();
        var inputs = Inputs.of(module.ctx().plugin());
        Runnable reopen = () -> openCourses(admin);
        inputs.answered(admin, inputs.slug(admin, module.text().screens().promptCourse())
                .validate(name -> !module.catalogue().courses().containsKey(ParkourCourses.id(name)),
                        Values.of("course", "").apply(module.text().courseExists()))
                .open(), name -> module.create(admin, name, reopen), reopen);
    }

    // ======================== editor ========================

    void openEditor(Player admin, String id) {
        ParkourMessages.Screens t = module.text().screens();
        Supplier<ParkourCourses.Course> course = () -> {
            ParkourCourses.Course found = module.catalogue().courses().get(id);
            return found == null ? new ParkourCourses.Course() : found;
        };
        Menus.of(module.ctx().plugin()).open(admin, EDITOR, Map.ofEntries(
                Map.entry("course_id", id),
                Map.entry("course_name", (Supplier<String>) () -> course.get().name()),
                Map.entry("course_state", (Supplier<String>) () -> course.get().ready() ? t.ready() : t.notReady()),
                Map.entry("start_state", (Supplier<String>) () -> course.get().start().placed() ? t.placed() : t.notPlaced()),
                Map.entry("finish_state", (Supplier<String>) () -> course.get().finish().placed() ? t.placed() : t.notPlaced()),
                Map.entry("checkpoints", (Supplier<String>) () -> String.valueOf(course.get().checkpoints().size())),
                Map.entry("kills", (Supplier<String>) () -> String.valueOf(course.get().killBlocks().size())),
                Map.entry("medals", (Supplier<String>) () -> {
                    ParkourCourses.Medals medals = course.get().medals();
                    return trim(medals.gold()) + "{letters_black}/{info}" + trim(medals.silver()) + "{letters_black}/{info}" + trim(medals.bronze());
                }),
                Map.entry("difficulty", (Supplier<String>) () -> course.get().difficulty()),
                Map.entry("enabled", (Supplier<String>) () -> course.get().enabled() ? t.on() : t.off())));
    }

    private static String trim(double seconds) {
        return seconds == Math.floor(seconds) ? String.valueOf((long) seconds) : String.valueOf(seconds);
    }

    private void edit(Player admin, String op, String id) {
        if (!module.catalogue().courses().containsKey(id)) {
            openCourses(admin);
            return;
        }
        Runnable reopen = () -> openEditor(admin, id);
        switch (op) {
            case "start" -> {
                module.setStart(admin, id, reopen);
                return;
            }
            case "checkpoint" -> {
                module.addCheckpoint(admin, id, reopen);
                return;
            }
            case "finish" -> {
                module.setFinish(admin, id, reopen);
                return;
            }
            case "kill" -> {
                module.addKill(admin, id, reopen);
                return;
            }
            case "board" -> {
                module.board(admin, id, reopen);
                return;
            }
            case "enabled" -> module.toggleCourse(admin, id);
            case "back" -> {
                openCourses(admin);
                return;
            }
            case "difficulty" -> {
                difficulty(admin, id, reopen);
                return;
            }
            case "medals" -> {
                medals(admin, id, reopen);
                return;
            }
            case "delete" -> {
                admin.closeInventory();
                module.delete(admin, id, deleted -> {
                    if (deleted) openCourses(admin);
                    else reopen.run();
                });
                return;
            }
            default -> {
                return;
            }
        }
        Menus.of(module.ctx().plugin()).update(admin, EDITOR, Map.of());
    }

    private void difficulty(Player admin, String id, Runnable reopen) {
        admin.closeInventory();
        var inputs = Inputs.of(module.ctx().plugin());
        inputs.answered(admin, inputs.text(admin, module.text().screens().promptDifficulty()).maxLength(16)
                .defaultValue(module.catalogue().courses().get(id).difficulty()).open(), value -> {
            module.setDifficulty(admin, id, value.trim());
            reopen.run();
        }, reopen);
    }

    private void medals(Player admin, String id, Runnable reopen) {
        ParkourMessages.Screens t = module.text().screens();
        ParkourCourses.Medals now = module.catalogue().courses().get(id).medals();
        admin.closeInventory();
        number(admin, t.promptGold(), now.gold(), reopen, gold ->
                number(admin, t.promptSilver(), now.silver(), reopen, silver ->
                        number(admin, t.promptBronze(), now.bronze(), reopen, bronze -> {
                            module.setMedals(admin, id, gold, silver, bronze);
                            reopen.run();
                        })));
    }

    private void number(Player admin, String prompt, double current, Runnable cancel, java.util.function.DoubleConsumer next) {
        var inputs = Inputs.of(module.ctx().plugin());
        inputs.answered(admin, inputs.decimal(admin, prompt).range(BigDecimal.ZERO, BigDecimal.valueOf(36000))
                .defaultValue(BigDecimal.valueOf(current)).open(), value -> next.accept(value.doubleValue()), cancel);
    }

    // ======================== endless areas ========================

    void openAreas(Player admin) {
        ParkourMessages.Staff t = module.text().staff();
        areas.open(admin, () -> hub(admin), t.back(), t.titleAreas(), t.kindArea(), List.of(
                button("GRASS_BLOCK", t.addArea(), this::addArea),
                button("COMPARATOR", t.settingsAreas(), player -> settings(player, () -> openAreas(player)))),
                () -> areaRows());
    }

    private List<UiEntry> areaRows() {
        ParkourMessages.Staff t = module.text().staff();
        List<UiEntry> rows = new ArrayList<>();
        module.rules().endless().areas().forEach((id, area) -> rows.add(AdminScreens.row(id, "GRASS_BLOCK", id, t.subtitleArea(),
                List.of(AdminScreens.stat("map", t.labelWorld(), "{info}" + area.world()), AdminScreens.stat("compass", t.labelStart(), "{info}" + area.start())),
                t.clicksArea(), false)));
        return rows;
    }

    private void areaClick(Player admin, String click, String id) {
        if (click.equals("right")) {
            admin.closeInventory();
            module.deleteArea(admin, id, () -> openAreas(admin));
        }
    }

    private void addArea(Player admin) {
        admin.closeInventory();
        var inputs = Inputs.of(module.ctx().plugin());
        inputs.answered(admin, inputs.slug(admin, module.text().screens().promptArea()).open(), name -> module.endlessArea(admin, name, () -> openAreas(admin)),
                () -> openAreas(admin));
    }

    // ======================== themes ========================

    void openThemes(Player admin) {
        ParkourMessages.Staff t = module.text().staff();
        themes.open(admin, () -> hub(admin), t.back(), t.titleThemes(), t.kindTheme(), List.of(
                button("LIME_DYE", t.enableThemes(), player -> {
                    module.enableAllThemes();
                    themes.update(player);
                })),
                () -> themeRows());
    }

    private List<UiEntry> themeRows() {
        ParkourMessages.Screens t = module.text().screens();
        ParkourMessages.Staff staff = module.text().staff();
        List<UiEntry> rows = new ArrayList<>();
        module.themeSet().themes().forEach((id, theme) -> rows.add(AdminScreens.row(id, icon(theme.floor(), "STONE"), id, staff.subtitleTheme(),
                List.of(AdminScreens.stat("redstone", staff.labelState(), theme.enabled() ? t.on() : t.off()),
                        AdminScreens.stat("pickaxe", staff.labelFloor(), "{info}" + theme.floor())),
                staff.clicksTheme(), theme.enabled())));
        return rows;
    }

    private void themeClick(Player admin, String click, String id) {
        if (!click.equals("left")) return;
        module.toggleTheme(admin, id);
        themes.update(admin);
    }

    private static AdminScreens.Add button(String icon, ParkourMessages.Button text, java.util.function.Consumer<Player> run) {
        return new AdminScreens.Add(icon, text.title(), text.text(), text.click(), text.next(), run);
    }

    /** A block or item name as a material, with block data after {@code [} dropped; the fallback when it is not one. */
    private static String icon(String written, String fallback) {
        String name = written == null ? "" : written.split("\\[")[0];
        Material material = Material.matchMaterial(name);
        return material == null || !material.isItem() ? fallback : material.name();
    }
}
