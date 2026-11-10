package net.exylia.exyliaLobby.module.parkour;

import net.exylia.lib.util.PeriodClock;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StandingsTest {

    private static ParkourRecord row(UUID player, long value) {
        return new ParkourRecord("x", player, "n", "tower|all", value, List.of(), 0);
    }

    @Test
    void courseBeatsWhenFasterAndModeWhenHigher() {
        assertTrue(Standings.beats(false, 10, null));
        assertTrue(Standings.beats(false, 9, 10L));
        assertFalse(Standings.beats(false, 10, 10L));
        assertTrue(Standings.beats(true, 11, 10L));
        assertFalse(Standings.beats(true, 9, 10L));
    }

    @Test
    void rankIsOneBasedAndZeroWhenAbsent() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<ParkourRecord> top = List.of(row(a, 1), row(b, 2));
        assertEquals(1, Standings.rankIn(top, a));
        assertEquals(2, Standings.rankIn(top, b));
        assertEquals(0, Standings.rankIn(top, UUID.randomUUID()));
    }

    @Test
    void aResultCountsOnEveryPeriodAndDailyOnlyOnItsOwn() {
        assertEquals(4, Standings.boardsOf("tower").size());
        assertEquals(List.of(new Board("daily", Board.Period.DAILY)), Standings.boardsOf("daily"));
    }

    @Test
    void periodKeysRollOver() {
        PeriodClock clock = PeriodClock.of("UTC", 0, DayOfWeek.MONDAY);
        Board daily = new Board("tower", Board.Period.DAILY);
        assertEquals("tower|all", new Board("tower", Board.Period.ALL).key(clock));
        assertTrue(daily.key(clock).startsWith("tower|d"));
    }
}
