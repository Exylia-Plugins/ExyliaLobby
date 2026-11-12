package net.exylia.exyliaLobby.module.parkour;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissedJumpTest {

    @Test
    void oneUnseenJumpCountsOnlyInARealJumpsTime() {
        assertTrue(EndlessRun.missedOne(3, 5, EndlessRun.TWO_JUMPS_MILLIS));
        assertFalse(EndlessRun.missedOne(3, 5, EndlessRun.TWO_JUMPS_MILLIS - 1));
        assertFalse(EndlessRun.missedOne(3, 6, 10_000));
    }
}
