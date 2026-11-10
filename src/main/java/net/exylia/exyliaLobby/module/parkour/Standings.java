package net.exylia.exyliaLobby.module.parkour;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The pure rules of the boards: what beats a best, which boards a result counts on, who stands where. */
final class Standings {

    private Standings() {
    }

    /** Whether {@code value} beats {@code current} on a board: lower for a course, higher for an endless mode. */
    static boolean beats(boolean mode, long value, @Nullable Long current) {
        if (current == null) return true;
        return mode ? value > current : value < current;
    }

    /** Every board a result on {@code name} counts towards: its all-time, monthly, weekly and daily ones. */
    static List<Board> boardsOf(String name) {
        Set<Board> boards = new LinkedHashSet<>();
        for (Board.Period period : Board.Period.values()) boards.add(new Board(name, period));
        return List.copyOf(boards);
    }

    static int rankIn(List<ParkourRecord> top, UUID player) {
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).player().equals(player)) return i + 1;
        }
        return 0;
    }
}
