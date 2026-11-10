package net.exylia.exyliaLobby.module.parkour.endless;

/**
 * What a block of the path is for. A theme turns each role into a block; the functional ones (ice, slime,
 * soul sand, honey) keep their material in every theme because their physics is the point.
 */
public enum Role {
    /** A plain jump block. */
    FLOOR,
    /** A jump block of a bonus segment, worth more. */
    BONUS,
    /** A wide rest platform, and the start. */
    REST,
    ICE,
    SLIME,
    SOUL_SAND,
    HONEY,
    /** A fence or wall post: a narrow target half a block higher. */
    POST,
    /** The slab over a head-hitter's take-off. */
    CEILING,
    LADDER,
    /** The wall a ladder climbs or a neo jump goes around. */
    WALL,
    /** The column under a pillar jump. */
    PILLAR;

    /** Where a player's feet are above the block's own y when standing on it. */
    double surface() {
        return this == POST ? 1.5 : 1.0;
    }

    /** Whether a player can stand on it as a landing. */
    public boolean standable() {
        return this != CEILING && this != LADDER && this != WALL && this != PILLAR;
    }
}
