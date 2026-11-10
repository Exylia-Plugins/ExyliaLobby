package net.exylia.exyliaLobby;

import lombok.Getter;
import net.exylia.exyliaLobby.admin.AdminHub;
import net.exylia.exyliaLobby.area.Areas;
import net.exylia.exyliaLobby.area.LobbyVisibility;
import net.exylia.exyliaLobby.command.AdminCommand;
import net.exylia.exyliaLobby.config.LobbyConfig;
import net.exylia.exyliaLobby.config.LobbyMessages;
import net.exylia.exyliaLobby.config.ModuleNames;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.exyliaLobby.module.Modules;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Configs;
import net.exylia.lib.debug.Debug;
import net.exylia.lib.module.PluginModules;
import net.exylia.lib.reload.Reloads;
import net.exylia.lib.task.TaskScheduler;
import net.exylia.lib.task.Tasks;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Prefixes;
import net.exylia.lib.text.Values;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.Lamp;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.orphan.Orphans;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.exception.SendableException;
import net.exylia.lib.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ExyliaLobby: a complete lobby made of modules, each switched on and off live and edited in game.
 *
 * <p>Not the Bukkit plugin itself: {@link ExyliaLobbyPlugin} is, so it can say where to get ExyliaLib when the
 * server lacks it.
 */
@Getter
public final class ExyliaLobby {

    private JavaPlugin plugin;
    private Debug debug;
    private TaskScheduler tasks;
    private ConfigFile<LobbyConfig> config;
    private ConfigFile<LobbyMessages> messages;
    private ConfigFile<ModuleNames> names;
    private Areas areas;
    private LobbyVisibility visibility;
    private PluginModules modules;
    private List<LobbyModule> registered = List.of();
    private AdminHub hub;
    private Reloads reloads;
    private Lamp<BukkitCommandActor> lamp;
    private volatile boolean stopping;

    public void start(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        debug = Debug.of(plugin);
        tasks = Tasks.of(plugin);
        config = Configs.define(plugin, "config", LobbyConfig.class).version(1).load();
        messages = Configs.define(plugin, "messages", LobbyMessages.class).version(1).translated().load();
        names = Configs.define(plugin, "modules", ModuleNames.class).version(1).translated().load();
        applyConfig();
        debug.motd();

        areas = new Areas(plugin, tasks, debug, config, this::crossing);
        visibility = new LobbyVisibility(plugin);
        modules = PluginModules.of(plugin, id -> config.get().modules().switchOf(id));
        hub = new AdminHub(this);
        LobbyContext context = new LobbyContext(plugin, tasks, debug, config, messages, areas, visibility, hub, modules,
                () -> stopping);
        List<LobbyModule> all = Modules.all(context);
        for (LobbyModule module : all) modules.register(module);
        registered = List.copyOf(all);
        modules.registerActions(net.exylia.lib.action.Actions.of(plugin));

        hub.load();

        lamp = BukkitLamp.builder(plugin)
                .commandCondition(command -> {
                    java.lang.reflect.Method method = command.command().function().method();
                    String off = modules.offModuleOf(method).orElseGet(() -> registered.stream()
                            .filter(module -> !module.isEnabled() && module.ownsCommand(method.getDeclaringClass()))
                            .map(LobbyModule::getId).findFirst().orElse(null));
                    if (off == null) return;
                    PluginMessages.of(plugin).send(command.actor().sender(), messages.get().moduleOff(),
                            Values.of("module", names.get().of(off).name()));
                    throw new SendableException() {
                        @Override
                        public void sendTo(@NotNull CommandActor actor) {
                        }
                    };
                })
                .build();
        lamp.register(Orphans.path(config.get().adminCommand().toArray(String[]::new)).handler(new AdminCommand(this)));
        for (Object handler : modules.allCommandHandlers()) lamp.register(handler);

        modules.loadOnlinePlayers(plugin.getServer().getOnlinePlayers());
        areas.start();
        modules.startTimers();

        reloads = Reloads.of(plugin)
                .step("configs", () -> {
                    Configs.reloadAll(plugin);
                    applyConfig();
                })
                .step("areas", areas::reload)
                .step("modules", modules::reloadConfigs)
                .step("menus", hub::load);
        debug.success("ExyliaLobby enabled with " + registered.stream().filter(LobbyModule::isEnabled).count()
                + " of " + registered.size() + " modules on");
    }

    public void shutdown() {
        stopping = true;
        if (areas != null) areas.stop();
        if (modules != null) modules.disableAll();
        if (lamp != null) lamp.unregisterAllCommands();
        if (debug != null) debug.success("ExyliaLobby disabled");
    }

    private void applyConfig() {
        Prefixes.set(plugin, messages.get().prefix());
        debug.enabled(config.get().debug());
    }

    /** Hands a crossing to every running module: in registration order on the way in, reversed on the way out. */
    private void crossing(Player player, Areas.Change change) {
        List<LobbyModule> order = new ArrayList<>(registered);
        if (!change.entered()) Collections.reverse(order);
        for (LobbyModule module : order) {
            try {
                module.crossing(player, change.entered(), change.cause());
            } catch (RuntimeException failed) {
                debug.error("Module " + module.getId() + " failed on " + player.getName()
                        + (change.entered() ? " entering" : " leaving") + " the lobby", failed);
            }
        }
    }
}
