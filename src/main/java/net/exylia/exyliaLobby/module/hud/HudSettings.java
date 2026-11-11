package net.exylia.exyliaLobby.module.hud;

import net.exylia.lib.config.Comment;
import net.exylia.lib.scoreboard.SidebarConfig;

import java.time.Duration;
import java.util.List;

/** {@code modules/hud/config.yml}: what players see on screen inside the lobby. Every part switches on its own. */
public record HudSettings(
        @Comment("The sidebar. Network counts: %network_online%, %network_players_<server>%, %network_state_<server>%")
        @Comment("(server names as the proxy knows them; list the ones in your selector). %lobby_hud_here% counts this lobby.")
        @Comment("PlaceholderAPI works too: %server_time_dd/MM/yyyy% needs its Server expansion, %luckperms_prefix% LuckPerms;")
        @Comment("the text after | shows when one is missing. A leading <flow> animates the title: keep interval at 1-2.")
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
            this(true, new SidebarConfig(true,
                    List.of("<flow><gradient:{primary}:{secondary_light}>&l✦ LOBBY ✦</gradient>"), List.of(
                    "{letters_black}%server_time_dd/MM/yyyy|Hub%",
                    "",
                    "{primary}&l▸▸ YOU",
                    "{info}{icon:tag} {letters}Name{letters_black}: {info}%player_name%",
                    "{highlight}{icon:star} {letters}Rank{letters_black}: %luckperms_prefix|{letters_black}—%",
                    "{accent}{icon:eye} {letters}Players{letters_black}: %lobby_visibility_state%",
                    "",
                    "{primary}&l▸▸ SERVERS",
                    "{success}{icon:pickaxe} {letters}Survival{letters_black}: {success}%network_players_survival%",
                    "{highlight}{icon:star} {letters}Minigames{letters_black}: {success}%network_players_minigames%",
                    "",
                    "{info}{icon:compass} {letters}Online{letters_black}: {success}%network_online% "
                            + "{letters_black}· {info}{icon:ping} %player_ping%ms",
                    "",
                    "{primary}{server-ip}"), new SidebarConfig.Update(2, true, true)));
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
                    List.of("", "{letters}Players on the network{letters_black}: {success}%network_online%", ""),
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
