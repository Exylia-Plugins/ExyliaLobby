package net.exylia.exyliaLobby.module.parkour;

import net.exylia.lib.config.Comment;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@code modules/parkour/courses.yml}: every course, written by the in-game tools. */
public record ParkourCourses(
        @Comment("Courses by id (letters, numbers and dashes). Places are written by /lobbyadmin parkour and its screens.")
        Map<String, Course> courses) {

    public ParkourCourses() {
        this(Map.of());
    }

    /** Lower case letters, digits and dashes, so ids fit commands, placeholders and board names. */
    public static String id(String typed) {
        return typed.trim().toLowerCase(Locale.ROOT).replaceAll("[ _]+", "-").replaceAll("[^a-z0-9-]", "");
    }

    public record Course(
            @Comment("Shown name; colours allowed.")
            String name,
            @Comment("Players cannot start it while off.")
            boolean enabled,
            @Comment("Shown on its card: Easy, Medium, Hard, Expert...")
            String difficulty,
            @Comment("The card's icon.")
            String icon,
            String world,
            @Comment("Stepping here starts (or restarts) a run.")
            Spot start,
            @Comment("Reached in this order. A run goes back to the last one reached.")
            List<Spot> checkpoints,
            Spot finish,
            @Comment("Blocks that send a runner back to their last checkpoint, such as LAVA or MAGMA_BLOCK.")
            List<String> killBlocks,
            @Comment("Seconds a run may last. 0 has no limit.")
            double timeLimit,
            @Comment("Medal targets in seconds; 0 has no such medal.")
            Medals medals,
            @Comment("A finish faster than this many seconds is refused and logged. 0 checks nothing.")
            double minimumTime,
            @Comment("Given the first time a player finishes it.")
            List<Object> firstRewards,
            @Comment("Given on later finishes, at most once per cooldown.")
            List<Object> repeatRewards,
            Duration repeatCooldown) {

        public Course() {
            this("Course", true, "Medium", "FEATHER", "world", new Spot(), List.of(), new Spot(), List.of(), 0,
                    new Medals(), 0, List.of(), List.of(), Duration.ofHours(24));
        }

        /** Whether start and finish are both placed, so it can be run. */
        public boolean ready() {
            return start.placed() && finish.placed();
        }

        public Course withStart(Spot spot) {
            return new Course(name, enabled, difficulty, icon, world, spot, checkpoints, finish, killBlocks, timeLimit,
                    medals, minimumTime, firstRewards, repeatRewards, repeatCooldown);
        }

        public Course withCheckpoint(Spot spot) {
            List<Spot> more = new ArrayList<>(checkpoints);
            more.add(spot);
            return new Course(name, enabled, difficulty, icon, world, start, List.copyOf(more), finish, killBlocks,
                    timeLimit, medals, minimumTime, firstRewards, repeatRewards, repeatCooldown);
        }

        public Course withFinish(Spot spot) {
            return new Course(name, enabled, difficulty, icon, world, start, checkpoints, spot, killBlocks, timeLimit,
                    medals, minimumTime, firstRewards, repeatRewards, repeatCooldown);
        }

        public Course withDifficulty(String difficulty) {
            return new Course(name, enabled, difficulty, icon, world, start, checkpoints, finish, killBlocks, timeLimit,
                    medals, minimumTime, firstRewards, repeatRewards, repeatCooldown);
        }

        public Course withEnabled(boolean enabled) {
            return new Course(name, enabled, difficulty, icon, world, start, checkpoints, finish, killBlocks, timeLimit,
                    medals, minimumTime, firstRewards, repeatRewards, repeatCooldown);
        }

        public Course inWorld(String name) {
            return new Course(this.name, enabled, difficulty, icon, name, start, checkpoints, finish, killBlocks,
                    timeLimit, medals, minimumTime, firstRewards, repeatRewards, repeatCooldown);
        }

        /** The lowest place of the course, for falls. */
        public double lowestY() {
            double lowest = Math.min(start.y(), finish.y());
            for (Spot spot : checkpoints) lowest = Math.min(lowest, spot.y());
            return lowest;
        }
    }

    /**
     * A place a runner steps into: a pressure plate, or a small box around it.
     *
     * @param radius blocks to each side of the centre that still count; height is two blocks over it
     */
    public record Spot(double x, double y, double z, double yaw, double pitch, double radius) {
        public Spot() {
            this(0, -1000, 0, 0, 0, 0.8);
        }

        /** Set in game rather than the default. */
        public boolean placed() {
            return y > -999;
        }

        /** Whether feet at this position stand in it. */
        public boolean contains(double px, double py, double pz) {
            return placed() && Math.abs(px - x) <= radius && Math.abs(pz - z) <= radius && py >= y - 0.5 && py <= y + 2;
        }
    }

    /** Medal targets, in seconds. */
    public record Medals(double gold, double silver, double bronze) {
        public Medals() {
            this(0, 0, 0);
        }

        /** The best medal a time earns, or null. */
        public Medal of(long millis) {
            if (gold > 0 && millis <= gold * 1000) return Medal.GOLD;
            if (silver > 0 && millis <= silver * 1000) return Medal.SILVER;
            if (bronze > 0 && millis <= bronze * 1000) return Medal.BRONZE;
            return null;
        }
    }

    public enum Medal {GOLD, SILVER, BRONZE}
}
