package net.exylia.exyliaLobby.module.holograms;

import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.exyliaLobby.module.shared.AdminScreens;
import net.exylia.exyliaLobby.module.shared.HologramLook;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.hologram.Hologram;
import net.exylia.lib.hologram.Holograms;
import net.exylia.lib.task.TaskHandle;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Text holograms placed in game and stored in the module's settings. Other modules put boards of their own
 * (a parkour top, a PvP ranking) with {@link #place} and take them away with {@link #remove}.
 */
public final class HologramsModule extends LobbyModule {

    public static final String ID = "holograms";
    private static final String PREFIX = "lobby-";
    private static final String EXTERNAL = "lobby-ext-";

    /** A board another module placed: kept while this module is off so it comes back when it is switched on. */
    private record External(Location at, Supplier<List<String>> lines, double viewDistance) {
    }

    private final Map<String, External> external = new ConcurrentHashMap<>();
    private final Map<String, List<String>> shown = new ConcurrentHashMap<>();
    private final Set<String> placed = ConcurrentHashMap.newKeySet();
    private ConfigFile<HologramsSettings> settings;
    private ConfigFile<HologramsMessages> messages;
    private AdminScreens screens;
    private TaskHandle refresher;
    private volatile Runnable back = () -> {
    };

    public HologramsModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.OAK_SIGN;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(HologramsSettings.class);
        messages = messages(HologramsMessages.class);
    }

    @Override
    protected void start() {
        screens = new AdminScreens(context, ID, this::click);
        screens.register();
        build();
        settings.onReload(reloaded -> {
            if (isEnabled()) build();
        });
        // Boards of other modules change their lines now and then (a top moves): read them again every two seconds.
        refresher = context.tasks().runTimer(40L, 40L, this::refreshExternal);
    }

    @Override
    protected void stop() {
        if (refresher != null) refresher.cancel();
        refresher = null;
        clearPlaced();
        screens.clear();
    }

    // ======================== public hooks ========================

    /**
     * Puts a text hologram at a place; placing the same id again replaces it. {@code lines} is read about every two
     * seconds, so a board that follows a ranking just returns the current rows. Kept while this module is off and
     * drawn again when it is switched on; {@link #remove} takes it away for good.
     */
    public void place(@NotNull String id, @NotNull Location at, @NotNull Supplier<List<String>> lines) {
        External board = new External(at.clone(), lines, 48);
        external.put(id, board);
        if (isEnabled()) show(id, board);
    }

    /** Takes a board placed with {@link #place} away. False when there was none. */
    public boolean remove(@NotNull String id) {
        boolean had = external.remove(id) != null;
        shown.remove(id);
        if (placed.remove(EXTERNAL + id)) Holograms.remove(plugin(), EXTERNAL + id);
        return had;
    }

    // ======================== drawing ========================

    private void clearPlaced() {
        for (String id : placed) Holograms.remove(plugin(), id);
        placed.clear();
        shown.clear();
    }

    private void build() {
        clearPlaced();
        if (!Holograms.isSupported()) {
            context.debug().warn("Holograms need PacketEvents; none are drawn.");
            return;
        }
        for (Map.Entry<String, HologramsSettings.Hologram> entry : settings.get().holograms().entrySet()) {
            HologramsSettings.Hologram holo = entry.getValue();
            World world = Bukkit.getWorld(holo.world());
            if (world == null) continue;
            String id = PREFIX + entry.getKey();
            Holograms.show(plugin(), id, new Location(world, holo.x(), holo.y(), holo.z()),
                    HologramLook.text(holo.lines(), holo.viewDistance(), 0));
            placed.add(id);
        }
        external.forEach(this::show);
    }

    private void show(String id, External board) {
        if (!Holograms.isSupported()) return;
        List<String> lines = board.lines().get();
        shown.put(id, lines);
        Holograms.show(plugin(), EXTERNAL + id, board.at(), HologramLook.text(lines, board.viewDistance(), 0));
        placed.add(EXTERNAL + id);
    }

    private void refreshExternal() {
        external.forEach((id, board) -> {
            List<String> now = board.lines().get();
            if (now.equals(shown.get(id))) return;
            shown.put(id, now);
            Holograms.get(plugin(), EXTERNAL + id).ifPresent((Hologram hologram) -> hologram.lines(now));
        });
    }

    // ======================== admin ========================

    @Override
    public boolean hasAdmin() {
        return true;
    }

    @Override
    public void openAdmin(@NotNull Player viewer, @NotNull Runnable back) {
        this.back = back;
        HologramsMessages text = messages.get();
        screens.open(viewer, back, "Modules", text.listTitle(), text.listKind(), List.of(
                new AdminScreens.Add("OAK_SIGN", text.addSpotTitle(), text.addSpotText(), text.addSpotClick(), text.addSpotNext(),
                        admin -> add(admin, false)),
                new AdminScreens.Add("SPYGLASS", text.addBlockTitle(), text.addBlockText(), text.addBlockClick(), text.addBlockNext(),
                        admin -> add(admin, true))),
                this::rows);
    }

    private List<UiEntry> rows() {
        HologramsMessages text = messages.get();
        List<UiEntry> rows = new ArrayList<>();
        for (Map.Entry<String, HologramsSettings.Hologram> entry : settings.get().holograms().entrySet()) {
            HologramsSettings.Hologram holo = entry.getValue();
            rows.add(AdminScreens.row(entry.getKey(), "OAK_SIGN", entry.getKey(), text.rowSubtitle(), List.of(
                    AdminScreens.stat("map", text.labelPlace(), "{info}" + holo.world() + " " + Math.round(holo.x()) + " "
                            + Math.round(holo.y()) + " " + Math.round(holo.z())),
                    AdminScreens.stat("paper", text.labelLines(), "{info}" + holo.lines().size())), text.clicks(), false));
        }
        return rows;
    }

    private void click(Player viewer, String click, String id) {
        HologramsSettings.Hologram holo = settings.get().holograms().get(id);
        if (holo == null) return;
        HologramsMessages text = messages.get();
        Runnable reopen = () -> openAdmin(viewer, back);
        switch (click) {
            case "left" -> {
                viewer.closeInventory();
                screens.inputs().answered(viewer, screens.inputs().text(viewer, text.promptLines())
                                .defaultValue(String.join("<nl>", holo.lines())).open(),
                        value -> {
                            List<String> lines = Arrays.stream(value.split("<nl>")).toList();
                            change(id, current -> new HologramsSettings.Hologram(current.world(), current.x(), current.y(), current.z(),
                                    lines, current.viewDistance()));
                            send(viewer, text.changed(), Values.of("name", id));
                            reopen.run();
                        }, reopen);
            }
            case "right" -> {
                viewer.closeInventory();
                setup(viewer, reopen).spawn(text.wizardSpot(), id, where -> {
                    Location at = where.clone().add(0, 1.6, 0);
                    change(id, current -> new HologramsSettings.Hologram(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
                            current.lines(), current.viewDistance()));
                    send(viewer, text.moved(), Values.of("name", id));
                    reopen.run();
                });
            }
            case "shift" -> screens.confirm(viewer, Values.of("name", id).apply(text.confirmRemove()), () -> {
                save(map -> map.remove(id));
                send(viewer, text.removed(), Values.of("name", id));
            }, reopen);
            default -> {
            }
        }
    }

    private void change(String id, java.util.function.UnaryOperator<HologramsSettings.Hologram> edit) {
        save(map -> map.computeIfPresent(id, (key, old) -> edit.apply(old)));
    }

    private void save(java.util.function.Consumer<Map<String, HologramsSettings.Hologram>> change) {
        settings.update(current -> {
            Map<String, HologramsSettings.Hologram> map = new LinkedHashMap<>(current.holograms());
            change.accept(map);
            return new HologramsSettings(map);
        });
        build();
    }

    /** A new hologram where the admin stood, at eye height, or ({@code overBlock}) above a clicked block. */
    private void add(Player admin, boolean overBlock) {
        HologramsMessages text = messages.get();
        Runnable reopen = () -> openAdmin(admin, back);
        admin.closeInventory();
        if (overBlock) setup(admin, reopen).block(text.wizardBlock(), block -> create(admin, block.clone().add(0.5, 2, 0.5), reopen));
        else setup(admin, reopen).spawn(text.wizardSpot(), where -> create(admin, where.clone().add(0, 1.6, 0), reopen));
    }

    private void create(Player admin, Location at, Runnable reopen) {
        Map<String, HologramsSettings.Hologram> all = settings.get().holograms();
        int number = all.size() + 1;
        while (all.containsKey("board-" + number)) number++;
        String id = "board-" + number;
        HologramsSettings.Hologram base = new HologramsSettings.Hologram();
        save(map -> map.put(id, new HologramsSettings.Hologram(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
                base.lines(), base.viewDistance())));
        send(admin, messages.get().added(), Values.of("name", id));
        reopen.run();
    }

    private void send(Player to, String line, Values values) {
        PluginMessages.of(plugin()).send(to, line, values);
    }
}
