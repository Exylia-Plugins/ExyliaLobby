package net.exylia.exyliaLobby.module.selector;

import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.module.Busy;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.exyliaLobby.module.shared.AdminScreens;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Languages;
import net.exylia.lib.network.Network;
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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The server and lobby selectors and the portals. Counts and status are read live from
 * {@link Proxy#servers()}; a click hands the player to the proxy through ExyliaLib's teleport module.
 *
 * <p>Other modules use {@link #join(Player, String)}, {@link #openServers(Player)} and {@link #openLobbies(Player)}.
 */
public final class SelectorModule extends LobbyModule {

    public static final String ID = "selector";
    public static final String PERMISSION = "exylialobby.selector";
    static final String SERVERS = "selector/servers";
    static final String LOBBIES = "selector/lobbies";
    /** A second click on a server while the first one is still connecting is ignored. */
    private static final Duration REPEAT = Duration.ofSeconds(3);

    private ConfigFile<SelectorSettings> settings;
    private ConfigFile<SelectorMessages> messages;
    private AdminScreens screens;
    private PluginRegions regions;
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
        settings = settings(SelectorSettings.class, b -> b.localizedDefaults());
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
        if (regions != null) regions.replaceAll(List.of());
        screens.clear();
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
        if (!cooldowns().tryStart(player, "selector_connect", REPEAT)) return;
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

    private void build() {
        SelectorSettings config = settings.get();
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
                new AdminScreens.Add("END_PORTAL_FRAME", text.addPortalTitle(), text.addPortalText(), text.addPortalClick(),
                        text.addPortalNext(), admin -> addPortal(admin))),
                this::rows);
    }

    private List<UiEntry> rows() {
        SelectorMessages text = messages.get();
        List<UiEntry> rows = new ArrayList<>();
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
        String key = id.substring(id.indexOf(':') + 1);
        if (!settings.get().portals().containsKey(key)) return;
        SelectorMessages text = messages.get();
        Runnable reopen = () -> openAdmin(viewer, back);
        String what = text.subtitlePortal() + " " + key;
        switch (click) {
            case "left" -> askServer(viewer, server -> {
                settings.update(current -> {
                    Map<String, SelectorSettings.Portal> portals = new LinkedHashMap<>(current.portals());
                    portals.computeIfPresent(key, (k, old) -> new SelectorSettings.Portal(server, old.world(), old.x1(), old.y1(),
                            old.z1(), old.x2(), old.y2(), old.z2()));
                    return with(current, current.servers(), portals);
                });
                build();
                send(viewer, text.changed(), Values.of("what", what).put("server", serverName(server)));
                reopen.run();
            }, reopen);
            case "right" -> {
                viewer.closeInventory();
                regions.visualize(viewer, "portal-" + key);
            }
            case "shift" -> screens.confirm(viewer, Values.of("what", what).apply(text.confirmRemove()), () -> {
                settings.update(current -> {
                    Map<String, SelectorSettings.Portal> portals = new LinkedHashMap<>(current.portals());
                    portals.remove(key);
                    return with(current, current.servers(), portals);
                });
                build();
                send(viewer, text.removed(), Values.of("what", what));
            }, reopen);
            default -> {
            }
        }
    }

    private static SelectorSettings with(SelectorSettings current, Map<String, SelectorSettings.Server> servers,
                                         Map<String, SelectorSettings.Portal> portals) {
        return new SelectorSettings(current.command(), current.lobbyCommand(), current.lobbies(), servers, portals);
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
                .label(key -> servers.get(key).name()).key(key -> key).transports(AdminScreens.NO_SCREEN).open(), chosen, reopen);
    }

    private String nextId(Set<String> taken, String prefix) {
        int number = taken.size() + 1;
        while (taken.contains(prefix + number)) number++;
        return prefix + number;
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
            return with(current, current.servers(), portals);
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
