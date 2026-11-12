package net.exylia.exyliaLobby.module.parkour;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissedJumpTest {

    @Test
    void quickChainsCountAndLongLeapsNeedReach() {
        assertTrue(EndlessRun.counts(3, 3 + EndlessRun.MAX_SKIP, false));
        assertFalse(EndlessRun.counts(3, 4 + EndlessRun.MAX_SKIP, false));
        assertTrue(EndlessRun.counts(3, 10, true));
    }
}
