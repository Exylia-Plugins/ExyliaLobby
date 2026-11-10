package net.exylia.exyliaLobby.module;

import net.exylia.exyliaLobby.admin.AdminHub;
import net.exylia.exyliaLobby.area.Areas;
import net.exylia.exyliaLobby.area.LobbyVisibility;
import net.exylia.exyliaLobby.config.LobbyConfig;
import net.exylia.exyliaLobby.config.LobbyMessages;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.debug.Debug;
import net.exylia.lib.module.PluginModule;
import net.exylia.lib.module.PluginModules;
import net.exylia.lib.task.TaskScheduler;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.BooleanSupplier;

/** What every module is handed: the plugin, its scheduler, the core files, the lobby areas and the other modules. */
public record LobbyContext(
        @NotNull JavaPlugin plugin,
        @NotNull TaskScheduler tasks,
        @NotNull Debug debug,
        @NotNull ConfigFile<LobbyConfig> config,
        @NotNull ConfigFile<LobbyMessages> messages,
        @NotNull Areas areas,
        @NotNull LobbyVisibility visibility,
        @NotNull AdminHub hub,
        @NotNull PluginModules modules,
        @NotNull BooleanSupplier stoppingFlag) {

    /** Another running module, to ask it something; empty when it is off. */
    public <T extends PluginModule> @NotNull Optional<T> module(@NotNull String id, @NotNull Class<T> type) {
        return modules.getModule(id, type);
    }

    /**
     * Another module whether it runs or not, to leave it a registration it keeps while off and applies when it is
     * switched on (a hologram board, a reserved hotbar slot, a damage rule). Ask {@link #module} for anything else.
     */
    public <T extends PluginModule> @NotNull Optional<T> registered(@NotNull String id, @NotNull Class<T> type) {
        return modules.all().stream().filter(module -> module.getId().equals(id))
                .filter(type::isInstance).map(type::cast).findFirst();
    }

    /** Whether the plugin is shutting down: no new tasks may be scheduled, work runs in place. */
    public boolean stopping() {
        return stoppingFlag.getAsBoolean();
    }
}
