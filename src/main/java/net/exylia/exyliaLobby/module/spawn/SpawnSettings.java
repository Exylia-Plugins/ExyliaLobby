package net.exylia.exyliaLobby.module.spawn;

import net.exylia.exyliaLobby.module.shared.Countdown;
import net.exylia.lib.config.Comment;
import net.exylia.lib.config.Menu;

import java.util.List;
import java.util.Map;

/** {@code modules/spawn/config.yml}: where players appear and when they are sent back. */
public record SpawnSettings(
        @Comment("Labels of the player command that teleports to spawn. Empty removes the command.")
        List<String> command,

        @Comment("Who is sent to spawn when they join the server inside the lobby: ALWAYS, FIRST_JOIN or NEVER.")
        JoinMode onJoin,

        @Comment("Seconds standing still before /spawn teleports. 0 teleports at once.")
        @Menu.Range(min = 0, max = 30, step = 0.5)
        double warmup,

        @Comment("Sounds of the warmup countdown, heard by that player only, as NAME|volume|pitch. Blank plays nothing.")
        Countdown.Sounds warmupSounds,

        @Comment("Players who die inside the lobby respawn at spawn instead of their bed.")
        boolean respawn,

        @Comment("Send players back to spawn when they fall below the height below, inside the lobby.")
        boolean voidReturn,

        @Comment("The height (Y) under which a player in the lobby is sent back. Keep it under the lowest floor.")
        @Menu.Range(min = -2048, max = 320, step = 10)
        int voidY,

        @Comment("Spawn points, set in game from /lobbyadmin → Spawn. The key is the world; a player gets the point")
        @Comment("of the world they are in, and the first one when their world has none.")
        Map<String, Point> points) {

    public SpawnSettings() {
        this(List.of("spawn"), JoinMode.ALWAYS, 0.0, new Countdown.Sounds(), true, true, -70, Map.of());
    }

    /** Whether to teleport a player who joins inside the lobby. */
    public enum JoinMode {
        ALWAYS, FIRST_JOIN, NEVER
    }

    /** One spawn point. */
    public record Point(String world, double x, double y, double z, double yaw, double pitch) {
        public Point() {
            this("world", 0, 64, 0, 0, 0);
        }
    }
}
