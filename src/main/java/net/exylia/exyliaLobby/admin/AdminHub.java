package net.exylia.exyliaLobby.admin;

import net.exylia.exyliaLobby.ExyliaLobby;
import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.config.LobbyMessages;
import net.exylia.exyliaLobby.config.ModuleNames;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.exyliaLobby.module.spawn.SpawnModule;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.Actions;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigMenus;
import net.exylia.lib.config.Languages;
import net.exylia.lib.text.Text;
import net.exylia.lib.ui.Menus;
import net.exylia.lib.ui.PluginMenus;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /lobbyadmin}: the hub, and the Modules screen where every module is switched live and opened.
 *
 * <p>Admin menus live under {@code lang/<language>/menus/admin/} and are regenerated from the jar on start.
 */
public final class AdminHub {

    public static final String HUB = "admin/hub";
    public static final String MODULES = "admin/modules";
    private static final String DIRECTORY = "menus/admin";
    private static final List<String> MENUS = List.of(HUB, MODULES);

    private final ExyliaLobby lobby;
    private final PluginMenus menus;
    private final List<String> extra = new ArrayList<>();

    public AdminHub(@NotNull ExyliaLobby lobby) {
        this.lobby = lobby;
        this.menus = Menus.of(lobby.getPlugin());
        registerActions();
    }

    private volatile boolean ready;

    /**
     * A menu a module ships ({@code menus/<id>.yml} of the server's language), loaded with the core's on start and
     * reload. Menus compile their action lines when loaded, so this waits until every module's actions exist: until
     * the core's first {@link #load()} at start, and one tick when a module is switched on live (its actions are
     * registered after its start).
     */
    public void menu(@NotNull String id) {
        if (!extra.contains(id)) extra.add(id);
        if (ready) lobby.getTasks().run(() -> load(id));
    }

    public void load() {
        Languages.replace(lobby.getPlugin(), AdminHub.class, DIRECTORY);
        for (String id : MENUS) load(id);
        for (String id : extra) load(id);
        ready = true;
    }

    private void load(String id) {
        File file = Languages.file(lobby.getPlugin(), "menus/" + id + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            menus.load(id, yaml);
        } catch (IOException | InvalidConfigurationException failure) {
            lobby.getDebug().error("The " + id + " menu could not be read. Fix " + file.getPath() + " and reload.", failure);
        }
    }

    public void open(@NotNull Player viewer) {
        menus.open(viewer, HUB, Map.of(
                "modules_on", (java.util.function.Supplier<String>) () -> String.valueOf(lobby.getRegistered().stream().filter(LobbyModule::isEnabled).count()),
                "modules_total", String.valueOf(lobby.getRegistered().size()),
                "lobby_players", (java.util.function.Supplier<String>) () -> String.valueOf(lobby.getAreas().players().size())));
    }

    public void openModules(@NotNull Player viewer) {
        menus.open(viewer, MODULES, Map.of(), this::moduleRows);
    }

    private List<UiEntry> moduleRows() {
        LobbyMessages.Modules text = lobby.getMessages().get().modules();
        ModuleNames names = lobby.getNames().get();
        List<UiEntry> rows = new ArrayList<>();
        for (LobbyModule module : lobby.getRegistered()) {
            ModuleNames.Info info = names.of(module.getId());
            String id = module.getId();
            rows.add(UiEntry.of(id)
                    .with("module_id", id)
                    .with("module_icon", module.icon().name())
                    .withFormatted("module_name", info.name())
                    .withFormatted("module_description", String.join("<nl>", info.description().stream().map(line -> " {letters}" + line).toList()))
                    .withFormatted("module_state", () -> module.isEnabled() ? text.on() : text.off())
                    .with("module_glow", () -> String.valueOf(module.isEnabled()))
                    .build());
        }
        return rows;
    }

    private void registerActions() {
        PluginActions actions = Actions.of(lobby.getPlugin());
        actions.registerSync("admin_hub", Permissions.ADMIN, (action, args) -> {
            open(action.player());
            return ActionResult.success();
        });
        actions.registerSync("admin_modules", Permissions.ADMIN, (action, args) -> {
            openModules(action.player());
            return ActionResult.success();
        });
        actions.registerSync("admin_areas", Permissions.ADMIN, (action, args) -> {
            openAreas(action.player());
            return ActionResult.success();
        });
        actions.registerSync("admin_settings", Permissions.ADMIN, (action, args) -> {
            ConfigMenus.of(lobby.getPlugin()).permission(Permissions.ADMIN).back(this::open).open(action.player());
            return ActionResult.success();
        });
        actions.registerSync("admin_reload", Permissions.ADMIN, (action, args) -> {
            reload(action.player());
            return ActionResult.success();
        });
        actions.registerSync("module_toggle", Permissions.ADMIN, (action, args) -> {
            toggle(action.player(), args.string(0, ""));
            return ActionResult.success();
        });
        actions.registerSync("module_open", Permissions.ADMIN, (action, args) -> {
            openModule(action.player(), args.string(0, ""));
            return ActionResult.success();
        });
    }

    /** The lobby areas editor, which lives in the spawn module. */
    private void openAreas(Player viewer) {
        Optional<SpawnModule> spawn = lobby.getRegistered().stream()
                .filter(module -> module instanceof SpawnModule && module.isEnabled())
                .map(SpawnModule.class::cast).findFirst();
        if (spawn.isPresent()) {
            spawn.get().openAreas(viewer, "Admin", () -> open(viewer));
            return;
        }
        net.exylia.lib.text.PluginMessages.of(lobby.getPlugin()).send(viewer, lobby.getMessages().get().moduleOff(),
                net.exylia.lib.text.Values.of("module", lobby.getNames().get().of(SpawnModule.ID).name()));
    }

    public void reload(@NotNull Player viewer) {
        lobby.getReloads().run(viewer);
    }

    private Optional<LobbyModule> module(String id) {
        return lobby.getRegistered().stream().filter(module -> module.getId().equals(id)).findFirst();
    }

    /** Opens a module's own admin screen when it has one and runs, else its settings file. */
    private void openModule(Player viewer, String id) {
        module(id).ifPresent(module -> {
            if (module.isEnabled() && module.hasAdmin()) {
                module.openAdmin(viewer, () -> openModules(viewer));
                return;
            }
            ModuleSwitches.openSettings(lobby, viewer, module, () -> openModules(viewer));
        });
    }

    private void toggle(Player viewer, String id) {
        module(id).ifPresent(module -> {
            ModuleSwitches.toggle(lobby, viewer, module, null, () -> menus.update(viewer, MODULES, Map.of()));
        });
    }
}
