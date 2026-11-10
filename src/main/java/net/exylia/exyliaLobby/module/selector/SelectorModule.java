package net.exylia.exyliaLobby.module.selector;

import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.module.Busy;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.exyliaLobby.module.shared.AdminScreens;
import net.exylia.exyliaLobby.module.shared.HologramLook;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Languages;
import net.exylia.lib.hologram.Holograms;
import net.exylia.lib.network.Network;
import net.exylia.lib.npc.NpcLook;
import net.exylia.lib.npc.NpcPose;
import net.exylia.lib.npc.NpcSkin;
import net.exylia.lib.npc.NpcSpec;
import net.exylia.lib.npc.Npcs;
import net.exylia.lib.npc.PlacedNpc;
import net.exylia.lib.proxy.NetworkServer;
import net.exylia.lib.proxy.Proxy;
import net.exylia.lib.region.Cuboid;
import net.exylia.lib.region.PluginRegions;
import net.exylia.lib.region.PolicySet;
import net.exylia.lib.region.RegionSnapshot;
import net.exylia.lib.region.Regions;
import net.exylia.lib.region.SelectionResult;
import net.exylia.lib.region.WorldIdentity;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.Menus;
import net.exylia.lib.ui.UiEntry;
import net.exylia.lib.util.teleport.TeleportResult;
import net.exylia.lib.util.teleport.Teleports;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The server and lobby selectors, the server NPCs and the portals. Counts and status are read live from
 * {@link Proxy#servers()}; a click hands the player to the proxy through ExyliaLib's teleport module.
 *
 * <p>Other modules use {@link #join(Player, String)}, {@link #openServers(Player)} and {@link #openLobbies(Player)}.
 */
public final class SelectorModule extends LobbyModule {

    public static final String ID = "selector";
    public static final String PERMISSION = "exylialobby.selector";
    static final String SERVERS = "selector/servers";
    static final String LOBBIES = "selector/lobbies";
    private static final long REPEAT_MS = 3000;

    private ConfigFile<SelectorSettings> settings;
    private ConfigFile<SelectorMessages> messages;
    private AdminScreens screens;
    private PluginRegions regions;
    private final Set<String> npcIds = ConcurrentHashMap.newKeySet();
    private final Set<String> hologramIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();
    private volatile Runnable back = () -> {
    };

    public SelectorModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.COMPASS;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(SelectorSettings.class);
        messages = messages(SelectorMessages.class);
    }

    @Override
    protected List<Object> commands() {
        List<Object> list = new ArrayList<>(command(settings.get().command(), new SelectorCommand(this, false)));
        list.addAll(command(settings.get().lobbyCommand(), new SelectorCommand(this, true)));
        return list;
    }

    @Override
    protected void start() {
        Languages.refresh(plugin(), SelectorModule.class, "menus/selector");
        load(SERVERS);
        load(LOBBIES);
        screens = new AdminScreens(context, ID, this::click);
        screens.register();
        regions = Regions.of(plugin(), ID);
        listen(new SelectorListener(this));
        build();
        settings.onReload(reloaded -> {
            if (isEnabled()) build();
        });
    }

    @Override
    protected void stop() {
        clearPlaced();
        if (regions != null) regions.replaceAll(List.of());
        screens.clear();
        last.clear();
    }

    @Override
    protected void actions(PluginActions actions) {
        actions.registerSync("selector_open", (action, args) -> {
            openServers(action.player());
            return ActionResult.success();
        });
        actions.registerSync("selector_lobbies", (action, args) -> {
            openLobbies(action.player());
            return ActionResult.success();
        });
        actions.registerSync("selector_join", (action, args) -> {
            join(action.player(), args.string(0, ""));
            return ActionResult.success();
        });
    }

    private void load(String id) {
        context.hub().menu(id);
    }

    // ======================== public hooks ========================

    /** Opens the server selector. */
    public void openServers(@NotNull Player player) {
        Menus.of(plugin()).open(player, SERVERS, Map.of("total_players", (Supplier<String>) () -> String.valueOf(total())),
                () -> serverRows(player));
    }

    /** Opens the lobby selector: the lobbies of this network, emptiest first, with the one the player is on glowing. */
    public void openLobbies(@NotNull Player player) {
        Menus.of(plugin()).open(player, LOBBIES, Map.of("lobby_count", (Supplier<String>) () -> String.valueOf(lobbyIds().size())),
                () -> lobbyRows(player));
    }

    /**
     * Sends a player to a server: {@code ref} is a catalogue key or, failing that, a server id listed as a lobby.
     * Answers in chat when it cannot.
     */
    public void join(@NotNull Player player, @NotNull String ref) {
        SelectorSettings.Server server = settings.get().servers().get(ref);
        if (server == null) server = lobbyIds().contains(ref) ? new SelectorSettings.Server(ref, "", 0, List.of(), ref, "") : null;
        SelectorMessages text = messages.get();
        if (server == null) {
            send(player, text.unknownServer(), Values.of());
            return;
        }
        Values values = Values.of("server", server.name());
        if (!server.permission().isBlank() && !player.hasPermission(server.permission())) {
            send(player, text.noPermission(), values);
            return;
        }
        if (Network.of(plugin()).isThisServer(server.server())) {
            send(player, text.here(), values);
            return;
        }
        if (Proxy.server(server.server()).map(NetworkServer::online).orElse(true) == false) {
            send(player, text.offline(), values);
            return;
        }
        long now = System.currentTimeMillis();
        Long before = last.put(player.getUniqueId(), now);
        if (before != null && now - before < REPEAT_MS) return;
        send(player, text.connecting(), values);
        // Players leave through the teleport module, which hands them to the proxy (never Player#teleport).
        Teleports.of(plugin()).toServer(player, server.server()).thenAccept(result -> {
            if (result != TeleportResult.SUCCESS) context.tasks().runAtEntity(player, () -> send(player, text.unreachable(), values));
        });
    }

    /** Whether a player may be sent away by a portal now. */
    boolean portalReady(Player player) {
        return context.areas().contains(player) && !Busy.any(context, player);
    }

    /** Called by the listener when a player walks into a portal box. */
    void entered(Player player, RegionSnapshot region) {
        String id = region.id().value();
        if (!id.startsWith("portal-")) return;
        SelectorSettings.Portal portal = settings.get().portals().get(id.substring("portal-".length()));
        if (portal != null && portalReady(player)) join(player, portal.server());
    }

    PluginRegions regions() {
        return regions;
    }

    // ======================== rows ========================

    private int total() {
        int sum = 0;
        for (SelectorSettings.Server server : settings.get().servers().values()) {
            sum += Proxy.server(server.server()).map(NetworkServer::players).orElse(0);
        }
        return sum;
    }

    private List<String> lobbyIds() {
        return settings.get().lobbies();
    }

    private String players(String id) {
        return Proxy.server(id).map(found -> String.valueOf(found.players())).orElse("-");
    }

    private String state(String id) {
        SelectorMessages text = messages.get();
        return Proxy.server(id).map(found -> found.online() ? text.statusOnline() : text.statusOffline()).orElse(text.statusUnknown());
    }

    /** The prompt of a card, which changes with what the viewer can do right now. */
    private String prompt(Player viewer, String id, String permission) {
        SelectorMessages text = messages.get();
        if (Network.of(plugin()).isThisServer(id)) return text.clickHere();
        if (!permission.isBlank() && !viewer.hasPermission(permission)) return text.clickLocked();
        if (Proxy.server(id).map(found -> !found.online()).orElse(false)) return text.clickOffline();
        return text.clickJoin();
    }

    private List<UiEntry> serverRows(Player viewer) {
        List<UiEntry> rows = new ArrayList<>();
        settings.get().servers().entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getValue().order()))
                .forEach(entry -> {
                    SelectorSettings.Server server = entry.getValue();
                    rows.add(UiEntry.of(entry.getKey())
                            .with("row_ref", entry.getKey())
                            .with("row_icon", server.icon())
                            .withFormatted("row_name", server.name())
                            .withFormatted("row_about", String.join("<nl>", server.description()))
                            .with("row_glow", String.valueOf(Network.of(plugin()).isThisServer(server.server())))
                            .withFormatted("row_players", () -> players(server.server()))
                            .withFormatted("row_state", () -> state(server.server()))
                            .withFormatted("row_prompt", () -> prompt(viewer, server.server(), server.permission()))
                            .build());
                });
        return rows;
    }

    private List<UiEntry> lobbyRows(Player viewer) {
        List<String> ids = new ArrayList<>(lobbyIds());
        // Emptiest first; re-read on every redraw, so the order follows the counts.
        ids.sort(Comparator.comparingInt((String id) -> Proxy.server(id).map(NetworkServer::players).orElse(Integer.MAX_VALUE)));
        List<UiEntry> rows = new ArrayList<>();
        for (String id : ids) {
            SelectorSettings.Server known = settings.get().servers().values().stream()
                    .filter(server -> server.server().equals(id)).findFirst().orElse(null);
            rows.add(UiEntry.of(id)
                    .with("row_ref", id)
                    .with("row_icon", known == null ? "ENDER_PEARL" : known.icon())
                    .withFormatted("row_name", known == null ? id : known.name())
                    .with("row_glow", String.valueOf(Network.of(plugin()).isThisServer(id)))
                    .withFormatted("row_players", () -> players(id))
                    .withFormatted("row_state", () -> state(id))
                    .withFormatted("row_prompt", () -> prompt(viewer, id, ""))
                    .build());
        }
        return rows;
    }

    // ======================== placing ========================

    private void clearPlaced() {
        for (String id : npcIds) Npcs.of(plugin()).unplace(id);
        for (String id : hologramIds) Holograms.remove(plugin(), id);
        npcIds.clear();
        hologramIds.clear();
    }

    private void build() {
        clearPlaced();
        SelectorSettings config = settings.get();
        if (Npcs.isSupported()) {
            for (Map.Entry<String, SelectorSettings.Npc> entry : config.npcs().entrySet()) placeNpc(entry.getKey(), entry.getValue());
        } else if (!config.npcs().isEmpty()) {
            context.debug().warn("Server NPCs need PacketEvents; they are not drawn.");
        }
        List<RegionSnapshot> boxes = new ArrayList<>();
        for (Map.Entry<String, SelectorSettings.Portal> entry : config.portals().entrySet()) {
            SelectorSettings.Portal portal = entry.getValue();
            World world = Bukkit.getWorld(portal.world());
            if (world == null) continue;
            boxes.add(regions.region("portal-" + entry.getKey(), WorldIdentity.from(world),
                    Cuboid.blocks(Math.min(portal.x1(), portal.x2()), Math.min(portal.y1(), portal.y2()), Math.min(portal.z1(), portal.z2()),
                            Math.max(portal.x1(), portal.x2()), Math.max(portal.y1(), portal.y2()), Math.max(portal.z1(), portal.z2())),
                    0, PolicySet.empty()));
        }
        regions.replaceAll(boxes);
    }

    private void placeNpc(String key, SelectorSettings.Npc npc) {
        World world = Bukkit.getWorld(npc.world());
        SelectorSettings.Server server = settings.get().servers().get(npc.server());
        if (world == null || server == null) return;
        Location at = new Location(world, npc.x(), npc.y(), npc.z(), (float) npc.yaw(), (float) npc.pitch());
        NpcSkin skin = npc.skin().isBlank() ? NpcSkin.none()
                : npc.skin().equalsIgnoreCase("mirror") ? NpcSkin.mirror() : NpcSkin.player(npc.skin());
        String id = "selector-" + key;
        PlacedNpc placed = Npcs.of(plugin()).place(id, NpcSpec.builder().skin(skin).pose(NpcPose.STANDING)
                .look(NpcLook.EACH_VIEWER).build(), at);
        placed.onClick((player, click) -> join(player, npc.server()));
        npcIds.add(id);
        if (npc.hologram().isEmpty() || !Holograms.isSupported()) return;
        Holograms.show(plugin(), id, at, HologramLook.text(npc.hologram(), 32, npc.hologramHeight()), Map.of(
                "server_name", server.name(),
                "server_players", (Supplier<Object>) () -> players(server.server())));
        hologramIds.add(id);
    }

    // ======================== admin ========================

    @Override
    public boolean hasAdmin() {
        return true;
    }

    @Override
    public void openAdmin(@NotNull Player viewer, @NotNull Runnable back) {
        this.back = back;
        SelectorMessages text = messages.get();
        screens.open(viewer, back, "Modules", text.listTitle(), text.listKind(), List.of(
                new AdminScreens.Add("PLAYER_HEAD", text.addNpcTitle(), text.addNpcText(), text.addNpcClick(), text.addNpcNext(),
                        admin -> addNpc(admin)),
                new AdminScreens.Add("END_PORTAL_FRAME", text.addPortalTitle(), text.addPortalText(), text.addPortalClick(),
                        text.addPortalNext(), admin -> addPortal(admin))),
                this::rows);
    }

    private List<UiEntry> rows() {
        SelectorMessages text = messages.get();
        List<UiEntry> rows = new ArrayList<>();
        for (Map.Entry<String, SelectorSettings.Npc> entry : settings.get().npcs().entrySet()) {
            SelectorSettings.Npc npc = entry.getValue();
            rows.add(AdminScreens.row("npc:" + entry.getKey(), "PLAYER_HEAD", entry.getKey(), text.subtitleNpc(), List.of(
                    AdminScreens.stat("compass", text.labelServer(), serverName(npc.server())),
                    AdminScreens.stat("map", text.labelPlace(), "{info}" + npc.world() + " " + Math.round(npc.x()) + " "
                            + Math.round(npc.y()) + " " + Math.round(npc.z()))), text.clicksNpc(), false));
        }
        for (Map.Entry<String, SelectorSettings.Portal> entry : settings.get().portals().entrySet()) {
            SelectorSettings.Portal portal = entry.getValue();
            rows.add(AdminScreens.row("portal:" + entry.getKey(), "END_PORTAL_FRAME", entry.getKey(), text.subtitlePortal(), List.of(
                    AdminScreens.stat("compass", text.labelServer(), serverName(portal.server())),
                    AdminScreens.stat("map", text.labelPlace(), "{info}" + portal.world() + " " + portal.x1() + " " + portal.y1() + " " + portal.z1()),
                    AdminScreens.stat("hopper", text.labelSize(), "{info}" + (Math.abs(portal.x2() - portal.x1()) + 1) + "x"
                            + (Math.abs(portal.y2() - portal.y1()) + 1) + "x" + (Math.abs(portal.z2() - portal.z1()) + 1))),
                    text.clicksPortal(), false));
        }
        return rows;
    }

    private String serverName(String key) {
        SelectorSettings.Server server = settings.get().servers().get(key);
        return server == null ? messages.get().serverMissing() : server.name();
    }

    private void click(Player viewer, String click, String id) {
        boolean npc = id.startsWith("npc:");
        String key = id.substring(id.indexOf(':') + 1);
        if (npc ? !settings.get().npcs().containsKey(key) : !settings.get().portals().containsKey(key)) return;
        SelectorMessages text = messages.get();
        Runnable reopen = () -> openAdmin(viewer, back);
        String what = (npc ? text.subtitleNpc() : text.subtitlePortal()) + " " + key;
        switch (click) {
            case "left" -> askServer(viewer, server -> {
                edit(npc, key, current -> new SelectorSettings.Npc(server, current.skin(), current.world(), current.x(), current.y(),
                        current.z(), current.yaw(), current.pitch(), current.hologram(), current.hologramHeight()),
                        current -> new SelectorSettings.Portal(server, current.world(), current.x1(), current.y1(), current.z1(),
                                current.x2(), current.y2(), current.z2()));
                send(viewer, text.changed(), Values.of("what", what).put("server", serverName(server)));
                reopen.run();
            }, reopen);
            case "right" -> {
                if (npc) {
                    viewer.closeInventory();
                    setup(viewer, reopen).spawn(text.wizardNpc(), key, at -> {
                        edit(true, key, current -> new SelectorSettings.Npc(current.server(), current.skin(), at.getWorld().getName(),
                                at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch(), current.hologram(), current.hologramHeight()), null);
                        send(viewer, text.moved(), Values.of("what", what));
                        reopen.run();
                    });
                } else {
                    viewer.closeInventory();
                    regions.visualize(viewer, "portal-" + key);
                }
            }
            case "shift" -> screens.confirm(viewer, Values.of("what", what).apply(text.confirmRemove()), () -> {
                settings.update(current -> {
                    Map<String, SelectorSettings.Npc> npcs = new LinkedHashMap<>(current.npcs());
                    Map<String, SelectorSettings.Portal> portals = new LinkedHashMap<>(current.portals());
                    if (npc) npcs.remove(key);
                    else portals.remove(key);
                    return with(current, current.servers(), npcs, portals);
                });
                build();
                send(viewer, text.removed(), Values.of("what", what));
            }, reopen);
            default -> {
            }
        }
    }

    private static SelectorSettings with(SelectorSettings current, Map<String, SelectorSettings.Server> servers,
                                         Map<String, SelectorSettings.Npc> npcs, Map<String, SelectorSettings.Portal> portals) {
        return new SelectorSettings(current.command(), current.lobbyCommand(), current.lobbies(), servers, npcs, portals);
    }

    private void edit(boolean npc, String key, UnaryOperator<SelectorSettings.Npc> npcChange, UnaryOperator<SelectorSettings.Portal> portalChange) {
        settings.update(current -> {
            Map<String, SelectorSettings.Npc> npcs = new LinkedHashMap<>(current.npcs());
            Map<String, SelectorSettings.Portal> portals = new LinkedHashMap<>(current.portals());
            if (npc) npcs.computeIfPresent(key, (k, old) -> npcChange.apply(old));
            else portals.computeIfPresent(key, (k, old) -> portalChange.apply(old));
            return with(current, current.servers(), npcs, portals);
        });
        build();
    }

    /** Asks which catalogue entry; closes the screen first and calls {@code reopen} on cancel. */
    private void askServer(Player viewer, java.util.function.Consumer<String> chosen, Runnable reopen) {
        Map<String, SelectorSettings.Server> servers = settings.get().servers();
        if (servers.isEmpty()) {
            send(viewer, messages.get().noServers(), Values.of());
            reopen.run();
            return;
        }
        viewer.closeInventory();
        screens.inputs().answered(viewer, screens.inputs().choice(viewer, messages.get().promptServer(), new ArrayList<>(servers.keySet()))
                .label(key -> servers.get(key).name()).key(key -> key).open(), chosen, reopen);
    }

    private String nextId(Set<String> taken, String prefix) {
        int number = taken.size() + 1;
        while (taken.contains(prefix + number)) number++;
        return prefix + number;
    }

    private void addNpc(Player admin) {
        Runnable reopen = () -> openAdmin(admin, back);
        askServer(admin, server -> setup(admin, reopen).spawn(messages.get().wizardNpc(), at -> {
            String key = nextId(settings.get().npcs().keySet(), "npc-");
            SelectorSettings.Npc base = new SelectorSettings.Npc();
            settings.update(current -> {
                Map<String, SelectorSettings.Npc> npcs = new LinkedHashMap<>(current.npcs());
                npcs.put(key, new SelectorSettings.Npc(server, "mirror", at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
                        at.getYaw(), at.getPitch(), base.hologram(), base.hologramHeight()));
                return with(current, current.servers(), npcs, current.portals());
            });
            build();
            send(admin, messages.get().added(), Values.of("what", messages.get().subtitleNpc() + " " + key));
            reopen.run();
        }), reopen);
    }

    private void addPortal(Player admin) {
        Runnable reopen = () -> openAdmin(admin, back);
        if (settings.get().servers().isEmpty()) {
            send(admin, messages.get().noServers(), Values.of());
            return;
        }
        admin.closeInventory();
        setup(admin, reopen).area(messages.get().wizardPortal(),
                result -> askServer(admin, server -> savePortal(admin, server, result, reopen), reopen));
    }

    private void savePortal(Player admin, String server, SelectionResult result, Runnable reopen) {
        String key = nextId(settings.get().portals().keySet(), "portal-");
        var a = result.first();
        var b = result.second();
        settings.update(current -> {
            Map<String, SelectorSettings.Portal> portals = new LinkedHashMap<>(current.portals());
            portals.put(key, new SelectorSettings.Portal(server, result.world().fallbackName(), a.x(), a.y(), a.z(), b.x(), b.y(), b.z()));
            return with(current, current.servers(), current.npcs(), portals);
        });
        build();
        send(admin, messages.get().added(), Values.of("what", messages.get().subtitlePortal() + " " + key));
        reopen.run();
    }

    // ======================== shared ========================

    private void send(Player to, String line, Values values) {
        PluginMessages.of(plugin()).send(to, line, values);
    }
}
