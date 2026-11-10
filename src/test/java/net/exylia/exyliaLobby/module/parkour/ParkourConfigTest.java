package net.exylia.exyliaLobby.module.parkour;

import net.exylia.exyliaLobby.module.parkour.endless.SegmentType;
import net.exylia.exyliaLobby.module.parkour.endless.Tuning;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkourConfigTest {

    @Test
    void spotsHoldFeetCloseAboveThem() {
        ParkourCourses.Spot spot = new ParkourCourses.Spot(10.5, 64, 10.5, 0, 0, 0.8);
        assertTrue(spot.contains(10.9, 64, 11.2));
        assertTrue(spot.contains(10.5, 65.5, 10.5));
        assertFalse(spot.contains(11.5, 64, 10.5));
        assertFalse(spot.contains(10.5, 62, 10.5));
        assertFalse(new ParkourCourses.Spot().contains(0, -1000, 0));
    }

    @Test
    void medalsAreTheBestTargetBeaten() {
        ParkourCourses.Medals medals = new ParkourCourses.Medals(30, 45, 0);
        assertEquals(ParkourCourses.Medal.GOLD, medals.of(30_000));
        assertEquals(ParkourCourses.Medal.SILVER, medals.of(30_001));
        assertNull(medals.of(60_000));
    }

    @Test
    void themesRotateInBlocksOfJumps() {
        assertEquals(1, ParkourThemes.themeAt(0, 25, 3, 1));
        assertEquals(1, ParkourThemes.themeAt(24, 25, 3, 1));
        assertEquals(2, ParkourThemes.themeAt(25, 25, 3, 1));
        assertEquals(0, ParkourThemes.themeAt(50, 25, 3, 1));
        assertEquals(1, ParkourThemes.themeAt(999, 0, 3, 1));
        assertEquals(0, ParkourThemes.themeAt(5, 25, 0, 4));
    }

    @Test
    void idsFitCommandsAndBoards() {
        assertEquals("tower-2", ParkourCourses.id("Tower_2 "));
    }

    @Test
    void tuningKeepsDefaultsForWhatIsNotWritten() {
        ParkourSettings.PathTuning written = new ParkourSettings.PathTuning(Map.of("slime", 0), Map.of(),
                Map.of("0", 2, "x", 5), 10, 0.1, 5, 3, 0.2);
        Tuning tuning = written.tuning();
        assertEquals(0, tuning.weights().get(SegmentType.SLIME));
        assertEquals(SegmentType.ICE.defaultWeight(), tuning.weights().get(SegmentType.ICE));
        assertEquals(2, tuning.maxDifficulty(1000));
        assertEquals(10, tuning.restEvery());
        assertEquals(Tuning.defaults().curve(), new ParkourSettings.PathTuning().tuning().curve());
    }
}
