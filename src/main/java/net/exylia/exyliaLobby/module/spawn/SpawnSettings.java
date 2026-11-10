package net.exylia.exyliaLobby.module.spawn;

import net.exylia.lib.config.Comment;

import java.util.List;
import java.util.Map;

/** {@code modules/spawn/config.yml}: where players appear and when they are sent back. */
public record SpawnSettings(
        @Comment("Labels of the player command that teleports to spawn. Empty removes the command.")
        List<String> command,

        @Comment("Who is sent to spawn when they join the server inside the lobby: ALWAYS, FIRST_JOIN or NEVER.")
        JoinMode onJoin,

        @Comment("Seconds standing still before /spawn teleports. 0 teleports at once.")
        double warmup,

        @Comment("Players who die inside the lobby respawn at spawn instead of their bed.")
        boolean respawn,

        @Comment("Send players back to spawn when they fall below the height below, inside the lobby.")
        boolean voidReturn,

        @Comment("The height (Y) under which a player in the lobby is sent back. Keep it under the lowest floor.")
        int voidY,

        @Comment("Spawn points, set in game from /lobbyadmin → Spawn. The key is the world; a player gets the point")
        @Comment("of the world they are in, and the first one when their world has none.")
        Map<String, Point> points) {

    public SpawnSettings() {
        this(List.of("spawn"), JoinMode.ALWAYS, 0.0, true, true, -70, Map.of());
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
