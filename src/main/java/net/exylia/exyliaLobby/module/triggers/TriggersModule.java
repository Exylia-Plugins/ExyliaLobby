package net.exylia.exyliaLobby.module.triggers;

import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.exyliaLobby.module.shared.AdminScreens;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.interaction.Click;
import net.exylia.lib.interaction.Interaction;
import net.exylia.lib.interaction.Interactions;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.util.TimeFormats;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Blocks and areas that run actions: a button, lever, pressure plate or any block clicked or stepped on, or an
 * area walked into or out of. Placed in game with the wizards; the actions are ExyliaLib interaction lines.
 */
public final class TriggersModule extends LobbyModule implements Listener {

    public static final String ID = "triggers";

    private ConfigFile<TriggersSettings> settings;
    private ConfigFile<TriggersMessages> messages;
    private AdminScreens screens;
    private volatile List<Compiled> compiled = List.of();
    private volatile Runnable back = () -> {
    };

    /** A trigger ready to run: its box and its compiled lines. */
    private record Compiled(String id, TriggersSettings.Trigger trigger, int[] min, int[] max,
                            Interaction actions, Interaction leave) {

        boolean contains(Location at) {
            return at.getWorld() != null && at.getWorld().getName().equals(trigger.world())
                    && at.getBlockX() >= min[0] && at.getBlockX() <= max[0]
                    && at.getBlockY() >= min[1] && at.getBlockY() <= max[1]
                    && at.getBlockZ() >= min[2] && at.getBlockZ() <= max[2];
        }
    }

    public TriggersModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.STONE_BUTTON;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(TriggersSettings.class);
        messages = messages(TriggersMessages.class);
    }

    @Override
    protected void start() {
        screens = new AdminScreens(context, ID, this::click);
        screens.register();
        build();
        settings.onReload(reloaded -> {
            if (isEnabled()) build();
        });
        listen(this);
    }

    @Override
    protected void stop() {
        compiled = List.of();
        screens.clear();
    }

    private void build() {
        List<Compiled> list = new ArrayList<>();
        settings.get().list().forEach((id, trigger) -> {
            int[] a = coordinates(trigger.min());
            int[] b = coordinates(trigger.max());
            if (a == null || b == null) {
                context.debug().warn("Trigger " + id + " is skipped: its corners are not 'x y z'");
                return;
            }
            int[] min = {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
            int[] max = {Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
            list.add(new Compiled(id, trigger, min, max,
                    Interactions.compile(plugin(), trigger.actions(), "trigger \"" + id + "\""),
                    Interactions.compile(plugin(), trigger.leaveActions(), "trigger \"" + id + "\" leave-actions")));
        });
        compiled = List.copyOf(list);
    }

    private static int[] coordinates(String text) {
        String[] parts = text == null ? new String[0] : text.trim().split("[ ,]+");
        if (parts.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException wrong) {
            return null;
        }
    }

    // ======================== running ========================

    /** Runs on cancelled events too: lobby protection cancels block use, and a trigger is meant to work there. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Click click = switch (event.getAction()) {
            case PHYSICAL -> Click.RIGHT;
            case RIGHT_CLICK_BLOCK -> event.getHand() == EquipmentSlot.HAND ? Click.of(false, event.getPlayer().isSneaking()) : null;
            case LEFT_CLICK_BLOCK -> Click.of(true, event.getPlayer().isSneaking());
            default -> null;
        };
        if (click == null) return;
        Location at = event.getClickedBlock().getLocation();
        for (Compiled trigger : compiled) {
            if (!trigger.trigger().area() && trigger.contains(at)) fire(event.getPlayer(), trigger, click);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock()) crossed(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        crossed(event.getPlayer(), event.getFrom(), event.getTo());
    }

    // ponytail: every area is checked on each block a player crosses; a spatial index if servers keep hundreds
    private void crossed(Player player, Location from, Location to) {
        for (Compiled trigger : compiled) {
            if (!trigger.trigger().area()) continue;
            boolean was = trigger.contains(from);
            boolean now = trigger.contains(to);
            if (now && !was) fire(player, trigger, Click.RIGHT);
            else if (was && !now && allowed(player, trigger)) trigger.leave().run(player, Click.RIGHT, data(trigger));
        }
    }

    private void fire(Player player, Compiled trigger, Click click) {
        if (trigger.actions().isEmpty() || !trigger.actions().handles(click) || !allowed(player, trigger)) return;
        Duration cooldown = trigger.trigger().cooldown();
        // Quiet on purpose: a plate fires on every step while it is stood on, so a refusal would flood the chat.
        if (cooldown != null && !cooldown.isZero() && !cooldowns().tryStart(player, ID + "_" + trigger.id(), cooldown)) return;
        trigger.actions().run(player, click, data(trigger));
    }

    private static boolean allowed(Player player, Compiled trigger) {
        String permission = trigger.trigger().permission();
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    private static Map<String, Object> data(Compiled trigger) {
        return Map.of("trigger", trigger.id());
    }

    // ======================== admin ========================

    @Override
    public boolean hasAdmin() {
        return true;
    }

    @Override
    public void openAdmin(@NotNull Player viewer, @NotNull Runnable back) {
        this.back = back;
        TriggersMessages text = messages.get();
        screens.open(viewer, back, "Modules", text.listTitle(), text.listKind(), List.of(
                new AdminScreens.Add("STONE_BUTTON", text.addBlockTitle(), text.addBlockText(), text.addBlockClick(), text.addBlockNext(),
                        admin -> add(admin, false)),
                new AdminScreens.Add("GLASS", text.addAreaTitle(), text.addAreaText(), text.addAreaClick(), text.addAreaNext(),
                        admin -> add(admin, true))),
                this::rows);
    }

    private List<UiEntry> rows() {
        TriggersMessages text = messages.get();
        List<UiEntry> rows = new ArrayList<>();
        settings.get().list().forEach((id, trigger) -> {
            String place = "{info}" + trigger.world() + " " + trigger.min() + (trigger.area() ? " → " + trigger.max() : "");
            int count = trigger.actions().size() + trigger.leaveActions().size();
            rows.add(AdminScreens.row(id, trigger.area() ? "GLASS" : "STONE_BUTTON", id,
                    trigger.area() ? text.areaSubtitle() : text.blockSubtitle(), List.of(
                            AdminScreens.stat("map", text.labelPlace(), place),
                            AdminScreens.stat("book", text.labelActions(), count == 0 ? text.noActions() : "{info}" + count),
                            AdminScreens.stat("clock", text.labelCooldown(), "{info}" + TimeFormats.render(
                                    trigger.cooldown() == null ? 0 : trigger.cooldown().toMillis() / 1000.0, TimeFormats.Style.PRECISE)),
                            AdminScreens.stat("locked", text.labelPermission(),
                                    trigger.permission().isBlank() ? text.everyone() : "{info}" + trigger.permission())),
                    text.clicks(), false));
        });
        return rows;
    }

    private void click(Player viewer, String click, String id) {
        TriggersSettings.Trigger trigger = settings.get().list().get(id);
        if (trigger == null) return;
        TriggersMessages text = messages.get();
        Runnable reopen = () -> openAdmin(viewer, back);
        switch (click) {
            case "left" -> {
                viewer.closeInventory();
                screens.inputs().answered(viewer, screens.inputs().text(viewer, text.promptAction()).maxLength(256).transports(AdminScreens.NO_SCREEN).open(),
                        line -> {
                            if (!line.isBlank()) {
                                List<String> lines = new ArrayList<>(trigger.actions());
                                lines.add(line.trim());
                                change(viewer, id, current -> current.withActions(lines));
                            }
                            reopen.run();
                        }, reopen);
            }
            case "right" -> {
                viewer.closeInventory();
                screens.inputs().answered(viewer, screens.inputs().duration(viewer, text.promptCooldown())
                                .atLeast(Duration.ZERO).defaultValue(trigger.cooldown()).transports(AdminScreens.NO_SCREEN).open(),
                        time -> {
                            change(viewer, id, current -> current.withCooldown(time));
                            reopen.run();
                        }, reopen);
            }
            case "turn" -> {
                if (trigger.actions().isEmpty()) return;
                change(viewer, id, current -> current.withActions(current.actions().subList(0, current.actions().size() - 1)));
                screens.update(viewer);
            }
            case "shift" -> screens.confirm(viewer, Values.of("trigger", id).apply(text.confirmRemove()), () -> {
                save(list -> {
                    list.remove(id);
                    return list;
                });
                send(viewer, text.removed(), Values.of("trigger", id));
            }, reopen);
            default -> {
            }
        }
    }

    private void add(Player admin, boolean area) {
        TriggersMessages text = messages.get();
        Runnable reopen = () -> openAdmin(admin, back);
        admin.closeInventory();
        if (area) {
            setup(admin, reopen).area(text.wizardArea(), result -> {
                var a = result.first();
                var b = result.second();
                create(admin, result.world().fallbackName(), a.x() + " " + a.y() + " " + a.z(), b.x() + " " + b.y() + " " + b.z(),
                        true, reopen);
            });
        } else {
            setup(admin, reopen).block(text.wizardBlock(), at -> {
                String corner = at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ();
                create(admin, at.getWorld().getName(), corner, corner, false, reopen);
            });
        }
    }

    private void create(Player admin, String world, String min, String max, boolean area, Runnable reopen) {
        Map<String, TriggersSettings.Trigger> list = settings.get().list();
        if (!area && list.values().stream().anyMatch(t -> !t.area() && t.world().equals(world) && t.min().equals(min))) {
            send(admin, messages.get().samePlace(), Values.of());
            reopen.run();
            return;
        }
        int number = list.size() + 1;
        while (list.containsKey("trigger-" + number)) number++;
        String id = "trigger-" + number;
        TriggersSettings.Trigger fresh = new TriggersSettings.Trigger();
        save(map -> {
            map.put(id, new TriggersSettings.Trigger(world, min, max, area, "", fresh.cooldown(), List.of(), List.of()));
            return map;
        });
        send(admin, messages.get().added(), Values.of("trigger", id));
        reopen.run();
    }

    private void change(Player viewer, String id, UnaryOperator<TriggersSettings.Trigger> edit) {
        save(list -> {
            TriggersSettings.Trigger old = list.get(id);
            if (old != null) list.put(id, edit.apply(old));
            return list;
        });
        send(viewer, messages.get().changed(), Values.of("trigger", id));
    }

    private void save(UnaryOperator<Map<String, TriggersSettings.Trigger>> change) {
        settings.update(current -> new TriggersSettings(change.apply(new LinkedHashMap<>(current.list()))));
        build();
    }

    private void send(Player to, String line, Values values) {
        PluginMessages.of(plugin()).send(to, line, values);
    }
}
