package net.exylia.exyliaLobby.module.selector;

import net.exylia.lib.config.Comment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code modules/selector/config.yml}: the servers players can pick, and the NPCs and portals that send them there. */
public record SelectorSettings(
        @Comment("Labels of the player command that opens the server selector. Empty removes the command.")
        List<String> command,

        @Comment("Labels of the player command that opens the lobby selector. Empty removes the command.")
        List<String> lobbyCommand,

        @Comment("The server ids (as the proxy names them) that are lobbies of this network: the lobby selector lists them,")
        @Comment("emptiest first, with the one the player is on glowing.")
        List<String> lobbies,

        @Comment("The server catalogue, in the order of 'order'. The key is only a name for the entry.")
        Map<String, Server> servers,

        @Comment("Server NPCs placed in game from /lobbyadmin → Server selector. A click sends the player to the server.")
        Map<String, Npc> npcs,

        @Comment("Portals placed in game from /lobbyadmin → Server selector. Walking in sends the player to the server.")
        Map<String, Portal> portals) {

    public SelectorSettings() {
        this(List.of("servers"), List.of("lobbies"), List.of(), defaults(), new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    /**
     * One entry of the catalogue.
     *
     * @param name        shown on the card; colours allowed
     * @param icon        any item a menu accepts: a material, {@code basehead-...}, {@code nexo:id}
     * @param order       position in the menu, lowest first
     * @param description up to three short lines
     * @param server      the server id on the proxy
     * @param permission  needed to join. Empty is everyone; without it the card shows as locked
     */
    public record Server(String name, String icon, int order, List<String> description, String server, String permission) {
        public Server() {
            this("Server", "GRASS_BLOCK", 0, List.of(), "server", "");
        }
    }

    /** A server NPC. {@code skin} is a player name, {@code mirror} (each viewer's own skin) or empty for the default one. */
    public record Npc(String server, String skin, String world, double x, double y, double z, double yaw, double pitch,
                      List<String> hologram, double hologramHeight) {
        public Npc() {
            this("", "", "world", 0, 64, 0, 0, 0, List.of("{letters}%server_name%", "{info}%server_players% {letters_black}playing"), 2.4);
        }
    }

    /** A portal: the box between two corners. */
    public record Portal(String server, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        public Portal() {
            this("", "world", 0, 0, 0, 0, 0, 0);
        }
    }

    private static Map<String, Server> defaults() {
        Map<String, Server> map = new LinkedHashMap<>();
        map.put("survival", new Server("{success}Survival", "GRASS_BLOCK", 1,
                List.of(" {letters}Build, trade and explore.", " {letters}Your world, your rules."), "survival", ""));
        map.put("minigames", new Server("{highlight}Minigames", "NETHER_STAR", 2,
                List.of(" {letters}Quick matches against", " {letters}other players."), "minigames", ""));
        return map;
    }
}
