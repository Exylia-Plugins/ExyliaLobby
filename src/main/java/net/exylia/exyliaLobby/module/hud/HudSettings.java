package net.exylia.exyliaLobby.module.hud;

import net.exylia.lib.config.Comment;
import net.exylia.lib.scoreboard.SidebarConfig;

import java.time.Duration;
import java.util.List;

/** {@code modules/hud/config.yml}: what players see on screen inside the lobby. Every part switches on its own. */
public record HudSettings(
        @Comment("The sidebar. %lobby_hud_here% (players in this lobby) and %lobby_hud_network% (players on the network) are ours;")
        @Comment("every other placeholder works too. Title and lines use the palette tokens.")
        Sidebar sidebar,

        @Comment("The header and footer of the player list (Tab).")
        Tab tab,

        @Comment("Boss bars that take turns at the top of the screen.")
        BossBars bossBars,

        @Comment("Chat announcements sent on a timer. Players can mute them in /settings → Announcements.")
        Announcements announcements) {

    public HudSettings() {
        this(new Sidebar(), new Tab(), new BossBars(), new Announcements());
    }

    public record Sidebar(
            @Comment("Show the sidebar in the lobby.")
            boolean enabled,
            @Comment("The board itself; 'enabled' inside it is ignored.")
            SidebarConfig board) {

        public Sidebar() {
            this(true, new SidebarConfig(true, List.of("{primary}&lLOBBY"), List.of(
                    "{primary}✦ Lobby",
                    "  {letters_black}· {info}{icon:star} {letters}Here{letters_black}: {info}%lobby_hud_here%",
                    "  {letters_black}· {success}{icon:heart} {letters}Network{letters_black}: {success}%lobby_hud_network%",
                    "",
                    "{muted}{server-ip}"), new SidebarConfig.Update(20, true, true)));
        }
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
                    List.of("", "{letters}Players on the network{letters_black}: {success}%lobby_hud_network%", ""),
                    Duration.ofSeconds(5));
        }
    }

    public record BossBars(
            @Comment("Show boss bars in the lobby.")
            boolean enabled,
            @Comment("The texts, shown one after another. Placeholders work.")
            List<String> texts,
            @Comment("Time each text stays before the next one.")
            Duration interval,
            @Comment("PINK, BLUE, RED, GREEN, YELLOW, PURPLE or WHITE.")
            String colour,
            @Comment("PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12 or NOTCHED_20.")
            String overlay) {

        public BossBars() {
            this(true, List.of("{letters}Pick a server with the {highlight}compass{letters}.",
                    "{letters}Choose who you see in the lobby with the {highlight}dye{letters}."),
                    Duration.ofSeconds(15), "PURPLE", "PROGRESS");
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
