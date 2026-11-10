package net.exylia.exyliaLobby.module.parkour.endless;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The jumps of an endless path drawn for a runner standing on one: a few behind, the one under them, a few ahead.
 * Pure.
 *
 * @param first the lowest jump index drawn
 * @param last  the highest jump index drawn
 */
public record Window(int first, int last) {

    /** What is drawn while standing on jump {@code current}. */
    public static Window around(int current, int ahead, int behind) {
        return new Window(Math.max(0, current - Math.max(0, behind)), current + Math.max(1, ahead));
    }

    public boolean contains(int index) {
        return index >= first && index <= last;
    }

    /**
     * The blocks of the jumps leaving the window that no jump still in it uses: those are cleared, the rest stay.
     */
    public static Set<Cell> leftBehind(Collection<Jump> leaving, Collection<Jump> staying) {
        Set<Cell> kept = new HashSet<>();
        for (Jump jump : staying) {
            for (Jump.Piece piece : jump.pieces()) kept.add(piece.at());
        }
        Set<Cell> cleared = new LinkedHashSet<>();
        for (Jump jump : leaving) {
            for (Jump.Piece piece : jump.pieces()) {
                if (!kept.contains(piece.at())) cleared.add(piece.at());
            }
        }
        return cleared;
    }

    /**
     * Whether feet at this position stand on a block of the jump: over its top (a post's is half a block higher),
     * within a player's half width of it.
     */
    public static boolean standsOn(Jump jump, double x, double y, double z) {
        return standingOn(jump, x, y, z) != null;
    }

    /** The block of the jump feet at this position stand on (see {@link #standsOn}); null when none. */
    public static Cell standingOn(Jump jump, double x, double y, double z) {
        for (Cell cell : jump.standable()) {
            double top = cell.y() + jump.surface().surface();
            if (Math.abs(x - (cell.x() + 0.5)) <= 0.8 && Math.abs(z - (cell.z() + 0.5)) <= 0.8
                    && y >= top - 0.2 && y <= top + 0.3) {
                return cell;
            }
        }
        return null;
    }

    /**
     * Whether feet at this position are close enough to one of these blocks to be on the path: standing, jumping
     * between two of them or just fallen off one. Lenient on purpose; it only decides whether the server may
     * kick the runner for flying.
     */
    public static boolean near(Iterable<Cell> blocks, double x, double y, double z) {
        for (Cell cell : blocks) {
            if (Math.abs(x - (cell.x() + 0.5)) <= 3.5 && Math.abs(z - (cell.z() + 0.5)) <= 3.5
                    && y >= cell.y() - 3 && y <= cell.y() + 4) {
                return true;
            }
        }
        return false;
    }
}
