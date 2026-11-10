package net.exylia.exyliaLobby.module.parkour;

import net.exylia.lib.util.PeriodClock;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimesAndBoardsTest {

    @Test
    void timesAreTicksShownToTheMillisecond() {
        assertEquals(12_350, Times.millis(247));
        assertEquals("12.350", Times.format(12_350));
        assertEquals("1:02.050", Times.format(62_050));
        assertEquals("1:00:00.000", Times.format(3_600_000));
        assertEquals("0.000", Times.format(-5));
    }

    @Test
    void splitsCompareWithTheBestRun() {
        List<Long> best = List.of(10_000L, 25_000L);
        assertEquals(-1_200L, Times.compare(best, 0, 8_800));
        assertEquals(350L, Times.compare(best, 1, 25_350));
        assertNull(Times.compare(best, 2, 30_000));
        assertNull(Times.compare(null, 0, 1));
        assertEquals("-1.200", Times.delta(-1_200));
        assertEquals("+0.350", Times.delta(350));
        assertEquals("+0.000", Times.delta(0));
    }

    @Test
    void boardsParseTheirPeriod() {
        assertEquals(new Board("endless", Board.Period.WEEKLY), Board.parse("endless-weekly"));
        assertEquals(new Board("my-tower", Board.Period.ALL), Board.parse("my-tower"));
        assertEquals(new Board("my-tower", Board.Period.DAILY), Board.parse("my-tower-daily"));
        assertEquals(Board.Period.DAILY, Board.parse("daily").period(), "the daily mode only has a daily board");
        assertEquals("endless-monthly", new Board("endless", Board.Period.MONTHLY).id());
    }

    @Test
    void periodKeysRollAtTheResetHour() {
        PeriodClock clock = PeriodClock.of("UTC", 5, DayOfWeek.MONDAY);
        ZonedDateTime beforeReset = ZonedDateTime.parse("2026-11-10T03:00:00Z");
        ZonedDateTime afterReset = ZonedDateTime.parse("2026-11-10T06:00:00Z");
        assertEquals("2026-11-09", clock.key(PeriodClock.Period.DAILY, beforeReset));
        assertEquals("2026-11-10", clock.key(PeriodClock.Period.DAILY, afterReset));
        assertEquals("all", Board.Period.ALL.key(clock));
        assertEquals('w', Board.Period.WEEKLY.key(clock).charAt(0));
        assertEquals("d" + clock.key(PeriodClock.Period.DAILY), Board.Period.DAILY.key(clock));
        assertEquals("endless|all", new Board("endless", Board.Period.ALL).key(clock));
    }
}
