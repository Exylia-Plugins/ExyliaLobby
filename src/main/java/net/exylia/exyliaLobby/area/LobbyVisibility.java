package net.exylia.exyliaLobby.area;

import net.exylia.lib.packet.Packets;
import net.exylia.lib.packet.VisibilityRule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The plugin's one visibility rule, made of the rules its modules add.
 *
 * <p>ExyliaLib allows one {@link VisibilityRule} per plugin, so modules never register their own: the
 * visibility module (who a player chose to see) and parkour (runners hidden from each other) both
 * {@link #add} here, and a viewer sees a target only when every added rule agrees. A module removes its rule
 * when it stops. After changing what a rule answers, call {@link #refresh} for the targets it concerns.
 *
 * <p>Needs PacketEvents; without it every call does nothing and everyone sees everyone.
 */
public final class LobbyVisibility {

    private final Plugin plugin;
    private final Map<String, VisibilityRule> rules = new ConcurrentHashMap<>();
    private volatile boolean installed;

    public LobbyVisibility(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    /** Whether hiding players works on this server. */
    public boolean available() {
        return Packets.isAvailable();
    }

    /** Adds or replaces the rule named {@code id} (one per module). */
    public void add(@NotNull String id, @NotNull VisibilityRule rule) {
        rules.put(id, rule);
        if (!installed && available()) {
            Packets.of(plugin).visibility().rule(this::canSee);
            installed = true;
        }
    }

    /** Removes a module's rule and shows everyone it hid. */
    public void remove(@NotNull String id) {
        if (rules.remove(id) != null) refreshAll();
    }

    /** Whether every rule lets {@code viewer} see {@code target}. */
    public boolean canSee(@NotNull Player viewer, @NotNull Player target) {
        for (VisibilityRule rule : rules.values()) {
            if (!rule.canSee(viewer, target)) return false;
        }
        return true;
    }

    /** Re-evaluates who sees {@code target}. */
    public void refresh(@NotNull Player target) {
        if (installed) Packets.of(plugin).visibility().refresh(target);
    }

    /** Re-evaluates everyone, after a viewer's own choice changed (they now see more or fewer people). */
    public void refreshAll() {
        if (!installed) return;
        for (Player target : Bukkit.getOnlinePlayers()) refresh(target);
    }
}
