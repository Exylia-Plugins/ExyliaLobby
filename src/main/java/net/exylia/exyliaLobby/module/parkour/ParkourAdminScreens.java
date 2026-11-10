package net.exylia.exyliaLobby.module.parkour;

import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.module.shared.AdminScreens;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigMenus;
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
    private static final String BACK_NAME = "Parkour";

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
        ConfigMenus.of(module.ctx().plugin()).permission(Permissions.ADMIN).back(player -> reopen.run())
                .file(module.settingsPath()).open(admin);
    }

    // ======================== courses ========================

    void openCourses(Player admin) {
        courses.open(admin, () -> hub(admin), BACK_NAME, "Courses", "Course", List.of(
                new AdminScreens.Add("WRITABLE_BOOK", "Create", List.of(" {letters}A new course with its start", " {letters}at your feet."),
                        "Click to create", "Opens its editor next.", player -> create(player)),
                new AdminScreens.Add("COMPARATOR", "Settings", List.of(" {letters}Modes, anti-cheat, boards", " {letters}and the rest."),
                        "Click to open", "Every parkour setting.", player -> settings(player, () -> openCourses(player)))),
                () -> courseRows());
    }

    private List<UiEntry> courseRows() {
        ParkourMessages.Screens t = module.text().screens();
        List<UiEntry> rows = new ArrayList<>();
        module.catalogue().courses().forEach((id, course) -> rows.add(AdminScreens.row(id, icon(course.icon(), "FEATHER"), course.name(),
                course.ready() ? "Course · ready" : "Course · setup needed",
                List.of(AdminScreens.stat("Difficulty", "{info}" + course.difficulty()),
                        AdminScreens.stat("Checkpoints", "{info}" + course.checkpoints().size()),
                        AdminScreens.stat("State", course.enabled() ? t.on() : t.off())),
                List.of("{primary}{icon:click} Left-click {letters_black}· {letters}edit",
                        "{primary}{icon:right_click} Right-click {letters_black}· {letters}turn on or off",
                        "{error}{icon:click} Shift-right-click {letters_black}· {letters}delete",
                        "{letters_black}You will be asked to confirm."),
                course.enabled())));
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
                .open(), name -> {
            module.create(admin, name);
            openEditor(admin, ParkourCourses.id(name));
        }, reopen);
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
            case "start" -> module.setStart(admin, id);
            case "checkpoint" -> module.addCheckpoint(admin, id);
            case "finish" -> module.setFinish(admin, id);
            case "kill" -> module.addKill(admin, id);
            case "enabled" -> module.toggleCourse(admin, id);
            case "board" -> module.board(admin, id);
            case "back" -> {
                openCourses(admin);
                return;
            }
            case "rewards" -> {
                ConfigMenus.of(module.ctx().plugin()).permission(Permissions.ADMIN).back(player -> reopen.run())
                        .file("modules/parkour/courses").open(admin);
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
        areas.open(admin, () -> hub(admin), BACK_NAME, "Endless areas", "Area", List.of(
                new AdminScreens.Add("GRASS_BLOCK", "Add", List.of(" {letters}Select two corners with your", " {letters}wand, starting where you stand."),
                        "Click to add", "You name it after selecting.", player -> addArea(player)),
                new AdminScreens.Add("COMPARATOR", "Settings", List.of(" {letters}Path tuning, milestones", " {letters}and the endless modes."),
                        "Click to open", "Every parkour setting.", player -> settings(player, () -> openAreas(player)))),
                () -> areaRows());
    }

    private List<UiEntry> areaRows() {
        List<UiEntry> rows = new ArrayList<>();
        module.rules().endless().areas().forEach((id, area) -> rows.add(AdminScreens.row(id, "GRASS_BLOCK", id, "Endless area",
                List.of(AdminScreens.stat("World", "{info}" + area.world()), AdminScreens.stat("Start", "{info}" + area.start())),
                List.of("{error}{icon:right_click} Right-click {letters_black}· {letters}delete", "{letters_black}You will be asked to confirm."),
                false)));
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
        inputs.answered(admin, inputs.slug(admin, module.text().screens().promptArea()).open(), name -> module.endlessArea(admin, name), () -> openAreas(admin));
    }

    // ======================== themes ========================

    void openThemes(Player admin) {
        themes.open(admin, () -> hub(admin), BACK_NAME, "Themes", "Theme", List.of(
                new AdminScreens.Add("PAINTING", "Edit blocks", List.of(" {letters}The blocks each theme", " {letters}draws its path with."),
                        "Click to open", "Opens the themes file.", player -> ConfigMenus.of(module.ctx().plugin())
                                .permission(Permissions.ADMIN).back(back -> openThemes(player)).file("modules/parkour/themes").open(player)),
                new AdminScreens.Add("LIME_DYE", "Enable all", List.of(" {letters}Turns every theme on."),
                        "Click to enable", "Endless paths use them all.", player -> {
                            module.enableAllThemes();
                            themes.update(player);
                        })),
                () -> themeRows());
    }

    private List<UiEntry> themeRows() {
        ParkourMessages.Screens t = module.text().screens();
        List<UiEntry> rows = new ArrayList<>();
        module.themeSet().themes().forEach((id, theme) -> rows.add(AdminScreens.row(id, icon(theme.floor(), "STONE"), id, "Endless theme",
                List.of(AdminScreens.stat("State", theme.enabled() ? t.on() : t.off()), AdminScreens.stat("Floor", "{info}" + theme.floor())),
                List.of("{primary}{icon:click} Click {letters_black}· {letters}turn on or off"), theme.enabled())));
        return rows;
    }

    private void themeClick(Player admin, String click, String id) {
        if (!click.equals("left")) return;
        module.toggleTheme(admin, id);
        themes.update(admin);
    }

    /** A block or item name as a material, with block data after {@code [} dropped; the fallback when it is not one. */
    private static String icon(String written, String fallback) {
        String name = written == null ? "" : written.split("\\[")[0];
        Material material = Material.matchMaterial(name);
        return material == null || !material.isItem() ? fallback : material.name();
    }
}
