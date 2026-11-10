package net.exylia.exyliaLobby.module.protection;

import net.exylia.exyliaLobby.Permissions;
import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
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
        building.clear();
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
            send(player, text.buildOff());
            return;
        }
        if (!inLobby(player)) {
            send(player, text.notInLobby());
            return;
        }
        building.add(player.getUniqueId());
        BossBar bar = BossBar.bossBar(PluginMessages.of(plugin()).text(text.buildBar(), Values.of()).build(), 1f,
                BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        bars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
        send(player, text.buildOn());
    }

    private void hideBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    @Override
    protected void leave(@NotNull Player player, @NotNull AreaCause cause) {
        if (building.remove(player.getUniqueId())) hideBar(player);
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
