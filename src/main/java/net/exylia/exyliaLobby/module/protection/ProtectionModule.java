package net.exylia.exyliaLobby.module.protection;

import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.effect.Display;
import net.exylia.lib.effect.Effects;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

/**
 * Keeps the lobby as it is: no building, breaking, damage, drops, hunger, mobs, fire or explosions inside it.
 *
 * <p>Other modules use {@link #allowDamage} (the pvp module lets duels through) and {@link #bypass}.
 */
public final class ProtectionModule extends LobbyModule {

    public static final String ID = "protection";

    private ConfigFile<ProtectionSettings> settings;
    private ConfigFile<ProtectionMessages> messages;
    private final Set<UUID> building = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Display> bars = new ConcurrentHashMap<>();
    // ponytail: kept in memory only, so a crash mid-build keeps the build items; persist it if that ever matters
    private final Map<UUID, ItemStack[]> inventories = new ConcurrentHashMap<>();
    private final List<BiPredicate<Player, Player>> pvp = new CopyOnWriteArrayList<>();
    private volatile List<String> causeNames = List.of();
    private volatile Set<DamageCause> causes = Set.of();

    public ProtectionModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.SHIELD;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(ProtectionSettings.class);
        messages = messages(ProtectionMessages.class);
    }

    @Override
    protected void start() {
        listen(new ProtectionListener(this));
    }

    @Override
    protected void stop() {
        for (UUID id : building) {
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player == null) continue;
            if (context.stopping()) giveBack(player);
            else context.tasks().runAtEntity(player, () -> {
                giveBack(player);
                context.areas().pause(player, false);
            });
        }
        building.clear();
        bars.values().forEach(Display::stop);
        bars.clear();
    }

    // ======================== public hooks ========================

    /**
     * Lets a hit between two players through the protection, for a lobby PvP module.
     *
     * <p>Called as {@code (attacker, victim)} on the thread of the damage event, for hits inside the lobby only;
     * any registered predicate answering {@code true} lets that hit land. Keep it cheap. Kept while this module is
     * off, so register through {@code context.registered} to survive it being switched on later.
     *
     * @return a handle that removes the predicate again
     */
    public @NotNull Runnable allowDamage(@NotNull BiPredicate<Player, Player> predicate) {
        pvp.add(predicate);
        return () -> pvp.remove(predicate);
    }

    /** Whether the player is an admin with build mode on, so none of the protection rules apply to them. */
    public boolean bypass(@NotNull Player player) {
        return building.contains(player.getUniqueId()) && player.hasPermission(Permissions.BUILD);
    }

    // ======================== build mode ========================

    void toggleBuild(Player player) {
        ProtectionMessages text = messages.get();
        if (building.remove(player.getUniqueId())) {
            hideBar(player);
            giveBack(player);
            context.areas().pause(player, false);
            send(player, text.buildOff());
            return;
        }
        if (!inLobby(player)) {
            send(player, text.notInLobby());
            return;
        }
        building.add(player.getUniqueId());
        context.areas().pause(player, true);
        // what they build with is theirs only while building: the lobby inventory comes back untouched
        inventories.put(player.getUniqueId(), java.util.Arrays.stream(player.getInventory().getContents())
                .map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new));
        player.getInventory().clear();
        hideBar(player);
        bars.put(player.getUniqueId(), Effects.of(plugin()).bossBar(text.buildBar()).colour("YELLOW").progress(1f).show(player));
        send(player, text.buildOn());
    }

    private void hideBar(Player player) {
        Display bar = bars.remove(player.getUniqueId());
        if (bar != null) bar.stop();
    }

    @Override
    protected void leave(@NotNull Player player, @NotNull AreaCause cause) {
        if (cause == AreaCause.BUILD) return;
        if (building.remove(player.getUniqueId())) {
            hideBar(player);
            giveBack(player);
        }
    }

    /** Puts back the inventory the player had before build mode, dropping whatever they built with. */
    private void giveBack(Player player) {
        ItemStack[] before = inventories.remove(player.getUniqueId());
        if (before == null) return;
        player.getInventory().setContents(before);
        player.setItemOnCursor(null);
    }

    @Override
    protected List<Object> commands() {
        return adminCommand(new BuildCommand(this));
    }

    private void send(Player player, String line) {
        PluginMessages.of(plugin()).send(player, line, Values.of());
    }

    // ======================== for the listener ========================

    ProtectionSettings rules() {
        return settings.get();
    }

    LobbyContext lobby() {
        return context;
    }

    boolean pvpAllowed(Player attacker, Player victim) {
        for (BiPredicate<Player, Player> allow : pvp) {
            if (allow.test(attacker, victim)) return true;
        }
        return false;
    }

    /** The configured damage causes, parsed once per change of the list. */
    Set<DamageCause> cancelledCauses() {
        List<String> names = settings.get().cancelledDamage();
        if (names != causeNames) {
            Set<DamageCause> parsed = EnumSet.noneOf(DamageCause.class);
            for (String name : names) {
                try {
                    parsed.add(DamageCause.valueOf(name.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException unknown) {
                    context.debug().warn("Protection: unknown damage cause '" + name + "'");
                }
            }
            causes = parsed;
            causeNames = names;
        }
        return causes;
    }
}
