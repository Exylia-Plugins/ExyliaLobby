package net.exylia.exyliaLobby.module.parkour.endless;

/**
 * Whether a sprint jump between two blocks is possible, so the generator never draws one that is not.
 *
 * <p>Distances are between block centres, horizontally: a straight jump over a gap of {@code g} blocks is
 * {@code g + 1}. The rise is where the feet end up minus where they start, so a post (1.5 tall) counts half a
 * block higher. The limits are on the safe side of what a vanilla player reaches:
 * <ul>
 *   <li>flat or down: 5 (a 4-block gap, the hardest flat jump there is);</li>
 *   <li>up half a block: 4.5, up one: 4 (a 3+1), up more than one: 3, more than 1.25: impossible;</li>
 *   <li>a slab over the take-off (head-hitter): no rise and 3 at most;</li>
 *   <li>off slime: up to 60% of the fall onto it, 3 far when above a normal jump's height;</li>
 *   <li>off soul sand: 3, or 2 going up; off honey: 2.5 and never up;</li>
 *   <li>onto a post: 3.6 at most (a narrow target); off a post: 4.</li>
 * </ul>
 */
public final class JumpPhysics {

    /** How high a jump lifts the feet, in blocks. */
    public static final double JUMP_HEIGHT = 1.25;
    private static final double SLIME_RETURN = 0.6;
    private static final double EPSILON = 1e-9;

    private JumpPhysics() {
    }

    /**
     * @param from         the block jumped from
     * @param fallOntoFrom how far the player fell onto {@code from} (only matters for slime)
     * @param ceiling      whether a slab hangs over the take-off
     * @param dx           centre to centre, x
     * @param dz           centre to centre, z
     * @param dy           block y of the landing minus block y of the take-off
     * @param to           the block landed on
     */
    public static boolean reachable(Role from, double fallOntoFrom, boolean ceiling, int dx, int dz, int dy, Role to) {
        double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (distance < 1.0 || dy < -4) return false;
        double rise = dy + to.surface() - from.surface();
        double reach;
        if (ceiling) {
            if (rise > EPSILON) return false;
            reach = 3.0;
        } else {
            reach = switch (from) {
                case SLIME -> {
                    double bounce = Math.max(JUMP_HEIGHT, SLIME_RETURN * fallOntoFrom);
                    if (rise > bounce + EPSILON) yield 0;
                    yield rise > JUMP_HEIGHT ? 3.0 : normal(rise);
                }
                case HONEY -> rise > EPSILON ? 0 : 2.5;
                case SOUL_SAND -> rise > 1.0 ? 0 : rise > EPSILON ? 2.0 : 3.0;
                default -> normal(rise);
            };
        }
        if (to == Role.POST) reach = Math.min(reach, 3.6);
        if (from == Role.POST) reach = Math.min(reach, 4.0);
        return distance <= reach + EPSILON;
    }

    private static double normal(double rise) {
        if (rise > JUMP_HEIGHT + EPSILON) return 0;
        if (rise > 1.0 + EPSILON) return 3.0;
        if (rise > 0.5 + EPSILON) return 4.0;
        if (rise > EPSILON) return 4.5;
        return 5.0;
    }
}
