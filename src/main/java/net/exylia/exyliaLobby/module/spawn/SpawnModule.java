package net.exylia.exyliaLobby.module.spawn;

import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.admin.AdminHub;
import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.config.LobbyConfig;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.input.PluginInputs;
import net.exylia.lib.input.Inputs;
import net.exylia.lib.region.Regions;
import net.exylia.lib.respawn.RespawnHook;
import net.exylia.lib.respawn.Respawns;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.Menus;
import net.exylia.lib.ui.PluginMenus;
import net.exylia.lib.ui.UiEntry;
import net.exylia.lib.util.teleport.TeleportResult;
import net.exylia.lib.util.teleport.Teleports;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Spawn point, {@code /spawn}, spawn on join, void return, respawn at spawn and the lobby areas editor.
 *
 * <p>Other modules use {@link #spawn(World)}, {@link #teleport(Player)} and {@link #rescue}.
 */
public final class SpawnModule extends LobbyModule {

    public static final String ID = "spawn";
    public static final String PERMISSION = "exylialobby.spawn";
    static final String DETAIL = "admin/spawn/detail";
    static final String AREAS = "admin/spawn/areas";

    private ConfigFile<SpawnSettings> settings;
    private ConfigFile<SpawnMessages> messages;
    private RespawnHook respawnHook;
    private final List<Rescue> rescues = new CopyOnWriteArrayList<>();
    private final Set<UUID> rescuing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Runnable> areaBacks = new ConcurrentHashMap<>();
    private final Map<UUID, String> areaParents = new ConcurrentHashMap<>();
    private AreasEditor editor;

    public SpawnModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.RED_BED;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(SpawnSettings.class);
        messages = messages(SpawnMessages.class);
    }

    @Override
    protected void start() {
        context.hub().menu(DETAIL);
        context.hub().menu(AREAS);
        editor = new AreasEditor(this);
        listen(new SpawnListener(this));
        respawnHook = Respawns.of(plugin()).location(EventPriority.HIGH, ctx -> {
            if (!settings.get().respawn()) return null;
            Location death = ctx.deathLocation();
            if (death == null || !context.areas().contains(death)) return null;
            return spawn(death.getWorld()).orElse(null);
        });
    }

    @Override
    protected void stop() {
        if (respawnHook != null) respawnHook.cancel();
        rescuing.clear();
        areaBacks.clear();
        areaParents.clear();
    }

    // ======================== public hooks ========================

    /** A void-return rule: see {@link #rescue}. */
    private record Rescue(int priority, Function<Player, Optional<Location>> rule) {
    }

    /**
     * Decides where a player who fell below the void height goes, before spawn does.
     *
     * <p>Rules run highest priority first, on the player's own thread, and must be cheap; the first one that
     * answers a location wins, and spawn is the last resort. A parkour module returns the runner's checkpoint.
     *
     * @return a handle that removes the rule again
     */
    public @NotNull Runnable rescue(int priority, @NotNull Function<Player, Optional<Location>> rule) {
        Rescue entry = new Rescue(priority, rule);
        rescues.add(entry);
        rescues.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        return () -> rescues.remove(entry);
    }

    /** The spawn for a world: its own point, else the first one set, else the world's own spawn. */
    public @NotNull Optional<Location> spawn(World world) {
        Map<String, SpawnSettings.Point> points = settings.get().points();
        SpawnSettings.Point own = null;
        SpawnSettings.Point first = null;
        for (SpawnSettings.Point point : points.values()) {
            if (Bukkit.getWorld(point.world()) == null) continue;
            if (first == null) first = point;
            if (world != null && point.world().equals(world.getName())) {
                own = point;
                break;
            }
        }
        SpawnSettings.Point chosen = own != null ? own : first;
        if (chosen != null) {
            return Optional.of(new Location(Bukkit.getWorld(chosen.world()), chosen.x(), chosen.y(), chosen.z(),
                    (float) chosen.yaw(), (float) chosen.pitch()));
        }
        return Optional.ofNullable(world).map(World::getSpawnLocation);
    }

    /** Sends a player to spawn at once, through the teleport module. Empty result when there is no spawn. */
    public @NotNull CompletableFuture<TeleportResult> teleport(@NotNull Player player) {
        return spawn(player.getWorld())
                .map(location -> Teleports.of(plugin()).to(player, location).warmup(0).start().future())
                .orElseGet(() -> CompletableFuture.completedFuture(TeleportResult.WORLD_NOT_FOUND));
    }

    // ======================== lobby crossings ========================

    @Override
    protected void enter(@NotNull Player player, @NotNull AreaCause cause) {
        if (cause != AreaCause.JOIN) return;
        SpawnSettings.JoinMode mode = settings.get().onJoin();
        if (mode == SpawnSettings.JoinMode.NEVER) return;
        if (mode == SpawnSettings.JoinMode.FIRST_JOIN && player.hasPlayedBefore()) return;
        teleport(player);
    }

    /** Sends a player below the void height back; called by the listener on the player's thread. */
    void rescued(Player player) {
        if (!rescuing.add(player.getUniqueId())) return;
        Location target = null;
        for (Rescue rescue : rescues) {
            Optional<Location> answer = rescue.rule().apply(player);
            if (answer.isPresent()) {
                target = answer.get();
                break;
            }
        }
        if (target == null) target = spawn(player.getWorld()).orElse(null);
        if (target == null) {
            rescuing.remove(player.getUniqueId());
            return;
        }
        player.setFallDistance(0);
        Teleports.of(plugin()).to(player, target).warmup(0).start().future()
                .whenComplete((result, error) -> rescuing.remove(player.getUniqueId()));
    }

    // ======================== commands ========================

    @Override
    protected List<Object> commands() {
        return command(settings.get().command(), new SpawnCommand(this));
    }

    void playerSpawn(Player player) {
        Optional<Location> target = spawn(player.getWorld());
        if (target.isEmpty()) {
            send(player, messages.get().noSpawn(), Values.of());
            return;
        }
        double warmup = settings.get().warmup();
        if (warmup <= 0) {
            Teleports.of(plugin()).to(player, target.get()).warmup(0).start();
            return;
        }
        send(player, messages.get().teleporting(), withTime(Values.of(), warmup));
        Teleports.of(plugin()).to(player, target.get()).warmup(warmup).cancelOnMove().cancelOnDamage()
                .then(result -> {
                    if (result == TeleportResult.CANCELLED_ON_MOVE || result == TeleportResult.CANCELLED_ON_DAMAGE) {
                        send(player, messages.get().cancelled(), Values.of());
                    }
                }).start();
    }

    // ======================== admin ========================

    @Override
    public boolean hasAdmin() {
        return true;
    }

    @Override
    public void openAdmin(@NotNull Player viewer, @NotNull Runnable back) {
        openDetail(viewer);
    }

    void openDetail(Player viewer) {
        Supplier<String> state = () -> spawnIsSet(viewer.getWorld()) ? messages.get().stateSet() : messages.get().stateUnset();
        ui().open(viewer, DETAIL, Map.of("spawn_state", state, "spawn_world", (Supplier<String>) () -> viewer.getWorld().getName()));
    }

    /**
     * The lobby areas editor: worlds and boxes where the lobby rules apply.
     *
     * @param parent the name of the screen {@code back} reopens, shown on the Back button
     */
    public void openAreas(@NotNull Player viewer, @NotNull String parent, @NotNull Runnable back) {
        areaBacks.put(viewer.getUniqueId(), back);
        areaParents.put(viewer.getUniqueId(), parent);
        editor.open(viewer);
    }

    String areaParent(Player viewer) {
        return areaParents.getOrDefault(viewer.getUniqueId(), "Admin");
    }

    private boolean spawnIsSet(World world) {
        return settings.get().points().values().stream().anyMatch(point -> point.world().equals(world.getName()));
    }

    @Override
    protected void actions(PluginActions actions) {
        actions.registerSync("spawn_set", Permissions.ADMIN, (action, args) -> {
            setSpawn(action.player());
            return ActionResult.success();
        });
        actions.registerSync("spawn_tp", Permissions.ADMIN, (action, args) -> {
            teleport(action.player());
            return ActionResult.success();
        });
        actions.registerSync("spawn_areas", Permissions.ADMIN, (action, args) -> {
            Player viewer = action.player();
            openAreas(viewer, "Spawn", () -> openDetail(viewer));
            return ActionResult.success();
        });
        actions.registerSync("spawn_areas_back", Permissions.ADMIN, (action, args) -> {
            Player viewer = action.player();
            areaBacks.getOrDefault(viewer.getUniqueId(), () -> context.hub().open(viewer)).run();
            return ActionResult.success();
        });
        actions.registerSync("spawn_area_add_world", Permissions.ADMIN, (action, args) -> {
            editor.addWorld(action.player());
            return ActionResult.success();
        });
        actions.registerSync("spawn_area_add_box", Permissions.ADMIN, (action, args) -> {
            editor.addBox(action.player());
            return ActionResult.success();
        });
        actions.registerSync("spawn_area_remove", Permissions.ADMIN, (action, args) -> {
            editor.remove(action.player(), args.string(0, ""), args.string(1, ""));
            return ActionResult.success();
        });
    }

    private void setSpawn(Player admin) {
        admin.closeInventory();
        String world = admin.getWorld().getName();
        setup(admin, () -> openDetail(admin)).spawn(messages.get().wizardSpawn(), world, here -> {
            String in = here.getWorld().getName();
            SpawnSettings.Point point = new SpawnSettings.Point(in, here.getX(), here.getY(), here.getZ(), here.getYaw(), here.getPitch());
            settings.update(current -> {
                Map<String, SpawnSettings.Point> points = new LinkedHashMap<>(current.points());
                points.put(in, point);
                return new SpawnSettings(current.command(), current.onJoin(), current.warmup(), current.respawn(),
                        current.voidReturn(), current.voidY(), points);
            });
            send(admin, messages.get().spawnSet(), Values.of("world", in));
            openDetail(admin);
        });
    }

    // ======================== shared with the editor ========================

    ConfigFile<SpawnMessages> messages() {
        return messages;
    }

    ConfigFile<SpawnSettings> settings() {
        return settings;
    }

    LobbyContext context() {
        return context;
    }

    PluginMenus ui() {
        return Menus.of(plugin());
    }

    PluginInputs inputs() {
        return Inputs.of(plugin());
    }

    void send(CommandSender to, String line, Values values) {
        PluginMessages.of(plugin()).send(to, line, values);
    }

    /** Saves new areas and has the core re-read them. */
    void updateAreas(java.util.function.UnaryOperator<LobbyConfig.Areas> change) {
        context.config().update(config -> new LobbyConfig(config.language(), config.modules(), change.apply(config.areas()),
                config.adminCommand(), config.debug()));
        context.areas().reload();
    }

    List<UiEntry> areaRows() {
        return editor.rows();
    }
}
