package net.exylia.exyliaLobby.module.movement;

import net.exylia.exyliaLobby.module.Busy;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.exyliaLobby.module.shared.AdminScreens;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.movement.DoubleJump;
import net.exylia.lib.movement.JumpPad;
import net.exylia.lib.movement.JumpPads;
import net.exylia.lib.movement.PadTrigger;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Double jump and launch pads inside the lobby. Players held by another module ({@link Busy}: a parkour run, a PvP
 * fight) are left alone. Pads are edited in game from the module's admin screen and stored in its settings.
 */
public final class MovementModule extends LobbyModule {

    public static final String ID = "movement";

    private ConfigFile<MovementSettings> settings;
    private ConfigFile<MovementMessages> messages;
    private DoubleJump doubleJump;
    private JumpPads pads;
    private AdminScreens screens;

    public MovementModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.SLIME_BLOCK;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(MovementSettings.class);
        messages = messages(MovementMessages.class);
    }

    @Override
    protected void start() {
        screens = new AdminScreens(context, ID, this::click);
        screens.register();
        build();
        settings.onReload(reloaded -> {
            if (isEnabled()) build();
        });
    }

    @Override
    protected void stop() {
        if (doubleJump != null) doubleJump.stop();
        if (pads != null) pads.stop();
        doubleJump = null;
        pads = null;
        screens.clear();
    }

    /** Whether the player may be moved by us now: in the lobby and not held by a run or a fight. */
    private boolean free(Player player) {
        return context.areas().contains(player) && !Busy.any(context, player);
    }

    private void build() {
        if (doubleJump != null) doubleJump.stop();
        if (pads != null) pads.stop();
        MovementSettings.DoubleJump jump = settings.get().doubleJump();
        doubleJump = null;
        if (jump.enabled()) {
            DoubleJump.Builder builder = DoubleJump.of(plugin())
                    .forward(jump.forward()).vertical(jump.vertical())
                    .cooldown(jump.cooldown()).effect(jump.effect())
                    .when(this::free);
            if (!jump.permission().isBlank()) builder.permission(jump.permission());
            doubleJump = builder.start();
        }
        MovementSettings.Pads config = settings.get().pads();
        pads = null;
        if (config.enabled()) {
            pads = JumpPads.of(plugin()).cooldown(config.cooldown()).when(this::free).pads(padList()).start();
        }
    }

    private List<JumpPad> padList() {
        MovementSettings.Pads config = settings.get().pads();
        List<JumpPad> list = new ArrayList<>();
        for (Map.Entry<String, MovementSettings.Pad> entry : config.list().entrySet()) {
            MovementSettings.Pad pad = entry.getValue();
            JumpPad built = JumpPad.of(entry.getKey(), new PadTrigger.At(pad.world(), pad.x(), pad.y(), pad.z()),
                    pad.power(), pad.vertical()).withEffect(config.effect());
            list.add(pad.fixed() ? built.towards((float) pad.yaw()) : built);
        }
        return list;
    }

    /** Saves the pads and hands them to the library. */
    private void savePads(UnaryOperator<Map<String, MovementSettings.Pad>> change) {
        settings.update(current -> {
            Map<String, MovementSettings.Pad> list = change.apply(new LinkedHashMap<>(current.pads().list()));
            MovementSettings.Pads old = current.pads();
            return new MovementSettings(current.doubleJump(), new MovementSettings.Pads(old.enabled(), old.cooldown(), old.effect(), list));
        });
        if (pads != null) pads.set(padList());
    }

    // ======================== admin ========================

    @Override
    public boolean hasAdmin() {
        return true;
    }

    @Override
    public void openAdmin(@NotNull Player viewer, @NotNull Runnable back) {
        this.back = back;
        MovementMessages text = messages.get();
        screens.open(viewer, back, "Modules", text.listTitle(), text.listKind(), List.of(
                new AdminScreens.Add("SLIME_BLOCK", text.addHereTitle(), text.addHereText(), text.addHereClick(), text.addHereNext(),
                        admin -> add(admin, back, false)),
                new AdminScreens.Add("SPYGLASS", text.addLookTitle(), text.addLookText(), text.addLookClick(), text.addLookNext(),
                        admin -> add(admin, back, true))),
                this::rows);
    }

    private volatile Runnable back = () -> {
    };

    private List<UiEntry> rows() {
        MovementMessages text = messages.get();
        List<UiEntry> rows = new ArrayList<>();
        for (Map.Entry<String, MovementSettings.Pad> entry : settings.get().pads().list().entrySet()) {
            MovementSettings.Pad pad = entry.getValue();
            rows.add(AdminScreens.row(entry.getKey(), "SLIME_BLOCK", entry.getKey(), text.rowSubtitle(), List.of(
                    AdminScreens.stat("map", text.labelPlace(), "{info}" + pad.world() + " " + pad.x() + " " + pad.y() + " " + pad.z()),
                    AdminScreens.stat("blaze", text.labelPower(), "{info}" + pad.power()),
                    AdminScreens.stat("arrow", text.labelVertical(), "{info}" + pad.vertical()),
                    AdminScreens.stat("compass", text.labelDirection(), pad.fixed()
                            ? Values.of("yaw", Math.round(pad.yaw())).apply(text.directionFixed()) : text.directionLook())),
                    text.clicks(), false));
        }
        return rows;
    }

    private void click(Player viewer, String click, String id) {
        MovementSettings.Pad pad = settings.get().pads().list().get(id);
        if (pad == null) return;
        MovementMessages text = messages.get();
        Runnable reopen = () -> openAdmin(viewer, back);
        switch (click) {
            case "left" -> ask(viewer, text.promptPower(), pad.power(), 0.1, 8, value -> change(viewer, id,
                    current -> new MovementSettings.Pad(current.world(), current.x(), current.y(), current.z(), value,
                            current.vertical(), current.fixed(), current.yaw())));
            case "right" -> ask(viewer, text.promptVertical(), pad.vertical(), 0.1, 4, value -> change(viewer, id,
                    current -> new MovementSettings.Pad(current.world(), current.x(), current.y(), current.z(), current.power(),
                            value, current.fixed(), current.yaw())));
            case "turn" -> {
                change(viewer, id, current -> new MovementSettings.Pad(current.world(), current.x(), current.y(), current.z(),
                        current.power(), current.vertical(), !current.fixed(), viewer.getLocation().getYaw()));
                screens.update(viewer);
            }
            case "shift" -> screens.confirm(viewer, Values.of("pad", id).apply(text.confirmRemove()), () -> {
                savePads(list -> {
                    list.remove(id);
                    return list;
                });
                send(viewer, text.padRemoved(), Values.of("pad", id));
            }, reopen);
            default -> {
            }
        }
    }

    private void ask(Player viewer, String prompt, double current, double min, double max, java.util.function.DoubleConsumer apply) {
        viewer.closeInventory();
        Runnable reopen = () -> openAdmin(viewer, back);
        screens.inputs().answered(viewer, screens.inputs().decimal(viewer, prompt)
                        .range(BigDecimal.valueOf(min), BigDecimal.valueOf(max)).defaultValue(BigDecimal.valueOf(current)).open(),
                value -> {
                    apply.accept(value.doubleValue());
                    reopen.run();
                }, reopen);
    }

    private void change(Player viewer, String id, UnaryOperator<MovementSettings.Pad> edit) {
        savePads(list -> {
            MovementSettings.Pad old = list.get(id);
            if (old != null) list.put(id, edit.apply(old));
            return list;
        });
        send(viewer, messages.get().padChanged(), Values.of("pad", id));
    }

    private void add(Player admin, Runnable back, boolean looking) {
        MovementMessages text = messages.get();
        Block block = looking ? admin.getTargetBlockExact(8) : admin.getLocation().getBlock();
        if (block == null) {
            send(admin, text.noTarget(), Values.of());
            return;
        }
        String world = block.getWorld().getName();
        for (MovementSettings.Pad pad : settings.get().pads().list().values()) {
            if (pad.world().equals(world) && pad.x() == block.getX() && pad.y() == block.getY() && pad.z() == block.getZ()) {
                send(admin, text.samePlace(), Values.of());
                return;
            }
        }
        int number = settings.get().pads().list().size() + 1;
        while (settings.get().pads().list().containsKey("pad-" + number)) number++;
        String id = "pad-" + number;
        savePads(list -> {
            list.put(id, new MovementSettings.Pad(world, block.getX(), block.getY(), block.getZ(), 1.6, 0.9, false, 0));
            return list;
        });
        send(admin, text.padAdded(), Values.of("pad", id));
        screens.update(admin);
    }

    // ======================== shared ========================

    /** The launch pad on a block, if any. */
    public java.util.Optional<String> padAt(@NotNull Location location) {
        return pads == null ? java.util.Optional.empty() : pads.padAt(location).map(JumpPad::id);
    }

    private void send(Player to, String line, Values values) {
        PluginMessages.of(plugin()).send(to, line, values);
    }
}
