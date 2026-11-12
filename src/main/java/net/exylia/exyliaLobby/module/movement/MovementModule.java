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
        settings = settings(MovementSettings.class, b -> b.version(2).migration(1, MovementSettings::oneBlockPerPad));
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
            for (int i = 0; i < pad.blocks().size(); i++) {
                String[] at = pad.blocks().get(i).trim().split("\\s+");
                if (at.length != 4) {
                    plugin().getLogger().warning("Launch pad " + entry.getKey() + ": '" + pad.blocks().get(i) + "' is not 'world x y z', skipped.");
                    continue;
                }
                JumpPad built;
                try {
                    built = JumpPad.of(entry.getKey() + "#" + i, new PadTrigger.At(at[0], Integer.parseInt(at[1]),
                            Integer.parseInt(at[2]), Integer.parseInt(at[3])), pad.power(), pad.vertical()).withEffect(config.effect());
                } catch (NumberFormatException e) {
                    plugin().getLogger().warning("Launch pad " + entry.getKey() + ": '" + pad.blocks().get(i) + "' is not 'world x y z', skipped.");
                    continue;
                }
                list.add(pad.fixed() ? built.towards((float) pad.yaw()) : built);
            }
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
                new AdminScreens.Add("SLIME_BLOCK", text.addPadTitle(), text.addPadText(), text.addPadClick(), text.addPadNext(),
                        admin -> add(admin, false)),
                new AdminScreens.Add("SPYGLASS", text.addAimedTitle(), text.addAimedText(), text.addAimedClick(), text.addAimedNext(),
                        admin -> add(admin, true))),
                this::rows);
    }

    private volatile Runnable back = () -> {
    };

    private List<UiEntry> rows() {
        MovementMessages text = messages.get();
        List<UiEntry> rows = new ArrayList<>();
        for (Map.Entry<String, MovementSettings.Pad> entry : settings.get().pads().list().entrySet()) {
            MovementSettings.Pad pad = entry.getValue();
            List<String> clicks = new ArrayList<>(text.clicks());
            clicks.add(text.clickBlocks());
            rows.add(AdminScreens.row(entry.getKey(), "SLIME_BLOCK", entry.getKey(), text.rowSubtitle(), List.of(
                    AdminScreens.stat("map", text.labelBlocks(), "{info}" + pad.blocks().size()),
                    AdminScreens.stat("blaze", text.labelPower(), "{info}" + pad.power()),
                    AdminScreens.stat("arrow", text.labelVertical(), "{info}" + pad.vertical()),
                    AdminScreens.stat("compass", text.labelDirection(), pad.fixed()
                            ? Values.of("yaw", Math.round(pad.yaw())).apply(text.directionFixed()) : text.directionLook())),
                    clicks, false));
        }
        return rows;
    }

    private void click(Player viewer, String click, String id) {
        MovementSettings.Pad pad = settings.get().pads().list().get(id);
        if (pad == null) return;
        MovementMessages text = messages.get();
        Runnable reopen = () -> openAdmin(viewer, back);
        switch (click) {
            case "left" -> ask(viewer, text.promptPower(), pad.power(), 0.1, 8, value -> change(viewer, id, current -> current.withPower(value)));
            case "right" -> ask(viewer, text.promptVertical(), pad.vertical(), 0.1, 4, value -> change(viewer, id, current -> current.withVertical(value)));
            case "turn" -> {
                if (pad.fixed()) {
                    change(viewer, id, current -> current.aimed(false, current.yaw()));
                    screens.update(viewer);
                    return;
                }
                // The aim is a facing, so it is asked the way a spawn is: stand on the pad looking where it throws.
                viewer.closeInventory();
                setup(viewer, reopen).spawn(text.wizardAim(), id, where -> {
                    change(viewer, id, current -> current.aimed(true, where.getYaw()));
                    reopen.run();
                });
            }
            case "drop" -> {
                viewer.closeInventory();
                editBlocks(viewer, id, reopen);
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

    /**
     * Click a block to add it to the pad, or one of its blocks to take it off; the wizard comes back after each click
     * until cancelled, which reopens the list.
     */
    private void editBlocks(Player viewer, String id, Runnable reopen) {
        MovementMessages text = messages.get();
        setup(viewer, reopen).block(text.wizardBlocks(), id, at -> {
            MovementSettings.Pad pad = settings.get().pads().list().get(id);
            if (pad == null) {
                reopen.run();
                return;
            }
            String block = MovementSettings.block(at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
            if (!pad.blocks().contains(block) && owner(block) != null) {
                send(viewer, text.samePlace(), Values.of());
            } else {
                List<String> blocks = new ArrayList<>(pad.blocks());
                boolean added = !blocks.remove(block);
                if (added) blocks.add(block);
                savePads(list -> {
                    list.computeIfPresent(id, (key, old) -> old.withBlocks(blocks));
                    return list;
                });
                send(viewer, added ? text.blockAdded() : text.blockRemoved(), Values.of("pad", id).put("count", blocks.size()));
            }
            plugin().getServer().getScheduler().runTask(plugin(), () -> {
                if (viewer.isOnline()) editBlocks(viewer, id, reopen);
            });
        });
    }

    /** The pad that throws from a block, or {@code null}. */
    private String owner(String block) {
        for (Map.Entry<String, MovementSettings.Pad> entry : settings.get().pads().list().entrySet()) {
            if (entry.getValue().blocks().contains(block)) return entry.getKey();
        }
        return null;
    }

    private void ask(Player viewer, String prompt, double current, double min, double max, java.util.function.DoubleConsumer apply) {
        viewer.closeInventory();
        Runnable reopen = () -> openAdmin(viewer, back);
        screens.inputs().answered(viewer, screens.inputs().decimal(viewer, prompt)
                        .range(BigDecimal.valueOf(min), BigDecimal.valueOf(max)).defaultValue(BigDecimal.valueOf(current)).transports(AdminScreens.NO_SCREEN).open(),
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

    /** A new pad: a clicked block that throws the way the runner looks, or (aimed) the block stood on, throwing that way. */
    private void add(Player admin, boolean aimed) {
        MovementMessages text = messages.get();
        Runnable reopen = () -> openAdmin(admin, back);
        admin.closeInventory();
        if (aimed) {
            setup(admin, reopen).spawn(text.wizardAimed(), where -> {
                Block feet = where.getBlock();
                save(admin, feet.getType().isAir() ? feet.getRelative(0, -1, 0) : feet, true, where.getYaw(), reopen);
            });
        } else {
            setup(admin, reopen).block(text.wizardPad(), at -> save(admin, at.getBlock(), false, 0, reopen));
        }
    }

    private void save(Player admin, Block block, boolean fixed, float yaw, Runnable reopen) {
        MovementMessages text = messages.get();
        String at = MovementSettings.block(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (owner(at) != null) {
            send(admin, text.samePlace(), Values.of());
            reopen.run();
            return;
        }
        int number = settings.get().pads().list().size() + 1;
        while (settings.get().pads().list().containsKey("pad-" + number)) number++;
        String id = "pad-" + number;
        savePads(list -> {
            list.put(id, new MovementSettings.Pad(new ArrayList<>(List.of(at)), 1.6, 0.9, fixed, yaw));
            return list;
        });
        send(admin, text.padAdded(), Values.of("pad", id));
        reopen.run();
    }

    // ======================== shared ========================

    /** The launch pad on a block, if any. */
    public java.util.Optional<String> padAt(@NotNull Location location) {
        return pads == null ? java.util.Optional.empty() : pads.padAt(location).map(pad -> pad.id().replaceFirst("#\\d+$", ""));
    }

    private void send(Player to, String line, Values values) {
        PluginMessages.of(plugin()).send(to, line, values);
    }
}
