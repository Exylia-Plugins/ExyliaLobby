package net.exylia.exyliaLobby.module.pvp;

import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.module.Busy;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.exyliaLobby.module.holograms.HologramsModule;
import net.exylia.exyliaLobby.module.hotbar.HotbarModule;
import net.exylia.exyliaLobby.module.protection.ProtectionModule;
import net.exylia.exyliaLobby.module.state.StateModule;
import net.exylia.exyliaLobby.module.shared.AdminScreens;
import net.exylia.exyliaLobby.module.shared.Slots;
import net.exylia.exyliaLobby.module.spawn.SpawnModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Languages;
import net.exylia.lib.database.Databases;
import net.exylia.lib.database.Repository;
import net.exylia.lib.item.Item;
import net.exylia.lib.item.Items;
import net.exylia.lib.placeholder.Placeholders;
import net.exylia.lib.region.Cuboid;
import net.exylia.lib.region.PluginRegions;
import net.exylia.lib.region.PolicySet;
import net.exylia.lib.region.RegionSnapshot;
import net.exylia.lib.region.Regions;
import net.exylia.lib.region.WorldIdentity;
import net.exylia.lib.respawn.RespawnHook;
import net.exylia.lib.respawn.Respawns;
import net.exylia.lib.session.PluginStays;
import net.exylia.lib.session.Stays;
import net.exylia.lib.task.TaskHandle;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.Menus;
import net.exylia.lib.ui.UiEntry;
import net.exylia.lib.util.reward.OverflowPolicy;
import net.exylia.lib.util.reward.PendingRewards;
import net.exylia.lib.util.reward.RewardEntry;
import net.exylia.lib.util.reward.Rewards;
import net.exylia.lib.database.wipe.Wipes;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Lobby PvP. Holding the sword puts the player's kit on (their own things kept safe in an ExyliaLib stay), and
 * only two players in that state can hurt each other. Everything is given back on opt-out, death, quit, leaving
 * the lobby, a module switch-off or a crash.
 *
 * <p>Other modules use {@link #busy}, {@link #fighting}, {@link #kills}, {@link #deaths}, {@link #streak} and
 * {@link #top}.
 */
public final class PvpModule extends LobbyModule implements Busy {

    public static final String ID = "pvp";
    public static final String PERMISSION = "exylialobby.pvp";
    static final String TOP_MENU = "pvp/top";
    private static final int TOP_MAX = 36;
    private static final String BOARD = "pvp-top";

    private ConfigFile<PvpSettings> settings;
    private ConfigFile<PvpMessages> messages;
    private Repository<PvpStat> repo;
    private PluginStays stays;
    private PluginRegions regions;
    private AdminScreens screens;
    private NamespacedKey marker;
    private NamespacedKey kitMarker;
    private RespawnHook locationHook;
    private RespawnHook afterHook;
    private Runnable damageHook;
    private TaskHandle topTimer;
    private TaskHandle zoneTimer;
    private volatile Runnable back = () -> {
    };
    private volatile List<PvpStat> top = List.of();

    /** Players in the fight (kit on or going on). */
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    /** Players whose kit is on because of a zone rather than the sword. */
    private final Set<UUID> zoned = ConcurrentHashMap.newKeySet();
    /** Dead fighters waiting for their respawn, and fighters whose things are on the way back. */
    private final Set<UUID> dead = ConcurrentHashMap.newKeySet();
    private final Set<UUID> restoring = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Countdown> countdowns = new ConcurrentHashMap<>();
    private final Map<UUID, Tag> tags = new ConcurrentHashMap<>();
    private final Map<UUID, PvpStat> cache = new ConcurrentHashMap<>();
    /** When each killer last killed each victim ("killer:victim"), for the repeat-kill window. */
    private final Map<String, Long> lastKills = new ConcurrentHashMap<>();

    private record Tag(long until, UUID opponent) {
    }

    private static final class Countdown {
        final boolean entering;
        final int total;
        int left;
        final BossBar bar;
        TaskHandle task;

        Countdown(boolean entering, int total, BossBar bar) {
            this.entering = entering;
            this.total = total;
            this.left = total;
            this.bar = bar;
        }
    }

    public PvpModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.IRON_SWORD;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(PvpSettings.class);
        messages = messages(PvpMessages.class);
    }

    @Override
    protected List<Object> commands() {
        List<Object> list = new ArrayList<>(command(settings.get().topCommand(), new PvpCommand(this)));
        list.addAll(adminCommand(new PvpAdminCommand(this)));
        return list;
    }

    @Override
    protected void start() {
        marker = new NamespacedKey(plugin(), "pvp_sword");
        kitMarker = new NamespacedKey(plugin(), "pvp_kit");
        repo = Databases.of(plugin()).repository(PvpStat.class);
        stays = Stays.of(plugin());
        regions = Regions.of(plugin(), ID);
        Languages.refresh(plugin(), PvpModule.class, "menus/pvp");
        loadMenu();
        screens = new AdminScreens(context, ID, this::click);
        screens.register();
        listen(new PvpListener(this));
        context.registered(ProtectionModule.ID, ProtectionModule.class).ifPresent(protection ->
                damageHook = protection.allowDamage(this::canHit));
        locationHook = Respawns.of(plugin()).location(EventPriority.HIGH, ctx -> dead.contains(ctx.player().getUniqueId())
                ? respawnAt(ctx.player()) : null);
        afterHook = Respawns.of(plugin()).after(this::respawned);
        Wipes.of(plugin()).onPlayerReset(uuid -> {
            cache.remove(uuid);
            refreshTop();
        });
        Rewards.of(plugin()).overflow(OverflowPolicy.QUEUE).pending(PendingRewards.database(plugin())).claimOnJoin();
        Placeholders.group(plugin(), "lobby")
                .add("pvp_kills", request -> request.hasViewer() ? stat(request.viewer().getUniqueId()).kills() : 0)
                .add("pvp_deaths", request -> request.hasViewer() ? stat(request.viewer().getUniqueId()).deaths() : 0)
                .add("pvp_streak", request -> request.hasViewer() ? stat(request.viewer().getUniqueId()).streak() : 0)
                .add("pvp_top", request -> topValue(request.arg(0, 1), request.arg(1, "name")))
                .register();
        buildZones();
        buildBoard();
        settings.onReload(reloaded -> {
            if (isEnabled()) {
                buildZones();
                buildBoard();
            }
        });
        refreshTop();
        int every = Math.max(5, settings.get().topRefreshSeconds()) * 20;
        topTimer = context.tasks().runAsyncTimer(every, every, this::refreshTop);
        zoneTimer = context.tasks().runTimer(20L, 20L, this::checkZones);
    }

    @Override
    protected void stop() {
        if (topTimer != null) topTimer.cancel();
        if (zoneTimer != null) zoneTimer.cancel();
        if (locationHook != null) locationHook.cancel();
        if (afterHook != null) afterHook.cancel();
        if (damageHook != null) damageHook.run();
        damageHook = null;
        context.registered(HologramsModule.ID, HologramsModule.class).ifPresent(holograms -> holograms.remove(BOARD));
        if (regions != null) regions.replaceAll(List.of());
        screens.clear();
        countdowns.values().forEach(c -> {
            if (c.task != null) c.task.cancel();
        });
        countdowns.clear();
        active.clear();
        zoned.clear();
        dead.clear();
        restoring.clear();
        tags.clear();
        cache.clear();
        lastKills.clear();
    }

    @Override
    protected void actions(net.exylia.lib.action.PluginActions actions) {
        actions.registerSync("pvp_top", (action, args) -> {
            openTop(action.player());
            return net.exylia.lib.action.ActionResult.success();
        });
    }

    // ======================== public hooks ========================

    /** Busy while opted in, on the way in or out, dead and waiting to respawn, or in combat. */
    @Override
    public boolean busy(@NotNull Player player) {
        UUID id = player.getUniqueId();
        return active.contains(id) || restoring.contains(id) || dead.contains(id) || tagged(id);
    }

    /** Whether the player is in the fight with their kit on. */
    public boolean fighting(@NotNull Player player) {
        return active.contains(player.getUniqueId());
    }

    public int kills(@NotNull UUID player) {
        return stat(player).kills();
    }

    public int deaths(@NotNull UUID player) {
        return stat(player).deaths();
    }

    public int streak(@NotNull UUID player) {
        return stat(player).streak();
    }

    /** The cached kills top, best first (refreshed on a timer). */
    public @NotNull List<PvpStat> top() {
        return top;
    }

    /** Opens the kills top. */
    public void openTop(@NotNull Player player) {
        UUID id = player.getUniqueId();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("my_kills", (Supplier<Object>) () -> stat(id).kills());
        values.put("my_deaths", (Supplier<Object>) () -> stat(id).deaths());
        values.put("my_streak", (Supplier<Object>) () -> stat(id).streak());
        values.put("my_best", (Supplier<Object>) () -> stat(id).bestStreak());
        values.put("my_rank", (Supplier<Object>) () -> rankOf(id) == 0 ? "—" : rankOf(id));
        Menus.of(plugin()).open(player, TOP_MENU, values, this::topRows);
    }

    // ======================== for the listener ========================

    /** A hit may land when both players are in the fight. */
    boolean canHit(Player attacker, Player victim) {
        return active.contains(attacker.getUniqueId()) && active.contains(victim.getUniqueId());
    }

    boolean guardsDamage() {
        return context.module(ProtectionModule.ID, ProtectionModule.class).isEmpty();
    }

    boolean inLobbyNow(Player player) {
        return inLobby(player);
    }

    PluginRegions regions() {
        return regions;
    }

    boolean isSword(@Nullable ItemStack item) {
        return marked(item, marker);
    }

    /** A piece of the fighter's kit: never leaves the fight. */
    boolean isKit(@Nullable ItemStack item) {
        return marked(item, kitMarker);
    }

    private static boolean marked(@Nullable ItemStack item, NamespacedKey key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Whether a click or drag moving this item is refused: the sword always, the kit while fighting. */
    boolean locked(Player player, @Nullable ItemStack item) {
        return isSword(item) || (fighting(player) && isKit(item));
    }

    /** Takes the sword and kit pieces out of an inventory that is not the player's (a chest that got one). */
    void sweep(org.bukkit.inventory.Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isSword(item) || isKit(item)) inventory.setItem(slot, null);
        }
    }

    /** Whether dropping this item is refused: the sword, or anything while in the fight. */
    boolean keeps(Player player, ItemStack item) {
        return isSword(item) || fighting(player);
    }

    private int swordSlot() {
        return context.module(HotbarModule.ID, HotbarModule.class).map(HotbarModule::swordSlot)
                .orElse(settings.get().swordSlot());
    }

    /** A slot change. True cancels it (in combat, the sword stays out). */
    boolean held(Player player, int slot) {
        UUID id = player.getUniqueId();
        if (!inLobby(player) || zoned.contains(id) || restoring.contains(id) || dead.contains(id)) return false;
        boolean onSword = slot == swordSlot();
        if (active.contains(id)) {
            if (onSword) {
                cancelCountdown(player);
                return false;
            }
            if (tagged(id)) {
                send(player, messages.get().tagged(), Values.of("seconds", left(id)));
                return true;
            }
            if (!countdowns.containsKey(id)) countdown(player, false);
            return false;
        }
        if (onSword) {
            if (!allowed(player)) return false;
            if (!countdowns.containsKey(id)) countdown(player, true);
        } else {
            cancelCountdown(player);
        }
        return false;
    }

    /** A recorded hit: both players are tagged and remember each other. */
    void hit(Player attacker, Player victim) {
        if (!canHit(attacker, victim)) return;
        long until = System.currentTimeMillis() + settings.get().tagSeconds() * 1000L;
        tags.put(attacker.getUniqueId(), new Tag(until, victim.getUniqueId()));
        tags.put(victim.getUniqueId(), new Tag(until, attacker.getUniqueId()));
    }

    void joined(Player player) {
        // A crash or a shutdown mid-fight left a stay behind: it goes back now.
        stays.recoverLocal(player).thenAccept(count -> {
            if (count > 0 && !context.stopping()) {
                context.tasks().runAtEntity(player, () -> {
                    strip(player);
                    if (inLobby(player)) giveSword(player);
                });
            }
        });
    }

    void death(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID id = victim.getUniqueId();
        if (!active.contains(id)) return;
        event.deathMessage(null);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = victim.getKiller();
        if (killer == null || !active.contains(killer.getUniqueId())) killer = opponent(id);
        active.remove(id);
        zoned.remove(id);
        dead.add(id);
        cancelCountdown(victim);
        tags.remove(id);
        record(victim, killer, false);
        // No death screen: respawn at once; the respawn hooks send them home and give their things back.
        context.tasks().runAtEntityLater(victim, 2L, () -> {
            if (victim.isDead()) victim.spigot().respawn();
        });
    }

    void zoneEntered(Player player) {
        UUID id = player.getUniqueId();
        if (!inLobby(player) || active.contains(id) || restoring.contains(id) || dead.contains(id) || !allowed(player)) return;
        cancelCountdown(player);
        equip(player, true);
    }

    // ======================== countdowns ========================

    private boolean allowed(Player player) {
        PvpSettings config = settings.get();
        if (!config.permission().isBlank() && !player.hasPermission(config.permission())) return false;
        if (Busy.any(context, player)) {
            return false;
        }
        return true;
    }

    private void countdown(Player player, boolean entering) {
        PvpSettings config = settings.get();
        int seconds = entering ? config.equipSeconds() : config.unequipSeconds();
        if (seconds <= 0) {
            if (entering) equip(player, false);
            else unequip(player);
            return;
        }
        String line = entering ? messages.get().equipBar() : messages.get().unequipBar();
        BossBar bar = BossBar.bossBar(PluginMessages.of(plugin()).text(line, Values.of("seconds", seconds)).build(), 1f,
                entering ? BossBar.Color.GREEN : BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        Countdown running = new Countdown(entering, seconds, bar);
        countdowns.put(player.getUniqueId(), running);
        running.task = context.tasks().runAtEntityTimer(player, 20L, 20L, () -> {
            running.left--;
            if (running.left <= 0) {
                cancelCountdown(player);
                if (entering) equip(player, false);
                else unequip(player);
                return;
            }
            bar.name(PluginMessages.of(plugin()).text(line, Values.of("seconds", running.left)).build());
            bar.progress(Math.max(0f, Math.min(1f, running.left / (float) running.total)));
        });
    }

    private void cancelCountdown(Player player) {
        Countdown running = countdowns.remove(player.getUniqueId());
        if (running == null) return;
        if (running.task != null) running.task.cancel();
        player.hideBossBar(running.bar);
    }

    // ======================== kit on and off ========================

    private void equip(Player player, boolean byZone) {
        UUID id = player.getUniqueId();
        if (!isEnabled() || !inLobby(player) || active.contains(id) || restoring.contains(id)) return;
        if (Busy.any(context, player)) {
            send(player, messages.get().busyElsewhere(), Values.of());
            return;
        }
        HotbarModule hotbar = context.module(HotbarModule.ID, HotbarModule.class).orElse(null);
        if (hotbar != null) hotbar.hide(player);
        var stay = stays.enter(player, "Lobby PvP", () -> release(player));
        if (stay.isEmpty()) {
            if (hotbar != null) hotbar.show(player);
            send(player, messages.get().busyElsewhere(), Values.of());
            return;
        }
        active.add(id);
        if (byZone) zoned.add(id);
        GameMode now = player.getGameMode();
        GameMode mode = now == GameMode.CREATIVE || now == GameMode.SPECTATOR ? GameMode.ADVENTURE : now;
        stay.get().ready()
                .thenCompose(ignored -> stays.dress(player, mode, () -> active.contains(id)))
                .whenComplete((ignored, failure) -> {
                    if (context.stopping()) return;
                    context.tasks().runAtEntity(player, () -> {
                        if (failure != null) {
                            context.debug().error("Could not put the lobby PvP kit on " + player.getName(), failure);
                            unequip(player);
                        } else if (active.contains(id)) {
                            giveKit(player);
                            send(player, messages.get().equipped(), Values.of());
                        }
                    });
                });
    }

    /** Another plugin asks for the player: yes, unless they are in combat. */
    private boolean release(Player player) {
        if (tagged(player.getUniqueId())) return false;
        unequip(player);
        return true;
    }

    private void unequip(Player player) {
        UUID id = player.getUniqueId();
        if (!active.remove(id)) return;
        zoned.remove(id);
        tags.remove(id);
        restoring.add(id);
        cancelCountdown(player);
        send(player, messages.get().unequipped(), Values.of());
        giveBack(player);
    }

    /** Restores the player's own things and the lobby hotbar, once. */
    private void giveBack(Player player) {
        UUID id = player.getUniqueId();
        restoring.add(id);
        stays.leaveHere(player).whenComplete((ignored, failure) -> {
            restoring.remove(id);
            if (failure != null) context.debug().error("Could not give " + player.getName() + " their things back", failure);
            if (context.stopping()) return;
            context.tasks().runAtEntity(player, () -> {
                if (!player.isOnline()) return;
                if (isEnabled() && inLobby(player)) {
                    giveSword(player);
                    context.module(HotbarModule.ID, HotbarModule.class).ifPresent(hotbar -> hotbar.show(player));
                } else {
                    strip(player);
                    context.module(StateModule.ID, StateModule.class).ifPresent(state -> state.reapply(player));
                }
            });
        });
    }

    private void giveKit(Player player) {
        PvpSettings.Kit kit = settings.get().kit();
        PlayerInventory inventory = player.getInventory();
        inventory.setHelmet(piece(player, kit.helmet()));
        inventory.setChestplate(piece(player, kit.chestplate()));
        inventory.setLeggings(piece(player, kit.leggings()));
        inventory.setBoots(piece(player, kit.boots()));
        ItemStack weapon = piece(player, kit.weapon());
        int slot = swordSlot();
        if (weapon != null) Slots.place(plugin(), player, slot, weapon);
        inventory.setHeldItemSlot(slot);
        var max = player.getAttribute(net.exylia.lib.platform.Attributes.MAX_HEALTH);
        player.setHealth(max == null ? 20.0 : max.getValue());
        player.setFoodLevel(20);
        // No lobby flight or effects in a fight. The stay's snapshot holds them and gives them back with the rest.
        player.setFlying(false);
        player.setAllowFlight(false);
        for (var effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
    }

    private @Nullable ItemStack piece(Player player, String source) {
        if (source == null || source.isBlank()) return null;
        ItemStack stack = Items.of(plugin()).render(Item.of(source).build(), player);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(kitMarker, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ======================== the lobby sword ========================

    private void giveSword(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (isSword(item)) return;
        }
        Material material = Material.matchMaterial(settings.get().swordMaterial());
        PvpMessages text = messages.get();
        ItemStack sword = Items.of(plugin()).render(Item.of(material == null ? "WOODEN_SWORD" : material.name())
                .name(text.swordName()).lore(text.swordLore()).build(), player);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
            sword.setItemMeta(meta);
        }
        Slots.place(plugin(), player, swordSlot(), sword);
    }

    /** Takes the sword and any kit piece left over (a crash, a missed restore) out of the player's inventory. */
    private void strip(Player player) {
        sweep(player.getInventory());
        if (isSword(player.getItemOnCursor()) || isKit(player.getItemOnCursor())) player.setItemOnCursor(null);
    }

    @Override
    protected void enter(@NotNull Player player, @NotNull AreaCause cause) {
        strip(player);
        giveSword(player);
        UUID id = player.getUniqueId();
        repo.find(id).thenAccept(found -> cache.putIfAbsent(id, found.orElseGet(() -> PvpStat.empty(id, player.getName()))));
    }

    @Override
    protected void leave(@NotNull Player player, @NotNull AreaCause cause) {
        UUID id = player.getUniqueId();
        cancelCountdown(player);
        if (cause == AreaCause.QUIT && active.contains(id) && settings.get().quitCounts() && tagged(id)) {
            record(player, opponent(id), true);
        }
        tags.remove(id);
        if (cause == AreaCause.QUIT) dead.remove(id);
        boolean wasActive = active.remove(id);
        zoned.remove(id);
        if (wasActive) giveBack(player);
        else strip(player);
        if (cause == AreaCause.QUIT || cause == AreaCause.DISABLE) cache.remove(id);
    }

    // ======================== deaths and stats ========================

    private boolean tagged(UUID id) {
        Tag tag = tags.get(id);
        if (tag == null) return false;
        if (tag.until() > System.currentTimeMillis()) return true;
        tags.remove(id, tag);
        return false;
    }

    private long left(UUID id) {
        Tag tag = tags.get(id);
        return tag == null ? 0 : Math.max(1, (tag.until() - System.currentTimeMillis() + 999) / 1000);
    }

    private @Nullable Player opponent(UUID id) {
        Tag tag = tags.get(id);
        Player other = tag == null ? null : Bukkit.getPlayer(tag.opponent());
        return other != null && active.contains(other.getUniqueId()) ? other : null;
    }

    /** Counts a death (and the kill, when someone caused it) and tells the two of them, nobody else. */
    private void record(Player victim, @Nullable Player killer, boolean quit) {
        PvpMessages text = messages.get();
        UUID victimId = victim.getUniqueId();
        String victimName = victim.getName();
        change(victimId, victimName, stat -> stat.died(victimName), ignored -> {
        });
        if (!quit) send(victim, text.victim(), Values.of("killer", killer == null ? "?" : killer.getName()));
        if (killer == null) return;
        UUID killerId = killer.getUniqueId();
        tags.remove(killerId);
        if (repeated(killerId, victimId)) {
            // The same victim again within the window: no kill, no streak, no reward, so two accounts farm nothing.
            send(killer, quit ? text.quitKiller() : text.killer(), Values.of("victim", victimName).put("streak", stat(killerId).streak()));
            return;
        }
        change(killerId, killer.getName(), stat -> stat.killed(killer.getName()), stat -> context.tasks().runAtEntity(killer, () -> {
            Values values = Values.of("victim", victimName).put("streak", stat.streak());
            send(killer, quit ? text.quitKiller() : text.killer(), values);
            // A quit is not a fight won: it counts, but it pays nothing.
            if (!quit) reward(killer, stat.streak());
        }));
    }

    /** Whether this killer already killed this victim within the repeat window; remembers this kill either way. */
    private boolean repeated(UUID killer, UUID victim) {
        long now = System.currentTimeMillis();
        long window = settings.get().repeatKillWindow().toMillis();
        lastKills.values().removeIf(at -> now - at >= window);
        Long before = lastKills.put(killer + ":" + victim, now);
        return before != null && now - before < window;
    }

    private void change(UUID id, String name, UnaryOperator<PvpStat> edit, java.util.function.Consumer<PvpStat> done) {
        PvpStat known = cache.get(id);
        if (known != null) {
            apply(id, name, edit, done);
            return;
        }
        repo.find(id).thenAccept(found -> {
            cache.putIfAbsent(id, found.orElseGet(() -> PvpStat.empty(id, name)));
            apply(id, name, edit, done);
        });
    }

    private void apply(UUID id, String name, UnaryOperator<PvpStat> edit, java.util.function.Consumer<PvpStat> done) {
        PvpStat next = cache.compute(id, (key, old) -> edit.apply((old == null ? PvpStat.empty(id, name) : old).withName(name)));
        repo.save(next);
        done.accept(next);
    }

    private void reward(Player killer, int streak) {
        List<String> lines = settings.get().streakRewards().get(String.valueOf(streak));
        if (lines == null || lines.isEmpty()) return;
        List<RewardEntry> owed = new ArrayList<>();
        for (String line : lines) {
            if (line.regionMatches(true, 0, "item:", 0, 5)) {
                String[] parts = line.substring(5).split(":");
                int amount = 1;
                if (parts.length > 1) {
                    try {
                        amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                    } catch (NumberFormatException ignored) {
                        // keeps one
                    }
                }
                owed.add(RewardEntry.item(parts[0].trim()).fixedAmount(amount).build());
            } else {
                owed.add(RewardEntry.command(line).build());
            }
        }
        Rewards.of(plugin()).deliver(killer.getUniqueId(), owed);
        send(killer, messages.get().streakReward(), Values.of("streak", streak));
    }

    private PvpStat stat(UUID id) {
        PvpStat found = cache.get(id);
        return found != null ? found : PvpStat.empty(id, "");
    }

    // ======================== respawn ========================

    private @Nullable Location respawnAt(Player player) {
        PvpSettings.Point point = settings.get().respawn();
        World world = point.set() ? Bukkit.getWorld(point.world()) : null;
        if (world != null) return new Location(world, point.x(), point.y(), point.z(), (float) point.yaw(), (float) point.pitch());
        return context.module(SpawnModule.ID, SpawnModule.class).flatMap(spawn -> spawn.spawn(player.getWorld())).orElse(null);
    }

    private void respawned(Player player) {
        UUID id = player.getUniqueId();
        if (!dead.remove(id)) return;
        giveBack(player);
    }

    // ======================== tops ========================

    private void refreshTop() {
        if (repo == null) return;
        repo.all().orderByDescending("kills").limit(TOP_MAX).find().thenAccept(rows -> {
            top = rows.stream().filter(row -> row.kills() > 0).toList();
        });
    }

    private Object topValue(int rank, String what) {
        List<PvpStat> rows = top;
        if (rank < 1 || rank > rows.size()) return messages.get().topEmpty();
        PvpStat row = rows.get(rank - 1);
        return what.equalsIgnoreCase("value") ? (Object) row.kills() : row.name();
    }

    private int rankOf(UUID id) {
        List<PvpStat> rows = top;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).player().equals(id)) return i + 1;
        }
        return 0;
    }

    private List<UiEntry> topRows() {
        List<UiEntry> rows = new ArrayList<>();
        int rank = 1;
        for (PvpStat row : top) {
            rows.add(UiEntry.of(row.player().toString())
                    .with("row_rank", rank++)
                    .with("row_name", row.name())
                    .with("row_kills", row.kills())
                    .with("row_deaths", row.deaths())
                    .with("row_best", row.bestStreak())
                    .with("row_ratio", String.format(Locale.ROOT, "%.2f", row.kills() / (double) Math.max(1, row.deaths())))
                    .build());
        }
        return rows;
    }

    private void loadMenu() {
        context.hub().menu(TOP_MENU);
    }

    private void buildBoard() {
        context.registered(HologramsModule.ID, HologramsModule.class).ifPresent(holograms -> {
            holograms.remove(BOARD);
            PvpSettings.Point point = settings.get().board();
            World world = point.set() ? Bukkit.getWorld(point.world()) : null;
            if (world == null) return;
            holograms.place(BOARD, new Location(world, point.x(), point.y(), point.z()), this::boardLines);
        });
    }

    private List<String> boardLines() {
        PvpMessages text = messages.get();
        List<String> lines = new ArrayList<>();
        lines.add(text.boardTitle());
        List<PvpStat> rows = top;
        if (rows.isEmpty()) lines.add(text.boardEmpty());
        for (int i = 0; i < Math.min(rows.size(), Math.max(1, settings.get().boardRows())); i++) {
            lines.add(text.boardRow().replace("%rank%", String.valueOf(i + 1)).replace("%name%", rows.get(i).name())
                    .replace("%kills%", String.valueOf(rows.get(i).kills())));
        }
        return lines;
    }

    // ======================== zones ========================

    private void buildZones() {
        List<RegionSnapshot> boxes = new ArrayList<>();
        for (Map.Entry<String, PvpSettings.Zone> entry : settings.get().zones().entrySet()) {
            PvpSettings.Zone zone = entry.getValue();
            World world = Bukkit.getWorld(zone.world());
            if (world == null) continue;
            boxes.add(regions.region("zone-" + entry.getKey(), WorldIdentity.from(world),
                    Cuboid.blocks(Math.min(zone.x1(), zone.x2()), Math.min(zone.y1(), zone.y2()), Math.min(zone.z1(), zone.z2()),
                            Math.max(zone.x1(), zone.x2()), Math.max(zone.y1(), zone.y2()), Math.max(zone.z1(), zone.z2())),
                    0, PolicySet.empty()));
        }
        regions.replaceAll(boxes);
    }

    /** Walking out of a zone takes the kit off, once the combat tag allows it. */
    private void checkZones() {
        for (UUID id : zoned) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                zoned.remove(id);
                continue;
            }
            context.tasks().runAtEntity(player, () -> {
                if (zoned.contains(id) && regions.at(player.getLocation()).isEmpty() && !tagged(id)) unequip(player);
            });
        }
    }

    // ======================== admin ========================

    void setRespawn(Player admin, Runnable back) {
        admin.closeInventory();
        setup(admin, back).spawn(messages.get().wizardRespawn(), at -> {
            settings.update(current -> new PvpSettings(current.topCommand(), current.permission(), current.equipSeconds(),
                    current.unequipSeconds(), current.tagSeconds(), current.swordSlot(), current.swordMaterial(), current.quitCounts(),
                    current.kit(), point(at), current.topRefreshSeconds(), current.boardRows(), current.board(),
                    current.streakRewards(), current.zones(), current.repeatKillWindow()));
            send(admin, messages.get().spawnSet(), Values.of());
            if (back != null) back.run();
        });
    }

    void setBoard(Player admin, Runnable back) {
        admin.closeInventory();
        setup(admin, back).block(messages.get().wizardBoard(), block -> {
            Location at = block.clone().add(0.5, 2, 0.5);
            settings.update(current -> new PvpSettings(current.topCommand(), current.permission(), current.equipSeconds(),
                    current.unequipSeconds(), current.tagSeconds(), current.swordSlot(), current.swordMaterial(), current.quitCounts(),
                    current.kit(), current.respawn(), current.topRefreshSeconds(), current.boardRows(), point(at),
                    current.streakRewards(), current.zones(), current.repeatKillWindow()));
            buildBoard();
            send(admin, messages.get().boardSet(), Values.of());
            if (back != null) back.run();
        });
    }

    private static PvpSettings.Point point(Location at) {
        return new PvpSettings.Point(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
    }

    @Override
    public boolean hasAdmin() {
        return true;
    }

    @Override
    public void openAdmin(@NotNull Player viewer, @NotNull Runnable back) {
        this.back = back;
        PvpMessages text = messages.get();
        screens.open(viewer, back, "Modules", text.listTitle(), text.listKind(), List.of(
                new AdminScreens.Add("IRON_SWORD", text.addZoneTitle(), text.addZoneText(), text.addZoneClick(), text.addZoneNext(),
                        this::addZone),
                new AdminScreens.Add("RED_BED", text.addRespawnTitle(), text.addRespawnText(), text.addRespawnClick(),
                        text.addRespawnNext(), admin -> setRespawn(admin, () -> openAdmin(admin, back)))),
                this::rows);
    }

    private List<UiEntry> rows() {
        PvpMessages text = messages.get();
        List<UiEntry> rows = new ArrayList<>();
        for (Map.Entry<String, PvpSettings.Zone> entry : settings.get().zones().entrySet()) {
            PvpSettings.Zone zone = entry.getValue();
            rows.add(AdminScreens.row(entry.getKey(), "IRON_SWORD", entry.getKey(), text.subtitleZone(), List.of(
                    AdminScreens.stat("map", text.labelPlace(), "{info}" + zone.world() + " " + zone.x1() + " " + zone.y1() + " " + zone.z1()),
                    AdminScreens.stat("hopper", text.labelSize(), "{info}" + (Math.abs(zone.x2() - zone.x1()) + 1) + "x"
                            + (Math.abs(zone.y2() - zone.y1()) + 1) + "x" + (Math.abs(zone.z2() - zone.z1()) + 1))),
                    text.clicksZone(), false));
        }
        return rows;
    }

    private void click(Player viewer, String click, String id) {
        if (!settings.get().zones().containsKey(id)) return;
        PvpMessages text = messages.get();
        switch (click) {
            case "left", "right" -> {
                viewer.closeInventory();
                regions.visualize(viewer, "zone-" + id);
            }
            case "shift" -> screens.confirm(viewer, Values.of("what", text.subtitleZone() + " " + id).apply(text.confirmRemove()), () -> {
                saveZones(map -> map.remove(id));
                send(viewer, text.removed(), Values.of("what", text.subtitleZone() + " " + id));
            }, () -> openAdmin(viewer, back));
            default -> {
            }
        }
    }

    private void addZone(Player admin) {
        admin.closeInventory();
        Runnable reopen = () -> openAdmin(admin, back);
        setup(admin, reopen).area(messages.get().wizardZone(), result -> {
            var a = result.first();
            var b = result.second();
            put(admin, new PvpSettings.Zone(result.world().fallbackName(), a.x(), a.y(), a.z(), b.x(), b.y(), b.z()));
            reopen.run();
        });
    }

    private void put(Player admin, PvpSettings.Zone zone) {
        Map<String, PvpSettings.Zone> all = settings.get().zones();
        int number = all.size() + 1;
        while (all.containsKey("zone-" + number)) number++;
        String key = "zone-" + number;
        saveZones(map -> map.put(key, zone));
        send(admin, messages.get().added(), Values.of("what", messages.get().subtitleZone() + " " + key));
    }

    private void saveZones(java.util.function.Consumer<Map<String, PvpSettings.Zone>> change) {
        settings.update(current -> {
            Map<String, PvpSettings.Zone> map = new LinkedHashMap<>(current.zones());
            change.accept(map);
            return new PvpSettings(current.topCommand(), current.permission(), current.equipSeconds(), current.unequipSeconds(),
                    current.tagSeconds(), current.swordSlot(), current.swordMaterial(), current.quitCounts(), current.kit(),
                    current.respawn(), current.topRefreshSeconds(), current.boardRows(), current.board(), current.streakRewards(), map,
                    current.repeatKillWindow());
        });
        buildZones();
    }

    private void send(Player to, String line, Values values) {
        PluginMessages.of(plugin()).send(to, line, values);
    }
}
