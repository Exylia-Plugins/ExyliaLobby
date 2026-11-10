package net.exylia.exyliaLobby.module.parkour;

import java.util.List;

/** Where on a course a position stands, and what stepping there means to a run. Pure. */
final class CourseTrack {

    static final int NONE = -1;
    static final int START = -2;
    static final int FINISH = -3;

    /** What entering a spot does. */
    enum Step {NOTHING, RESTART, CHECKPOINT, FINISH, MISSED}

    private CourseTrack() {
    }

    /**
     * The spot a position stands in: the next checkpoint first (a checkpoint may overlap the finish or the start
     * on a looping course), then the finish, the start, and any other checkpoint; {@link #NONE} outside them all.
     *
     * @param reached checkpoints reached so far, so the next one is {@code checkpoints[reached]}
     * @return a checkpoint index, {@link #START}, {@link #FINISH} or {@link #NONE}
     */
    static int spotAt(ParkourCourses.Course course, int reached, double x, double y, double z) {
        List<ParkourCourses.Spot> checkpoints = course.checkpoints();
        if (reached < checkpoints.size() && checkpoints.get(reached).contains(x, y, z)) return reached;
        if (course.finish().contains(x, y, z)) return FINISH;
        if (course.start().contains(x, y, z)) return START;
        for (int i = 0; i < checkpoints.size(); i++) {
            if (checkpoints.get(i).contains(x, y, z)) return i;
        }
        return NONE;
    }

    /** What entering {@code spot} does to a run that reached {@code reached} of {@code total} checkpoints. */
    static Step step(int spot, int reached, int total) {
        if (spot == START) return Step.RESTART;
        if (spot == FINISH) return reached >= total ? Step.FINISH : Step.MISSED;
        if (spot == reached && spot < total) return Step.CHECKPOINT;
        return Step.NOTHING;
    }
}
