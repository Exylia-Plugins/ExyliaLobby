package net.exylia.exyliaLobby.module.join;

import net.exylia.lib.config.Comment;

import java.time.Duration;
import java.util.List;

/** {@code modules/join/config.yml}: who gets which message, and what runs when they join or quit. */
public record JoinSettings(
        @Comment("Only handle players who join or quit while standing in the lobby. Off handles every join.")
        boolean onlyInLobby,

        @Comment("When no message applies to a player, hide the vanilla 'joined the game' line instead of showing it.")
        boolean hideVanillaMessages,

        @Comment("Players with this permission, and vanished staff, join and leave without a message or commands.")
        String silentPermission,

        @Comment("How long after joining the welcome title, sound and MOTD appear, so the loading screen is gone.")
        Duration welcomeDelay,

        @Comment("Join and leave messages by permission. The entry with the highest priority whose permission the")
        @Comment("player has wins; an empty permission matches everyone. An empty text means no message at all.")
        @Comment("Players matching no entry get the default lines of messages.yml. Placeholders: %player% and any PlaceholderAPI one.")
        List<Group> groups,

        @Comment("Commands run for the player. One per line: 'console: say hi', 'player: spawn', '[SERVER] lobby'")
        @Comment("or '[ACTION] ...'. %player_name% works in any of them.")
        Commands commands) {

    public JoinSettings() {
        this(true, true, "exylialobby.join.silent", Duration.ofMillis(800),
                List.of(new Group("exylialobby.join.vip", 10, "{highlight}&l★ {letters}VIP {highlight}%player%{letters} joined the lobby", "")),
                new Commands());
    }

    /** A message for everyone with a permission. */
    public record Group(String permission, int priority, String join, String leave) {
        public Group() {
            this("", 0, "", "");
        }
    }

    /** What runs on join, on first join, and on quit. */
    public record Commands(List<String> onJoin, List<String> onFirstJoin, List<String> onQuit) {
        public Commands() {
            this(List.of(), List.of(), List.of());
        }
    }
}
