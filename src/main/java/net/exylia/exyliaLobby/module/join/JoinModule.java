package net.exylia.exyliaLobby.module.join;

import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.effect.Effects;
import net.exylia.lib.interaction.Click;
import net.exylia.lib.interaction.Interaction;
import net.exylia.lib.interaction.Interactions;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Join, leave and first-join messages by permission group, silent joins for vanished staff, the welcome title,
 * MOTD and the commands that run on join, first join and quit.
 */
public final class JoinModule extends LobbyModule implements Listener {

    public static final String ID = "join";

    private ConfigFile<JoinSettings> settings;
    private ConfigFile<JoinMessages> messages;
    private volatile Interaction onJoin = Interaction.empty();
    private volatile Interaction onFirstJoin = Interaction.empty();
    private volatile Interaction onQuit = Interaction.empty();

    public JoinModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.OAK_DOOR;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(JoinSettings.class);
        messages = messages(JoinMessages.class);
    }

    @Override
    protected void start() {
        compile();
        listen(this);
    }

    @Override
    protected void onReload() {
        compile();
    }

    private void compile() {
        JoinSettings.Commands commands = settings.get().commands();
        onJoin = Interactions.compile(plugin(), commands.onJoin(), "join commands");
        onFirstJoin = Interactions.compile(plugin(), commands.onFirstJoin(), "first-join commands");
        onQuit = Interactions.compile(plugin(), commands.onQuit(), "quit commands");
    }

    // ======================== messages ========================

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        JoinSettings now = settings.get();
        if (now.onlyInLobby() && !context.areas().contains(player.getLocation())) return;
        Optional<JoinSettings.Group> group = group(player);
        boolean first = !player.hasPlayedBefore();
        String text = group.map(JoinSettings.Group::join).orElse(messages.get().join());
        if (first && !messages.get().firstJoin().isBlank()) text = messages.get().firstJoin();
        event.joinMessage(line(event.joinMessage(), text, group.isPresent(), silent(player), player));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuitEvent(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        JoinSettings now = settings.get();
        if (now.onlyInLobby() && !context.areas().contains(player.getLocation())) return;
        Optional<JoinSettings.Group> group = group(player);
        String text = group.map(JoinSettings.Group::leave).orElse(messages.get().leave());
        event.quitMessage(line(event.quitMessage(), text, group.isPresent(), silent(player), player));
    }

    /** What to broadcast: the configured line, nothing, or the vanilla one left alone. */
    private Component line(Component vanilla, String text, boolean matched, boolean silent, Player player) {
        if (silent) return null;
        if (!text.isBlank()) {
            return PluginMessages.of(plugin()).text(text, Values.of("player", player.getName())).forPlayer(player).build();
        }
        return matched || settings.get().hideVanillaMessages() ? null : vanilla;
    }

    private Optional<JoinSettings.Group> group(Player player) {
        JoinSettings.Group best = null;
        for (JoinSettings.Group group : settings.get().groups()) {
            boolean applies = group.permission().isBlank() || player.hasPermission(group.permission());
            if (applies && (best == null || group.priority() > best.priority())) best = group;
        }
        return Optional.ofNullable(best);
    }

    /** Vanished staff (SuperVanish, PremiumVanish and the like set the 'vanished' metadata) and holders of the silent permission. */
    private boolean silent(Player player) {
        String permission = settings.get().silentPermission();
        if (!permission.isBlank() && player.hasPermission(permission)) return true;
        return player.getMetadata("vanished").stream().anyMatch(value -> value.asBoolean());
    }

    // ======================== what the player sees and runs ========================

    @Override
    protected void enter(@NotNull Player player, @NotNull AreaCause cause) {
        if (cause != AreaCause.JOIN) return;
        boolean first = !player.hasPlayedBefore();
        boolean silent = silent(player);
        if (!silent) {
            onJoin.run(player, Click.LEFT, Map.of());
            if (first) onFirstJoin.run(player, Click.LEFT, Map.of());
        }
        long ticks = Math.max(1L, settings.get().welcomeDelay().toMillis() / 50L);
        context.tasks().runAtEntityLater(player, ticks, () -> {
            JoinMessages text = messages.get();
            Effects.of(plugin()).play(text.welcome().filled(Values.of("player", player.getName())), player);
            List<String> motd = text.motd();
            if (!motd.isEmpty()) PluginMessages.of(plugin()).send(player, motd, Values.of("player", player.getName()));
        });
    }

    @Override
    protected void leave(@NotNull Player player, @NotNull AreaCause cause) {
        if (cause == AreaCause.QUIT && !silent(player)) onQuit.run(player, Click.LEFT, Map.of());
    }
}
