package net.exylia.exyliaLobby.module.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.exylia.exyliaLobby.area.AreaCause;
import net.exylia.exyliaLobby.module.LobbyContext;
import net.exylia.exyliaLobby.module.LobbyModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Values;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.time.Duration;
import java.util.UUID;
import net.exylia.lib.util.CooldownScope;

/** Chat lock, slow mode and clear chat, for players inside the lobby only. Off by default. */
public final class ChatModule extends LobbyModule implements Listener {

    public static final String ID = "chat";

    private ConfigFile<ChatSettings> settings;
    private ConfigFile<ChatMessages> messages;
    private volatile boolean locked;
    private volatile int slow;
    private static final String SLOW = "chat_slow";

    public ChatModule(@NotNull LobbyContext context) {
        super(ID, context);
    }

    @Override
    public @NotNull Material icon() {
        return Material.WRITABLE_BOOK;
    }

    @Override
    protected void registerConfigs() {
        settings = settings(ChatSettings.class);
        messages = messages(ChatMessages.class);
    }

    @Override
    protected void start() {
        locked = settings.get().lockedAtStart();
        slow = settings.get().slowModeSeconds();
        listen(this);
    }

    @Override
    protected void stop() {
    }

    @Override
    protected List<Object> commands() {
        return adminCommand(new ChatCommand(this));
    }

    @Override
    protected void leave(@NotNull Player player, @NotNull AreaCause cause) {
        cooldowns().clear(player, SLOW);
    }

    // ======================== chat ========================

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!context.areas().contains(player) || player.hasPermission(settings.get().bypassPermission())) return;
        if (locked) {
            event.setCancelled(true);
            tell(player, messages.get().locked(), Values.of());
            return;
        }
        int delay = slow;
        if (delay <= 0) return;
        if (!cooldowns().tryStart(player, SLOW, Duration.ofSeconds(delay))) {
            event.setCancelled(true);
            if (refusalDue(player, SLOW)) {
                tell(player, messages.get().slowDenied(), withTime(Values.of(), cooldowns().remainingSeconds(player, SLOW)));
            }
        }
    }

    // ======================== staff ========================

    void toggleLock(CommandSender staff) {
        locked = !locked;
        announce(locked ? messages.get().lockOn() : messages.get().lockOff(), Values.of("player", staff.getName()));
    }

    void slow(CommandSender staff, int seconds) {
        slow = Math.max(0, seconds);
        // The new delay counts from each player's next message.
        for (UUID id : context.areas().players()) cooldowns().clear(CooldownScope.player(id), SLOW);
        announce(slow > 0 ? messages.get().slowOn() : messages.get().slowOff(), Values.of("seconds", slow));
    }

    void clear(CommandSender staff) {
        int lines = Math.max(1, Math.min(500, settings.get().clearLines()));
        String bypass = settings.get().bypassPermission();
        for (UUID id : context.areas().players()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            context.tasks().runAtEntity(player, () -> {
                if (!player.hasPermission(bypass)) {
                    for (int i = 0; i < lines; i++) player.sendMessage(Component.empty());
                }
                PluginMessages.of(plugin()).send(player, messages.get().cleared(), Values.of("player", staff.getName()));
            });
        }
    }

    /** One line to everyone in the lobby, each on their own thread. */
    private void announce(String line, Values values) {
        for (UUID id : context.areas().players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) tell(player, line, values);
        }
    }

    private void tell(Player player, String line, Values values) {
        context.tasks().runAtEntity(player, () -> PluginMessages.of(plugin()).send(player, line, values));
    }
}
