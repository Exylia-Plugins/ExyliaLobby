package net.exylia.exyliaLobby.module.hud;

import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.effect.Display;
import net.exylia.lib.effect.Effects;
import net.exylia.lib.placeholder.Placeholders;
import net.exylia.lib.proxy.Proxy;
import net.exylia.lib.scoreboard.Board;
import net.exylia.lib.scoreboard.SidebarConfig;
import net.exylia.lib.scoreboard.Scoreboards;
import net.exylia.lib.settings.Broadcasts;
import net.exylia.lib.settings.Settings;
import net.exylia.lib.task.TaskHandle;
import net.exylia.lib.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sidebar, player-list header and footer, rotating boss bars and timed announcements, shown only inside the lobby:
 * {@link #enter} draws them and {@link #leave} takes every one off. Each part has its own switch in the settings.
 */
public final class HudModule extends LobbyModule {

    public static final String ID = "hud";
    private static final String CHANNEL = "lobby-announcements";

    private ConfigFile<HudSettings> settings;
    private ConfigFile<HudMessages> messages;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private Broadcasts.Channel channel;
    private TaskHandle announcer;
    private final AtomicInteger announced = new AtomicInteger();

    /** What one player has on screen now. */
    private static final class Session {
        Board board;
        Display bar;
        TaskHandle ticker;
        /** Whether this session drew the player-list header, so turning the tab off by a reload still clears it. */
        boolean tab;
        int seconds;
        int text;
    }

    public HudModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.OAK_SIGN;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(HudSettings.class);
        messages = messages(HudMessages.class);
    }

    @Override
    protected void start() {
        Placeholders.group(plugin(), "lobby")
                .async()
                .add("hud_here", request -> context.areas().players().size())
                .add("hud_network", request -> Math.max(Proxy.players().size(), Bukkit.getOnlinePlayers().size()))
                .register();
        HudMessages text = messages.get();
        channel = Broadcasts.channel(plugin(), CHANNEL, "BELL", text.channelName(), text.channelDescription(), null, null);
        announce();
        settings.onReload(reloaded -> {
            if (!isEnabled()) return;
            announce();
            // Every part may have changed: draw them again for everybody inside.
            for (UUID id : context.areas().players()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) continue;
                context.tasks().runAtEntity(player, () -> {
                    leave(player, AreaCause.CHANGE);
                    if (inLobby(player)) enter(player, AreaCause.CHANGE);
                });
            }
        });
    }

    @Override
    protected void stop() {
        if (announcer != null) announcer.cancel();
        announcer = null;
        Settings.unregister(plugin(), CHANNEL);
        sessions.clear();
    }

    // ======================== lobby crossings ========================

    @Override
    protected void enter(@NotNull Player player, @NotNull AreaCause cause) {
        HudSettings config = settings.get();
        Session session = new Session();
        if (config.sidebar().enabled()) {
            SidebarConfig board = config.sidebar().board();
            session.board = Scoreboards.show(plugin(), player, new SidebarConfig(true, board.title(), board.lines(), board.update()));
        }
        Session old = sessions.put(player.getUniqueId(), session);
        if (old != null) clear(player, old);
        session.tab = config.tab().enabled();
        tab(player, config.tab());
        bar(player, session, config.bossBars());
        if (config.tab().enabled() || config.bossBars().enabled()) {
            session.ticker = context.tasks().runAtEntityTimer(player, 20L, 20L, () -> tick(player, session));
        }
    }

    @Override
    protected void leave(@NotNull Player player, @NotNull AreaCause cause) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) clear(player, session);
    }

    private void clear(Player player, Session session) {
        if (session.ticker != null) session.ticker.cancel();
        if (session.board != null) session.board.stop();
        if (session.bar != null) session.bar.stop();
        if (session.tab && player.isOnline()) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

    private void tick(Player player, Session session) {
        HudSettings config = settings.get();
        session.seconds++;
        if (config.tab().enabled() && session.seconds % seconds(config.tab().interval().toMillis()) == 0) tab(player, config.tab());
        rotate(player, session, config.bossBars());
    }

    private static int seconds(long millis) {
        return (int) Math.max(1, millis / 1000);
    }

    // ======================== parts ========================

    private void tab(Player player, HudSettings.Tab tab) {
        if (!tab.enabled()) return;
        player.sendPlayerListHeaderAndFooter(lines(player, tab.header()), lines(player, tab.footer()));
    }

    private static Component lines(Player player, List<String> lines) {
        List<Component> parts = new ArrayList<>();
        for (String line : lines) parts.add(Text.of(line).forPlayer(player).build());
        return Component.join(JoinConfiguration.newlines(), parts);
    }

    private void bar(Player player, Session session, HudSettings.BossBars config) {
        if (!config.enabled() || config.texts().isEmpty()) return;
        session.bar = Effects.of(plugin()).bossBar(config.texts().get(0)).colour(config.colour()).overlay(config.overlay())
                .progress(1f).show(player);
    }

    /** Moves a player's boss bar on to the next text when its time is up. */
    private void rotate(Player player, Session session, HudSettings.BossBars config) {
        if (!config.enabled() || config.texts().size() < 2 || session.bar == null) return;
        if (session.seconds % seconds(config.interval().toMillis()) != 0) return;
        session.text = (session.text + 1) % config.texts().size();
        session.bar.text(config.texts().get(session.text));
    }

    private void announce() {
        if (announcer != null) announcer.cancel();
        announcer = null;
        HudSettings.Announcements config = settings.get().announcements();
        if (!config.enabled() || config.messages().isEmpty()) return;
        long period = Math.max(20L, config.interval().toMillis() / 50);
        announcer = context.tasks().runTimer(period, period, () -> {
            List<String> lines = settings.get().announcements().messages();
            if (lines.isEmpty()) return;
            Text text = Text.from(plugin(), lines.get(Math.floorMod(announced.getAndIncrement(), lines.size())));
            for (UUID id : context.areas().players()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && Broadcasts.enabled(player, channel)) context.tasks().runAtEntity(player, () -> text.send(player));
            }
        });
    }
}
