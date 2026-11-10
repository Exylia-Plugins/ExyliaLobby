package net.exylia.exyliaLobby.module.parkour.endless;

import java.util.List;
import java.util.Set;

/**
 * One jump of an endless path: from the exit of the jump before to {@link #landing}, and every block that
 * makes it (the landing, a strip or platform it is part of, a pillar under it, a wall, a ladder, a ceiling).
 *
 * @param index           0 is the start platform
 * @param takeoff         where the player jumps from (the exit of the jump before)
 * @param from            what that block is
 * @param fallOntoFrom    how far the player fell onto the take-off, for a slime bounce
 * @param ceiling         a slab hangs over the take-off
 * @param shape           how this jump is built
 * @param landing         the block the jump lands on
 * @param surface         what the landing is
 * @param standable       every block of this jump a player may stand on (a strip or platform has several)
 * @param exit            where the next jump starts from
 * @param ceilingOverExit a slab hangs over the exit, so the next jump is a head-hitter
 * @param pieces          every block drawn for this jump
 * @param air             the cells this jump needs empty: headroom and the flight between take-off and landing
 * @param points          what landing it is worth
 * @param bonus           part of a bonus segment
 */
public record Jump(int index, Cell takeoff, Role from, double fallOntoFrom, boolean ceiling, Shape shape,
                   Cell landing, Role surface, List<Cell> standable, Cell exit, boolean ceilingOverExit,
                   List<Piece> pieces, Set<Cell> air, int points, boolean bonus) {

    /** How a jump is built. */
    public enum Shape {START, SINGLE, STRIP, REST, PILLAR, NEO, CLIMB}

    /** One block drawn for a jump. {@code facing} is a heading index (0-3), for ladders and vines. */
    public record Piece(Cell at, Role role, int facing) {
    }

    /** The lowest block a player standing on this jump can be on. */
    public int lowestY() {
        int lowest = Integer.MAX_VALUE;
        for (Cell cell : standable) lowest = Math.min(lowest, cell.y());
        return lowest;
    }
}
