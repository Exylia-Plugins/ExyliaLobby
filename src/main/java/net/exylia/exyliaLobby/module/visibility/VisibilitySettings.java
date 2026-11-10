package net.exylia.exyliaLobby.module.visibility;

import net.exylia.lib.config.Comment;

import java.time.Duration;

/** {@code modules/visibility/config.yml}: who a player sees in the lobby. */
public record VisibilitySettings(
        @Comment("What a player who never chose sees: EVERYONE, FRIENDS or NOBODY.")
        Mode defaultMode,

        @Comment("Time a player must wait between two switches from the hotbar button.")
        Duration cooldown,

        @Comment("Players with this permission are seen by everybody, whatever they chose.")
        String staffPermission,

        @Comment("Count clanmates as friends (needs a clan plugin ExyliaLib supports). Without it, friends are only staff.")
        boolean clanFriends) {

    public VisibilitySettings() {
        this(Mode.EVERYONE, Duration.ofSeconds(3), "exylialobby.visibility.staff", true);
    }

    /** What a player chose to see. */
    public enum Mode {
        EVERYONE, FRIENDS, NOBODY;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Mode of(String key, Mode fallback) {
            for (Mode mode : values()) if (mode.key().equalsIgnoreCase(key)) return mode;
            return fallback;
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
}
