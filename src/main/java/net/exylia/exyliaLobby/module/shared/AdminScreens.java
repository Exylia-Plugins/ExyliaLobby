package net.exylia.exyliaLobby.module.shared;

import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.Actions;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.input.Inputs;
import net.exylia.lib.input.PluginInputs;
import net.exylia.lib.ui.Menus;
import net.exylia.lib.ui.PluginMenus;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The one admin list screen (Admin list layout, 54 slots) the hub modules share: pads, NPCs, portals, holograms.
 *
 * <p>Each module owns one instance. It says what the rows are and what a click on one does; the screen, its
 * back button and its two add buttons are drawn from {@code menus/admin/list.yml}. The three actions it needs
 * ({@code screen_row}, {@code screen_add}, {@code screen_back}) are registered once and route by module id,
 * so they work whether or not their module is the last one that registered.
 */
public final class AdminScreens {

    public static final String MENU = "admin/list";

    private static final Map<String, AdminScreens> BY_ID = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicBoolean REGISTERED = new java.util.concurrent.atomic.AtomicBoolean();

    /** What a click on a row does. {@code click} is {@code left}, {@code right} or {@code shift}. */
    public interface RowClick {
        void on(Player viewer, String click, String id);
    }

    /** One add button: its card text and what it does. */
    public record Add(@NotNull String icon, @NotNull String title, @NotNull List<String> text, @NotNull String click,
                      @NotNull String next, @NotNull Consumer<Player> run) {
    }

    private final LobbyContext context;
    private final String id;
    private final RowClick rowClick;
    private final Map<UUID, Runnable> backs = new ConcurrentHashMap<>();
    private final Map<UUID, List<Add>> adds = new ConcurrentHashMap<>();

    public AdminScreens(@NotNull LobbyContext context, @NotNull String id, @NotNull RowClick rowClick) {
        this.context = context;
        this.id = id;
        this.rowClick = rowClick;
        BY_ID.put(id, this);
    }

    /** Loads the shared menu and registers the shared actions; call from the module's {@code start()}. */
    public void register() {
        context.hub().menu(MENU);
        PluginActions actions = Actions.of(context.plugin());
        // Shared by every module's screen: registered once for the plugin's life.
        if (!REGISTERED.compareAndSet(false, true)) return;
        actions.registerSync("screen_row", Permissions.ADMIN, (action, args) -> {
            AdminScreens screen = BY_ID.get(args.string(0, ""));
            if (screen != null) screen.rowClick.on(action.player(), args.string(1, ""), args.string(2, ""));
            return ActionResult.success();
        });
        actions.registerSync("screen_add", Permissions.ADMIN, (action, args) -> {
            AdminScreens screen = BY_ID.get(args.string(0, ""));
            List<Add> list = screen == null ? List.of() : screen.adds.getOrDefault(action.player().getUniqueId(), List.of());
            int index = args.integer(1, 0);
            if (index >= 0 && index < list.size()) list.get(index).run().accept(action.player());
            return ActionResult.success();
        });
        actions.registerSync("screen_back", Permissions.ADMIN, (action, args) -> {
            AdminScreens screen = BY_ID.get(args.string(0, ""));
            Runnable back = screen == null ? null : screen.backs.get(action.player().getUniqueId());
            if (back != null) back.run();
            else context.hub().open(action.player());
            return ActionResult.success();
        });
    }

    /** Forgets the viewers; call from {@code stop()}. */
    public void clear() {
        backs.clear();
        adds.clear();
        BY_ID.remove(id, this);
    }

    /**
     * Opens the list.
     *
     * @param backName the name of the screen {@code back} reopens, shown on the Back button
     * @param adds     one or two add buttons (slots 45 and 46); with one, slot 46 is frame
     */
    public void open(@NotNull Player viewer, @NotNull Runnable back, @NotNull String backName, @NotNull String title,
                     @NotNull String kind, @NotNull List<Add> adds, @NotNull Supplier<List<UiEntry>> rows) {
        backs.put(viewer.getUniqueId(), back);
        this.adds.put(viewer.getUniqueId(), adds);
        Add first = adds.get(0);
        Add second = adds.size() > 1 ? adds.get(1) : first;
        ui().open(viewer, MENU, Map.ofEntries(
                Map.entry("screen_id", id),
                Map.entry("list_title", title),
                Map.entry("list_kind", kind),
                Map.entry("back_name", backName),
                Map.entry("add1_icon", first.icon()),
                Map.entry("add1_title", first.title()),
                Map.entry("add1_text", String.join("<nl>", first.text())),
                Map.entry("add1_click", first.click()),
                Map.entry("add1_next", first.next()),
                Map.entry("add2_shown", String.valueOf(adds.size() > 1)),
                Map.entry("add2_icon", second.icon()),
                Map.entry("add2_title", second.title()),
                Map.entry("add2_text", String.join("<nl>", second.text())),
                Map.entry("add2_click", second.click()),
                Map.entry("add2_next", second.next())), rows);
    }

    /** Redraws the open list in place. */
    public void update(@NotNull Player viewer) {
        ui().update(viewer, MENU, Map.of());
    }

    /** One row of the list. {@code stats} and {@code clicks} are card lines, already formatted. */
    public static @NotNull UiEntry row(@NotNull String id, @NotNull String icon, @NotNull String name, @NotNull String subtitle,
                                       @NotNull List<String> stats, @NotNull List<String> clicks, boolean glow) {
        return UiEntry.of(id)
                .with("row_id", id)
                .with("row_icon", icon)
                .with("row_name", name)
                .withFormatted("row_subtitle", subtitle)
                .withFormatted("row_stats", String.join("<nl>", stats))
                .withFormatted("row_clicks", String.join("<nl>", clicks))
                .with("row_glow", String.valueOf(glow))
                .build();
    }

    /** A stat line of a card: {@code · icon Label: value}, the icon a named ExyliaLib sprite. */
    public static @NotNull String stat(@NotNull String icon, @NotNull String label, @NotNull String value) {
        return "  {letters_black}· {info}{icon:" + icon + "} {letters}" + label + "{letters_black}: " + value;
    }

    /** Closes the screen, asks the question and runs {@code yes} on a yes; {@code reopen} on no or cancel. */
    public void confirm(@NotNull Player viewer, @NotNull String question, @NotNull Runnable yes, @NotNull Runnable reopen) {
        viewer.closeInventory();
        inputs().answered(viewer, inputs().confirm(viewer, question).dangerous().open(), answer -> {
            if (answer) yes.run();
            reopen.run();
        }, reopen);
    }

    public PluginMenus ui() {
        return Menus.of(context.plugin());
    }

    public PluginInputs inputs() {
        return Inputs.of(context.plugin());
    }
}
