package net.exylia.exyliaLobby.module.parkour;

import org.junit.jupiter.api.Test;


import static net.exylia.exyliaLobby.module.parkour.CourseTrack.FINISH;
import static net.exylia.exyliaLobby.module.parkour.CourseTrack.NONE;
import static net.exylia.exyliaLobby.module.parkour.CourseTrack.START;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CourseTrackTest {

    private static ParkourCourses.Spot at(double x) {
        return new ParkourCourses.Spot(x, 64, 0, 0, 0, 0.8);
    }

    /** A loop: the finish sits on the start, checkpoint 1 at x=10, checkpoint 2 at x=20. */
    private final ParkourCourses.Course loop = new ParkourCourses.Course().withStart(at(0))
            .withCheckpoint(at(10)).withCheckpoint(at(20)).withFinish(at(0));

    @Test
    void theNextCheckpointWinsThenFinishThenStart() {
        assertEquals(0, CourseTrack.spotAt(loop, 0, 10, 64, 0));
        assertEquals(1, CourseTrack.spotAt(loop, 0, 20, 64, 0), "a later checkpoint is still found, as itself");
        assertEquals(FINISH, CourseTrack.spotAt(loop, 2, 0, 64, 0), "finish before start where they overlap");
        assertEquals(NONE, CourseTrack.spotAt(loop, 0, 5, 64, 0));
        ParkourCourses.Course line = new ParkourCourses.Course().withStart(at(0)).withFinish(at(30));
        assertEquals(START, CourseTrack.spotAt(line, 0, 0.5, 65, 0));
    }

    @Test
    void checkpointsCountOnlyInOrder() {
        assertEquals(CourseTrack.Step.CHECKPOINT, CourseTrack.step(0, 0, 2));
        assertEquals(CourseTrack.Step.NOTHING, CourseTrack.step(1, 0, 2), "skipping one does nothing");
        assertEquals(CourseTrack.Step.NOTHING, CourseTrack.step(0, 1, 2), "going back does nothing");
        assertEquals(CourseTrack.Step.MISSED, CourseTrack.step(FINISH, 1, 2));
        assertEquals(CourseTrack.Step.FINISH, CourseTrack.step(FINISH, 2, 2));
        assertEquals(CourseTrack.Step.RESTART, CourseTrack.step(START, 1, 2));
        assertEquals(CourseTrack.Step.NOTHING, CourseTrack.step(NONE, 0, 2));
    }
}
