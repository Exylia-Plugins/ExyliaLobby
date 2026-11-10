package net.exylia.exyliaLobby.module.parkour.endless;

import org.junit.jupiter.api.Test;

import static net.exylia.exyliaLobby.module.parkour.endless.JumpPhysics.reachable;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JumpPhysicsTest {

    @Test
    void flatSprintJumpsReachFourBlockGapsAndNoMore() {
        assertTrue(reachable(Role.FLOOR, 0, false, 5, 0, 0, Role.FLOOR));
        assertFalse(reachable(Role.FLOOR, 0, false, 6, 0, 0, Role.FLOOR));
        assertTrue(reachable(Role.FLOOR, 0, false, 4, 2, 0, Role.FLOOR));
        assertFalse(reachable(Role.FLOOR, 0, false, 5, 1, 0, Role.FLOOR));
    }

    @Test
    void goingUpShortensTheJumpAndTwoUpIsImpossible() {
        assertTrue(reachable(Role.FLOOR, 0, false, 4, 0, 1, Role.FLOOR));
        assertFalse(reachable(Role.FLOOR, 0, false, 5, 0, 1, Role.FLOOR));
        assertFalse(reachable(Role.FLOOR, 0, false, 2, 0, 2, Role.FLOOR));
        assertTrue(reachable(Role.FLOOR, 0, false, 5, 0, -1, Role.FLOOR));
    }

    @Test
    void sameBlockIsNoJump() {
        assertFalse(reachable(Role.FLOOR, 0, false, 0, 0, 0, Role.FLOOR));
    }

    @Test
    void postsAreHigherAndNarrow() {
        assertTrue(reachable(Role.FLOOR, 0, false, 3, 0, 0, Role.POST));
        assertFalse(reachable(Role.FLOOR, 0, false, 4, 0, 0, Role.POST));
        assertFalse(reachable(Role.FLOOR, 0, false, 2, 0, 1, Role.POST));
        assertTrue(reachable(Role.POST, 0, false, 4, 0, 0, Role.FLOOR));
        assertFalse(reachable(Role.POST, 0, false, 5, 0, 0, Role.FLOOR));
    }

    @Test
    void headHittersAreShortAndNeverUp() {
        assertTrue(reachable(Role.FLOOR, 0, true, 3, 0, 0, Role.FLOOR));
        assertFalse(reachable(Role.FLOOR, 0, true, 4, 0, 0, Role.FLOOR));
        assertFalse(reachable(Role.FLOOR, 0, true, 2, 0, 1, Role.FLOOR));
    }

    @Test
    void slimeBouncesBackWhatTheFallGave() {
        double threeDown = 3 + JumpPhysics.JUMP_HEIGHT;
        assertTrue(reachable(Role.SLIME, threeDown, false, 3, 0, 2, Role.FLOOR));
        assertFalse(reachable(Role.SLIME, threeDown, false, 4, 0, 2, Role.FLOOR));
        assertFalse(reachable(Role.SLIME, JumpPhysics.JUMP_HEIGHT, false, 2, 0, 2, Role.FLOOR));
        assertFalse(reachable(Role.SLIME, threeDown, false, 2, 0, 3, Role.FLOOR));
    }

    @Test
    void slowBlocksShortenTheJump() {
        assertTrue(reachable(Role.SOUL_SAND, 0, false, 2, 0, 0, Role.FLOOR));
        assertFalse(reachable(Role.SOUL_SAND, 0, false, 3, 0, 0, Role.FLOOR));
        assertTrue(reachable(Role.HONEY, 0, false, 2, 0, 0, Role.FLOOR));
        assertFalse(reachable(Role.HONEY, 0, false, 2, 0, 1, Role.FLOOR));
        assertFalse(reachable(Role.HONEY, 0, false, 3, 0, 0, Role.FLOOR));
    }
}
