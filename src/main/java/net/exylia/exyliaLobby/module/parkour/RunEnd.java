package net.exylia.exyliaLobby.module.parkour;

/** Why a parkour run ended. */
public enum RunEnd {
    /** {@code /parkour leave} or the leave item: the runner is told. */
    LEFT,
    /** The player quit the server. */
    QUIT,
    /** The player walked out of the lobby. */
    LOBBY,
    /** The module or the plugin is stopping. */
    DISABLED,
    /** A new run starts over this one. */
    REPLACED,
    /** A course was finished. */
    FINISHED,
    /** A course's time limit, or a sprint's clock, ran out. */
    TIME_UP,
    /** An endless runner fell off the path. */
    FELL,
    FLIGHT,
    ELYTRA,
    RIPTIDE,
    PEARL,
    VEHICLE,
    GAME_MODE,
    TELEPORT,
    /** An endless landing the server could not confirm: off the path, out of reach or too fast. */
    UNCONFIRMED;

    /** Ended by something a run does not allow: nothing is recorded and the runner is told why. */
    public boolean cheat() {
        return ordinal() >= FLIGHT.ordinal();
    }

    /** Ended by the runner's own play, so an endless run is scored and shown. */
    boolean scored() {
        return this == FELL || this == TIME_UP;
    }
}
