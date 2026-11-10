package net.exylia.exyliaLobby.area;

/** Why a player entered or left the lobby. */
public enum AreaCause {
    /** Joined the server inside a lobby area. */
    JOIN,
    /** Walked, teleported, respawned or changed world across the edge. */
    MOVE,
    /** Quit the server while inside. */
    QUIT,
    /** The areas changed under them (an admin edited them, or a reload). */
    CHANGE,
    /** A module was switched on while they were inside, so it catches up. Sent only to that module. */
    MODULE,
    /** Build mode was switched on (leave) or off (enter) while inside: the lobby lets the admin be. */
    BUILD,
    /** The plugin is stopping. */
    DISABLE
}
