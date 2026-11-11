package net.exylia.exyliaLobby.module.hud;

import net.exylia.lib.config.Comment;

import java.time.Duration;
import java.util.List;

/**
 * {@code modules/hud/config.yml}: the player list and the announcements inside the lobby. Every part switches on its own.
 * Sidebars and boss bars are ExyliaScreens' {@code scoreboards.yml} and {@code bossbars.yml}.
 */
public record HudSettings(
        @Comment("The header and footer of the player list (Tab).")
        Tab tab,

        @Comment("Chat announcements sent on a timer. Players can mute them in /settings → Announcements.")
        Announcements announcements) {

    public HudSettings() {
        this(new Tab(), new Announcements());
    }

    public record Tab(
            @Comment("Show a header and footer on the player list in the lobby.")
            boolean enabled,
            @Comment("Header lines.")
            List<String> header,
            @Comment("Footer lines.")
            List<String> footer,
            @Comment("How often placeholders in them are read again.")
            Duration interval) {

        public Tab() {
            this(true, List.of("", "{primary}&lLOBBY", "{letters_black}{server-ip}", ""),
                    List.of("", "{letters}Players on the network{letters_black}: {success}%network_online%", ""),
                    Duration.ofSeconds(5));
        }
    }

    public record Announcements(
            @Comment("Send the announcements.")
            boolean enabled,
            @Comment("The lines, sent one after another. %prefix% is this plugin's prefix.")
            List<String> messages,
            @Comment("Time between two announcements.")
            Duration interval) {

        public Announcements() {
            this(true, List.of("%prefix% {letters}Mute these announcements any time in {highlight}/settings{letters}."),
                    Duration.ofMinutes(5));
        }
    }
}
