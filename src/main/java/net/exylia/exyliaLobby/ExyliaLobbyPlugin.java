package net.exylia.exyliaLobby;

import net.exylia.exyliaLobby.bootstrap.ExyliaLibMissing;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Bukkit entry point, kept free of ExyliaLib types so it still runs when ExyliaLib is missing.
 *
 * <p>Without ExyliaLib it says where to get the library and disables itself until the restart; with it, it
 * hands over to {@link ExyliaLobby}.
 */
public final class ExyliaLobbyPlugin extends JavaPlugin {

    private ExyliaLobby core;

    @Override
    public void onEnable() {
        Plugin lib = getServer().getPluginManager().getPlugin("ExyliaLib");
        if (lib == null || !lib.isEnabled()) {
            ExyliaLibMissing.report(this);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        core = new ExyliaLobby();
        core.start(this);
    }

    @Override
    public void onDisable() {
        if (core != null) core.shutdown();
        core = null;
    }
}
