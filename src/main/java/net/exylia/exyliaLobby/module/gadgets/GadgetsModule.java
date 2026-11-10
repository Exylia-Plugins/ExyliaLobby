package net.exylia.exyliaLobby.module.gadgets;

import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.module.Busy;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.exyliaLobby.module.hotbar.HotbarModule;
import net.exylia.exyliaLobby.module.shared.Slots;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Languages;
import net.exylia.lib.item.Item;
import net.exylia.lib.item.Items;
import net.exylia.lib.packet.FakeBlocks;
import net.exylia.lib.packet.Packets;
import net.exylia.lib.settings.Setting;
import net.exylia.lib.settings.Settings;
import net.exylia.lib.text.Lore;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.Menus;
import net.exylia.lib.ui.UiEntry;
import net.exylia.lib.util.Cooldowns;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Toys for the lobby: grappling hook, ender ride, paint blaster, knockback stick, leap feather, snowball cannon
 * and trampoline. A player equips one from the menu (it goes in a hotbar slot the hotbar module keeps free);
 * every gadget has a cooldown and a permission, never damages and never changes a real block.
 *
 * <p>Players held by another module ({@link Busy}) and players who turned off "Gadgets affect me" are left
 * alone: nothing pushes, paints or bounces them, and they use no gadget while busy.
 */
public final class GadgetsModule extends LobbyModule {

    public static final String ID = "gadgets";
    static final String MENU = "gadgets/list";
    private static final String KEY = "gadgets-affect-me";
    private static final long RIDE_MS = 25_000;
    private static final long SAFE_MS = 4_000;

    private ConfigFile<GadgetsSettings> settings;
    private ConfigFile<GadgetsMessages> messages;
    private NamespacedKey marker;
    private NamespacedKey shotKey;
    private Runnable reservation;
    private final Map<UUID, String> equipped = new ConcurrentHashMap<>();
    /** Players hanging on to their pearl, and players who fall without taking damage, with when it ends. */
    private final Map<UUID, Long> rides = new ConcurrentHashMap<>();
    private final Map<UUID, Long> safe = new ConcurrentHashMap<>();
    private final Map<UUID, Long> bounced = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public GadgetsModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.ENDER_CHEST;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(GadgetsSettings.class);
        messages = messages(GadgetsMessages.class);
    }

    @Override
    protected List<Object> commands() {
        return command(settings.get().command(), new GadgetsCommand(this));
    }

    @Override
    protected void start() {
        marker = new NamespacedKey(plugin(), "gadget");
        shotKey = new NamespacedKey(plugin(), "gadget_shot");
        Languages.refresh(plugin(), GadgetsModule.class, "menus/gadgets");
        loadMenu();
        listen(new GadgetsListener(this));
        register();
        settings.onReload(reloaded -> {
            if (isEnabled()) reserve();
        });
        reserve();
        if (!Packets.isAvailable()) context.debug().warn("Gadgets: paint and trampolines need PacketEvents; they are refused.");
    }

    @Override
    protected void stop() {
        if (reservation != null) reservation.run();
        reservation = null;
        Settings.unregister(plugin(), KEY);
        equipped.clear();
        rides.clear();
        safe.clear();
        bounced.clear();
    }

    private void reserve() {
        if (reservation != null) reservation.run();
        reservation = context.registered(HotbarModule.ID, HotbarModule.class).map(hotbar -> hotbar.reserve(slot())).orElse(null);
    }

    private boolean inLobby(Location at) {
        return context.areas().contains(at);
    }

    private int slot() {
        return Math.max(0, Math.min(8, settings.get().slot()));
    }

    private void register() {
        GadgetsMessages text = messages.get();
        Settings.category(plugin(), "gadgets", "ENDER_CHEST", text.settingCategory(), text.settingCategoryText().toArray(String[]::new));
        Settings.register(plugin(), Setting.toggle(KEY, true)
                .category("gadgets")
                .icon("SLIME_BALL")
                .name(text.settingName())
                .description(text.settingDescription().toArray(String[]::new))
                .build());
    }

    @Override
    protected void actions(PluginActions actions) {
        actions.registerSync("gadgets_equip", (action, args) -> {
            equip(action.player(), args.string(0, ""));
            return ActionResult.success();
        });
        actions.registerSync("gadgets_off", (action, args) -> {
            putAway(action.player(), true);
            return ActionResult.success();
        });
    }

    private void loadMenu() {
        context.hub().menu(MENU);
    }

    // ======================== public hooks ========================

    /** Opens the gadgets menu. */
    public void open(@NotNull Player player) {
        UUID id = player.getUniqueId();
        Map<String, Object> values = new HashMap<>();
        values.put("equipped_name", (Supplier<Object>) () -> {
            String now = equipped.get(id);
            return now == null ? "None" : card(now).name();
        });
        // The summary card only offers the click while something is equipped.
        GadgetsMessages text = messages.get();
        values.put("equipped_action", (Supplier<Object>) () -> equipped.get(id) == null ? "{letters_black}Equip one below."
                : "{primary}{icon:click} " + text.clickPut() + "<nl>{letters_black}" + text.clickPutNext());
        Menus.of(plugin()).open(player, MENU, values, () -> rows(player));
    }

    /** The id of the gadget equipped by the player, if any. */
    public @NotNull java.util.Optional<String> equipped(@NotNull Player player) {
        return java.util.Optional.ofNullable(equipped.get(player.getUniqueId()));
    }

    // ======================== the menu ========================

    private GadgetsMessages.Card card(String id) {
        return messages.get().cards().getOrDefault(id, new GadgetsMessages.Card(id, List.of(), "Right-click", "use it"));
    }

    private boolean owns(Player player, GadgetsSettings.Gadget gadget) {
        return gadget.permission().isBlank() || player.hasPermission(gadget.permission());
    }

    private List<UiEntry> rows(Player player) {
        GadgetsMessages text = messages.get();
        String mine = equipped.get(player.getUniqueId());
        List<UiEntry> rows = new ArrayList<>();
        for (Map.Entry<String, GadgetsSettings.Gadget> entry : settings.get().gadgets().entrySet()) {
            String id = entry.getKey();
            GadgetsSettings.Gadget gadget = entry.getValue();
            if (!gadget.enabled()) continue;
            boolean owned = owns(player, gadget);
            boolean on = id.equals(mine);
            Lore.Tone tone = on ? Lore.Tone.SUCCESS : owned ? Lore.Tone.INFO : Lore.Tone.WARNING;
            GadgetsMessages.Card card = card(id);
            Lore lore = Lore.of(tone).subtitle(on ? text.kindEquipped() : owned ? text.kind() : text.kindLocked())
                    .section("Information");
            for (String line : card.text()) lore.text(line);
            lore.section("✦", "Status").stat("clock", "info", text.labelCooldown(), gadget.cooldown() + "s");
            if (!owned) {
                lore.stat("locked", "warning", text.labelNeeds(), gadget.permission());
                lore.line("").note(text.lockedNote());
            } else if (on) {
                lore.action(text.clickPut()).note(text.clickPutNext());
            } else {
                lore.action(text.clickEquip()).note(text.clickEquipNext());
            }
            rows.add(UiEntry.of(id)
                    .with("row_id", id)
                    .with("row_icon", gadget.material())
                    .withFormatted("row_name", Lore.name(tone, text.kind(), card.name()))
                    .withFormatted("row_lore", String.join("<nl>", lore.lines()))
                    .with("row_glow", String.valueOf(on))
                    .build());
        }
        return rows;
    }

    // ======================== equipping ========================

    void equip(Player player, String id) {
        GadgetsSettings.Gadget gadget = settings.get().gadgets().get(id);
        GadgetsMessages text = messages.get();
        if (gadget == null || !gadget.enabled()) return;
        Values values = Values.of("gadget", card(id).name());
        if (!inLobby(player) || Busy.any(context, player)) {
            send(player, text.notNow(), values);
            return;
        }
        if (!owns(player, gadget)) {
            send(player, text.noPermission(), values);
            return;
        }
        if (id.equals(equipped.get(player.getUniqueId()))) {
            putAway(player, true);
            return;
        }
        putAway(player, false);
        Material material = Material.matchMaterial(gadget.material());
        GadgetsMessages.Card card = card(id);
        Lore.Tone tone = Lore.Tone.SUCCESS;
        List<String> lore = Lore.of(tone).subtitle(text.kindEquipped()).section("Information")
                .text(card.text().isEmpty() ? "" : card.text().get(0))
                .click(card.click(), card.use()).lines();
        ItemStack item = Items.of(plugin()).render(Item.of(material == null ? "STICK" : material.name())
                .name(Lore.name(tone, text.kind(), card.name())).lore(lore).build(), player);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(marker, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        Slots.place(plugin(), player, slot(), item);
        equipped.put(player.getUniqueId(), id);
        send(player, text.equipped(), values);
    }

    private void putAway(Player player, boolean say) {
        boolean had = equipped.remove(player.getUniqueId()) != null;
        strip(player);
        if (had && say) send(player, messages.get().putAway(), Values.of());
    }

    private void strip(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int index = 0; index < inventory.getSize(); index++) {
            if (gadgetOf(inventory.getItem(index)) != null) inventory.setItem(index, null);
        }
    }

    /** The gadget id a marked item carries, or null for any other item. */
    @Nullable String gadgetOf(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(marker, PersistentDataType.STRING);
    }

    @Override
    protected void enter(@NotNull Player player, @NotNull AreaCause cause) {
        // Whatever a crash left in the inventory goes; the player equips again from the menu.
        strip(player);
        equipped.remove(player.getUniqueId());
    }

    @Override
    protected void leave(@NotNull Player player, @NotNull AreaCause cause) {
        UUID id = player.getUniqueId();
        equipped.remove(id);
        rides.remove(id);
        safe.remove(id);
        bounced.remove(id);
        strip(player);
    }

    // ======================== rules ========================

    /** Whether other players' gadgets may touch this player now. */
    private boolean affected(Player player) {
        return inLobby(player) && Settings.enabled(player, plugin(), KEY) && !Busy.any(context, player);
    }

    /** Checks permission, switch, place and cooldown for a use; tells the player why not. */
    private boolean ready(Player player, String id) {
        GadgetsSettings.Gadget gadget = settings.get().gadgets().get(id);
        GadgetsMessages text = messages.get();
        Values values = Values.of("gadget", card(id).name());
        if (gadget == null || !gadget.enabled()) {
            send(player, text.unavailable(), values);
            return false;
        }
        if (!inLobby(player) || Busy.any(context, player)) {
            send(player, text.notNow(), values);
            return false;
        }
        if (!owns(player, gadget)) {
            send(player, text.noPermission(), values);
            return false;
        }
        if (!Cooldowns.tryStart(player, "lobby_gadget_" + id, Duration.ofSeconds(Math.max(0, gadget.cooldown())))) {
            send(player, text.cooling(), values.put("seconds", Cooldowns.remainingWholeSeconds(player, "lobby_gadget_" + id)));
            return false;
        }
        return true;
    }

    private void keepSafe(Player player) {
        safe.put(player.getUniqueId(), System.currentTimeMillis() + SAFE_MS);
    }

    boolean safe(Player player) {
        Long until = safe.get(player.getUniqueId());
        if (until == null) return false;
        if (until > System.currentTimeMillis()) return true;
        safe.remove(player.getUniqueId(), until);
        return false;
    }

    boolean riding(Player player) {
        Long until = rides.get(player.getUniqueId());
        if (until == null) return false;
        if (until > System.currentTimeMillis()) return true;
        rides.remove(player.getUniqueId(), until);
        return false;
    }

    // ======================== the gadgets ========================

    /** A right-click with a gadget that is not the grappling hook. */
    void use(Player player, String id) {
        if (!ready(player, id)) return;
        GadgetsSettings.Gadget gadget = settings.get().gadgets().get(id);
        switch (id) {
            case "leap" -> {
                Vector push = player.getLocation().getDirection().multiply(gadget.power()).setY(0.55 + gadget.power() * 0.15);
                player.setVelocity(push);
                keepSafe(player);
            }
            case "ender" -> ride(player, gadget);
            case "paint" -> {
                if (!Packets.isAvailable()) {
                    send(player, messages.get().unavailable(), Values.of("gadget", card(id).name()));
                    return;
                }
                shoot(player, "paint", 1.6, 0);
            }
            case "snowball" -> {
                for (int index = 0; index < 3; index++) shoot(player, "snowball", 1.5, 0.07);
            }
            case "trampoline" -> pad(player, gadget);
            default -> {
            }
        }
    }

    /** Whether a cast of the grappling hook is allowed now (cancels the cast otherwise). */
    boolean cast(Player player) {
        GadgetsSettings.Gadget gadget = settings.get().gadgets().get("grapple");
        GadgetsMessages text = messages.get();
        Values values = Values.of("gadget", card("grapple").name());
        if (gadget == null || !gadget.enabled()) return false;
        if (!inLobby(player) || Busy.any(context, player)) {
            send(player, text.notNow(), values);
            return false;
        }
        if (!owns(player, gadget)) {
            send(player, text.noPermission(), values);
            return false;
        }
        if (Cooldowns.isActive(player, "lobby_gadget_grapple")) {
            send(player, text.cooling(), values.put("seconds", Cooldowns.remainingWholeSeconds(player, "lobby_gadget_grapple")));
            return false;
        }
        return true;
    }

    /** The hook: it pulls the rider to where it landed, and never hooks another player. */
    void fished(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!"grapple".equals(gadgetOf(player.getInventory().getItemInMainHand()))) return;
        FishHook hook = event.getHook();
        switch (event.getState()) {
            case CAUGHT_ENTITY, CAUGHT_FISH -> event.setCancelled(true);
            case IN_GROUND, REEL_IN -> {
                // Only a hook that landed on something pulls; reeling in an empty cast does nothing.
                if (event.getState() == PlayerFishEvent.State.REEL_IN && !hook.isOnGround()) return;
                GadgetsSettings.Gadget gadget = settings.get().gadgets().get("grapple");
                if (gadget == null || !ready(player, "grapple")) return;
                Vector to = hook.getLocation().toVector().subtract(player.getLocation().toVector());
                double distance = to.length();
                if (distance < 1.5) return;
                double speed = Math.min(2.4, 0.6 + distance * 0.12) * gadget.power();
                player.setVelocity(to.normalize().multiply(speed).setY(Math.max(0.35, to.getY() / distance * speed + 0.3)));
                keepSafe(player);
                hook.remove();
            }
            default -> {
            }
        }
    }

    private void ride(Player player, GadgetsSettings.Gadget gadget) {
        EnderPearl pearl = player.launchProjectile(EnderPearl.class);
        pearl.setVelocity(player.getLocation().getDirection().multiply(gadget.power()));
        pearl.getPersistentDataContainer().set(shotKey, PersistentDataType.STRING, "ender");
        pearl.addPassenger(player);
        rides.put(player.getUniqueId(), System.currentTimeMillis() + RIDE_MS);
        keepSafe(player);
    }

    private void shoot(Player player, String kind, double speed, double spread) {
        Snowball ball = player.launchProjectile(Snowball.class);
        Vector direction = player.getLocation().getDirection();
        if (spread > 0) {
            direction.add(new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).multiply(spread));
        }
        ball.setVelocity(direction.multiply(speed));
        ball.getPersistentDataContainer().set(shotKey, PersistentDataType.STRING, kind);
    }

    /** The knockback stick: a hit pushes, never hurts. True cancels the hit. */
    boolean stick(Player attacker, Player victim) {
        if (!"knockback".equals(gadgetOf(attacker.getInventory().getItemInMainHand()))) return false;
        if (attacker == victim || !affected(victim)) return true;
        if (!ready(attacker, "knockback")) return true;
        GadgetsSettings.Gadget gadget = settings.get().gadgets().get("knockback");
        Vector away = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 1.0E-4) away = attacker.getLocation().getDirection().setY(0);
        victim.setVelocity(away.normalize().multiply(gadget.power()).setY(0.35));
        return true;
    }

    /** A thrown pearl, paint or snowball landing. */
    void hit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String kind = projectile.getPersistentDataContainer().get(shotKey, PersistentDataType.STRING);
        if (kind == null) return;
        switch (kind) {
            case "ender" -> {
                for (Entity rider : new ArrayList<>(projectile.getPassengers())) {
                    if (rider instanceof Player player) {
                        keepSafe(player);
                        // Their pearl's teleport is refused only for this landing, not for a later pearl.
                        context.tasks().runAtEntityLater(player, 10L, () -> rides.remove(player.getUniqueId()));
                    }
                }
            }
            case "paint" -> {
                if (event.getHitEntity() != null) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getHitBlock() != null) paint(event.getHitBlock());
            }
            case "snowball" -> {
                if (event.getHitEntity() instanceof Player target) {
                    if (!affected(target) || target.equals(projectile.getShooter())) {
                        event.setCancelled(true);
                        return;
                    }
                    GadgetsSettings.Gadget gadget = settings.get().gadgets().get("snowball");
                    Vector push = projectile.getVelocity().clone().setY(0);
                    if (push.lengthSquared() > 1.0E-4) {
                        target.setVelocity(target.getVelocity().add(push.normalize().multiply(gadget.power()).setY(0.2)));
                    }
                }
            }
            default -> {
            }
        }
    }

    // ======================== fake blocks ========================

    private List<Player> viewers(Location at) {
        List<Player> list = new ArrayList<>();
        double radius = settings.get().viewRadius();
        for (Player player : at.getWorld().getNearbyPlayers(at, radius)) {
            if (affected(player)) list.add(player);
        }
        return list;
    }

    private void showFor(Location at, Map<Location, BlockData> blocks, int seconds) {
        FakeBlocks fake = Packets.of(plugin()).fakeBlocks();
        List<UUID> shown = new ArrayList<>();
        for (Player viewer : viewers(at)) {
            fake.show(viewer, blocks);
            shown.add(viewer.getUniqueId());
        }
        if (context.stopping()) return;
        context.tasks().runLater(Math.max(1, seconds) * 20L, () -> {
            for (UUID uuid : shown) {
                Player viewer = Bukkit.getPlayer(uuid);
                if (viewer != null) fake.clear(viewer, blocks.keySet());
            }
        });
    }

    private void paint(Block hit) {
        GadgetsSettings config = settings.get();
        GadgetsSettings.Gadget gadget = config.gadgets().get("paint");
        if (gadget == null || config.paintColors().isEmpty() || !inLobby(hit.getLocation())) return;
        Material colour = Material.matchMaterial(config.paintColors().get(random.nextInt(config.paintColors().size())));
        if (colour == null) return;
        BlockData data = colour.createBlockData();
        Map<Location, BlockData> blocks = new HashMap<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = hit.getRelative(x, y, z);
                    boolean exposed = block.getRelative(org.bukkit.block.BlockFace.UP).isPassable()
                            || block.getRelative(org.bukkit.block.BlockFace.DOWN).isPassable()
                            || block.getRelative(org.bukkit.block.BlockFace.NORTH).isPassable()
                            || block.getRelative(org.bukkit.block.BlockFace.SOUTH).isPassable()
                            || block.getRelative(org.bukkit.block.BlockFace.EAST).isPassable()
                            || block.getRelative(org.bukkit.block.BlockFace.WEST).isPassable();
                    if (block.getType().isSolid() && block.getType().isOccluding() && exposed && inLobby(block.getLocation())) {
                        blocks.put(block.getLocation(), data);
                    }
                }
            }
        }
        if (!blocks.isEmpty()) showFor(hit.getLocation(), blocks, gadget.seconds());
    }

    /** A 3x3 slime pad under the player's feet; while it lasts, anyone who lands on it bounces. */
    private void pad(Player player, GadgetsSettings.Gadget gadget) {
        if (!Packets.isAvailable()) {
            send(player, messages.get().unavailable(), Values.of("gadget", card("trampoline").name()));
            return;
        }
        Location feet = player.getLocation();
        Block centre = feet.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
        BlockData slime = Material.SLIME_BLOCK.createBlockData();
        Map<Location, BlockData> blocks = new HashMap<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = centre.getRelative(x, 0, z);
                if (inLobby(block.getLocation())) blocks.put(block.getLocation(), slime);
            }
        }
        Location middle = centre.getLocation().add(0.5, 1, 0.5);
        showFor(middle, blocks, gadget.seconds());
        int[] ticks = {Math.max(1, gadget.seconds()) * 10};
        double strength = gadget.power();
        context.tasks().runAtLocationTimer(middle, 2L, 2L, handle -> {
            if (ticks[0]-- <= 0 || context.stopping()) {
                handle.cancel();
                return;
            }
            World world = middle.getWorld();
            for (Player near : world.getNearbyPlayers(middle, 2.5)) {
                if (!affected(near)) continue;
                double dy = near.getLocation().getY() - middle.getY();
                if (Math.abs(near.getLocation().getX() - middle.getX()) > 1.8 || Math.abs(near.getLocation().getZ() - middle.getZ()) > 1.8
                        || dy < -0.3 || dy > 1.2 || near.getVelocity().getY() > 0.1) continue;
                long now = System.currentTimeMillis();
                Long last = bounced.get(near.getUniqueId());
                if (last != null && now - last < 400) continue;
                bounced.put(near.getUniqueId(), now);
                context.tasks().runAtEntity(near, () -> {
                    near.setVelocity(near.getVelocity().setY(0.9 * strength + 0.2));
                    keepSafe(near);
                });
            }
        });
    }

    private void send(Player to, String line, Values values) {
        PluginMessages.of(plugin()).send(to, line, values);
    }
}
