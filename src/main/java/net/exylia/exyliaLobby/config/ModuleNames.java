package net.exylia.exyliaLobby.config;

import net.exylia.lib.config.Comment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code lang/<language>/modules.yml}: how each module is named and described on its card. */
public record ModuleNames(
        @Comment("One entry per module id: its name and a short description (two or three lines of about 30 characters).")
        Map<String, Info> modules) {

    public ModuleNames() {
        this(defaults());
    }

    public record Info(String name, List<String> description) {
        public Info() {
            this("", List.of());
        }
    }

    /** The entry for a module, or one named after its id. */
    public Info of(String id) {
        Info info = modules.get(id);
        return info == null || info.name().isBlank() ? new Info(id, List.of()) : info;
    }

    private static Map<String, Info> defaults() {
        Map<String, Info> map = new LinkedHashMap<>();
        map.put("spawn", new Info("Spawn", List.of("Spawn point, /spawn, void", "return and respawn at spawn.")));
        map.put("state", new Info("Player state", List.of("Game mode, health, hunger,", "effects, time and weather.")));
        map.put("protection", new Info("Protection", List.of("No building, damage, drops,", "mobs, fire or explosions.")));
        map.put("join", new Info("Join and leave", List.of("Messages, welcome title and", "commands on join and quit.")));
        map.put("hotbar", new Info("Hotbar", List.of("Items that open menus, send", "to servers or run commands.")));
        map.put("movement", new Info("Movement", List.of("Double jump and", "launch pads.")));
        map.put("triggers", new Info("Triggers", List.of("Buttons, plates and areas", "that run actions.")));
        map.put("visibility", new Info("Visibility", List.of("Players choose who", "they see in the lobby.")));
        map.put("selector", new Info("Server selector", List.of("Servers and lobbies with live", "counts and portals.")));
        map.put("games", new Info("Games", List.of("Every game the Exylia", "plugins offer, on one screen.")));
        map.put("hud", new Info("HUD", List.of("Sidebar, tab list, boss", "bars and announcements.")));
        map.put("pvp", new Info("Lobby PvP", List.of("Hold the sword to fight", "players who opted in.")));
        map.put("parkour", new Info("Parkour", List.of("Courses, endless parkour", "and leaderboards.")));
        map.put("gadgets", new Info("Gadgets", List.of("Grappling hook, ender ride,", "paint blaster and more.")));
        map.put("chat", new Info("Chat", List.of("Chat lock, slow mode", "and clear chat.")));
        map.put("onboarding", new Info("Onboarding", List.of("Hands new players", "to ExyliaJourney.")));
        return map;
    }
}
