package net.exylia.exyliaLobby.area;

import net.exylia.exyliaLobby.config.LobbyConfig;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.debug.Debug;
import net.exylia.lib.region.Cuboid;
import net.exylia.lib.region.PlayerRegionChangeEvent;
import net.exylia.lib.region.PluginRegions;
import net.exylia.lib.region.PolicySet;
import net.exylia.lib.region.RegionSnapshot;
import net.exylia.lib.region.Regions;
import net.exylia.lib.region.WorldIdentity;
import net.exylia.lib.task.TaskHandle;
import net.exylia.lib.task.TaskScheduler;
import net.exylia.lib.util.worldguard.WorldGuardRegions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Where the lobby is, and who is in it.
 *
 * <p>Lobby worlds and boxes are registered as ExyliaLib regions (a whole world is one region spanning it), so
 * crossing an edge, a teleport, a world change and Folia's unreported moves all arrive through
 * {@link PlayerRegionChangeEvent}. WorldGuard regions have no events and are polled twice a second, only when
 * some are configured.
 *
 * <p>Membership changes are handed to {@code onChange} on the player's own thread, before the matching
 * {@link LobbyEnterEvent} or {@link LobbyLeaveEvent} is fired for other plugins.
 */
public final class Areas implements Listener {

    private static final int EDGE = 30_000_000;

    private final JavaPlugin plugin;
    private final TaskScheduler tasks;
    private final Debug debug;
    private final ConfigFile<LobbyConfig> config;
    private final PluginRegions regions;
    private final Set<UUID> inside = ConcurrentHashMap.newKeySet();
    private final Map<UUID, TaskHandle> polls = new ConcurrentHashMap<>();
    private final BiConsumer<Player, Change> onChange;

    /** A crossing: in or out, and why. */
    public record Change(boolean entered, @NotNull AreaCause cause) {
    }

    public Areas(@NotNull JavaPlugin plugin, @NotNull TaskScheduler tasks, @NotNull Debug debug,
                 @NotNull ConfigFile<LobbyConfig> config, @NotNull BiConsumer<Player, Change> onChange) {
        this.plugin = plugin;
        this.tasks = tasks;
        this.debug = debug;
        this.config = config;
        this.onChange = onChange;
        this.regions = Regions.of(plugin, "area");
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        rebuild();
        for (Player player : Bukkit.getOnlinePlayers()) {
            tasks.runAtEntity(player, () -> {
                poll(player);
                recheck(player, AreaCause.JOIN);
            });
        }
    }

    /** Re-reads the areas from config and moves everyone standing in what changed. */
    public void reload() {
        rebuild();
        for (Player player : Bukkit.getOnlinePlayers()) {
            tasks.runAtEntity(player, () -> {
                poll(player);
                recheck(player, AreaCause.CHANGE);
            });
        }
    }

    /** Takes everyone out (as {@link AreaCause#DISABLE}) and forgets the regions. */
    public void stop() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (inside.remove(player.getUniqueId())) change(player, new Change(false, AreaCause.DISABLE));
        }
        polls.values().forEach(TaskHandle::cancel);
        polls.clear();
        regions.replaceAll(List.of());
    }

    /** Whether the player is in the lobby now. Safe from any thread. */
    public boolean contains(@NotNull Player player) {
        return inside.contains(player.getUniqueId());
    }

    /** Whether a place is lobby. */
    public boolean contains(@NotNull Location location) {
        if (!regions.at(location).isEmpty()) return true;
        List<String> guarded = config.get().areas().worldguardRegions();
        if (guarded.isEmpty() || !WorldGuardRegions.available()) return false;
        for (String id : guarded) {
            if (WorldGuardRegions.isIn(location, id)) return true;
        }
        return false;
    }

    /** Everyone in the lobby now. */
    public @NotNull Set<UUID> players() {
        return Set.copyOf(inside);
    }

    private void rebuild() {
        LobbyConfig.Areas areas = config.get().areas();
        List<RegionSnapshot> built = new ArrayList<>();
        for (String name : areas.worlds()) {
            World world = Bukkit.getWorld(name);
            if (world == null) {
                debug.debug("Lobby world " + name + " is not loaded yet");
                continue;
            }
            built.add(regions.region("world-" + name.toLowerCase(java.util.Locale.ROOT), WorldIdentity.from(world),
                    Cuboid.blocks(-EDGE, world.getMinHeight(), -EDGE, EDGE, world.getMaxHeight(), EDGE),
                    0, PolicySet.empty()));
        }
        for (Map.Entry<String, LobbyConfig.Box> entry : areas.boxes().entrySet()) {
            LobbyConfig.Box box = entry.getValue();
            World world = Bukkit.getWorld(box.world());
            int[] min = coordinates(box.min());
            int[] max = coordinates(box.max());
            if (world == null || min == null || max == null) {
                debug.warn("Lobby area " + entry.getKey() + " is skipped: world " + box.world()
                        + " is not loaded or its corners are not 'x y z'");
                continue;
            }
            built.add(regions.region("box-" + entry.getKey().toLowerCase(java.util.Locale.ROOT), WorldIdentity.from(world),
                    Cuboid.blocks(min[0], min[1], min[2], max[0], max[1], max[2]), 0, PolicySet.empty()));
        }
        regions.replaceAll(built);
    }

    private static int[] coordinates(String text) {
        String[] parts = text.trim().split("[ ,]+");
        if (parts.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException wrong) {
            return null;
        }
    }

    /** Polls WorldGuard regions for one player, when there are any to poll. */
    private void poll(Player player) {
        TaskHandle previous = polls.remove(player.getUniqueId());
        if (previous != null) previous.cancel();
        if (config.get().areas().worldguardRegions().isEmpty() || !WorldGuardRegions.installed()) return;
        polls.put(player.getUniqueId(), tasks.runAtEntityTimer(player, 10L, 10L, () -> recheck(player, AreaCause.MOVE)));
    }

    /** Compares where the player is with what we last said, on their own thread. */
    private void recheck(Player player, AreaCause cause) {
        if (!player.isOnline()) return;
        boolean now = contains(player.getLocation());
        boolean was = inside.contains(player.getUniqueId());
        if (now == was) return;
        if (now) inside.add(player.getUniqueId());
        else inside.remove(player.getUniqueId());
        change(player, new Change(now, cause));
    }

    private void change(Player player, Change change) {
        try {
            onChange.accept(player, change);
        } catch (RuntimeException failed) {
            debug.error("A module failed while " + player.getName() + (change.entered() ? " entered" : " left")
                    + " the lobby", failed);
        }
        Bukkit.getPluginManager().callEvent(change.entered()
                ? new LobbyEnterEvent(player, change.cause())
                : new LobbyLeaveEvent(player, change.cause()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegionChange(PlayerRegionChangeEvent event) {
        if (event.entered(regions).isEmpty() && event.exited(regions).isEmpty()) return;
        recheck(event.getPlayer(), AreaCause.MOVE);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        poll(player);
        recheck(player, AreaCause.JOIN);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        TaskHandle poll = polls.remove(player.getUniqueId());
        if (poll != null) poll.cancel();
        if (inside.remove(player.getUniqueId())) change(player, new Change(false, AreaCause.QUIT));
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        LobbyConfig.Areas areas = config.get().areas();
        String name = event.getWorld().getName();
        if (areas.worlds().contains(name) || areas.boxes().values().stream().anyMatch(box -> box.world().equals(name))) {
            rebuild();
        }
    }
}
