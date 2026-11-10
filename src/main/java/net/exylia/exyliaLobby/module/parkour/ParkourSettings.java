package net.exylia.exyliaLobby.module.parkour;

import net.exylia.exyliaLobby.module.parkour.endless.SegmentType;
import net.exylia.exyliaLobby.module.parkour.endless.Tuning;
import net.exylia.lib.config.Comment;
import net.exylia.lib.config.Menu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** {@code modules/parkour/config.yml}: endless areas, modes, tuning, anti-cheat and rewards. */
public record ParkourSettings(
        @Comment("Labels of the player command: /parkour <course>, /parkour endless, /parkour leave. Empty removes it.")
        List<String> command,

        @Comment("Which endless modes players can start.")
        Modes modes,

        @Comment("Endless parkour: where it is drawn and how it plays.")
        Endless endless,

        @Comment("Hide players from everyone else while they run (needs PacketEvents).")
        boolean hideRunners,

        @Comment("Show a beam over the next checkpoint of a course (needs PacketEvents).")
        boolean checkpointBeacon,

        @Comment("Give runners the back to checkpoint, restart and leave items (needs PacketEvents).")
        boolean runItems,

        @Comment("Course runs fall back to their last checkpoint this many blocks under their lowest point. 0 never.")
        @Menu.Range(min = 0, max = 256, step = 2)
        int courseFallDepth,

        @Comment("What ends a run, or is refused while running.")
        AntiCheat antiCheat,

        @Comment("Leaderboards: how many places are kept and how often they are read again.")
        Tops tops,

        @Comment("When daily, weekly and monthly boards start over.")
        Periods periods,

        @Comment("Short sounds played to the runner only, as NAME|volume|pitch. Blank plays nothing.")
        Sounds sounds,

        @Comment("Leaderboard holograms by board (a course id, endless, sprint, daily, optionally -weekly/-monthly), placed by /lobbyadmin parkour board.")
        Map<String, BoardSpot> boards) {

    public ParkourSettings() {
        this(List.of("parkour", "pk"), new Modes(), new Endless(), true, true, true, 8, new AntiCheat(), new Tops(),
                new Periods(), new Sounds(), Map.of());
    }

    public ParkourSettings withEndless(Endless endless) {
        return new ParkourSettings(command, modes, endless, hideRunners, checkpointBeacon, runItems, courseFallDepth,
                antiCheat, tops, periods, sounds, boards);
    }

    public record Modes(
            @Comment("Endless: the score is how many jumps you make before falling.")
            boolean endless,
            @Comment("Sprint: as many jumps as you can before the clock runs out.")
            boolean sprint,
            @Comment("Daily: the same path for every player today, with its own board.")
            boolean daily,
            @Comment("How long a sprint lasts.")
            @Menu.Unit("s") @Menu.Range(min = 5, max = 600, step = 5)
            int sprintSeconds) {
        public Modes() {
            this(true, true, true, 60);
        }
    }

    public record Endless(
            @Comment("Boxes the path is drawn in, by name. A runner gets one at random; daily always uses the first.")
            @Comment("start is the block under the runner's feet at the start, heading where the path sets off:")
            @Comment("0 east (+x), 1 south (+z), 2 west (-x), 3 north (-z). Keep the box empty air.")
            Map<String, Area> areas,
            @Comment("Jumps drawn ahead of the runner (1-5).")
            int ahead,
            @Comment("Jumps kept behind the runner before they are cleared (0-3).")
            int behind,
            @Comment("The theme changes every this many jumps. 0 keeps one theme the whole run.")
            int themeEvery,
            @Comment("Scores that show a title and play the milestone sound.")
            List<Integer> milestones,
            @Comment("A run ends when the runner falls this many blocks under the jump they stand on.")
            int fallDepth,
            @Comment("The block marking where your best run ended. Blank draws none.")
            String bestMarker,
            @Comment("Rewards for reaching a score in one run, by score. Same format as course rewards.")
            Map<String, List<Object>> milestoneRewards,
            @Comment("How often each milestone reward can be earned again, per mode: ALL (once ever), MONTHLY,")
            @Comment("WEEKLY or DAILY. The daily mode always pays once a day, since its path is the same all day.")
            @Menu.Choices(value = {"ALL", "MONTHLY", "WEEKLY", "DAILY"})
            String milestonePeriod,
            @Comment("How the path is built.")
            PathTuning tuning) {
        public Endless() {
            this(Map.of(), 3, 2, 25, List.of(25, 50, 100, 150, 200), 4, "GOLD_BLOCK", Map.of(), "DAILY", new PathTuning());
        }

        /** {@link #milestonePeriod} read; daily when it is not a period. */
        Board.Period milestoneEvery() {
            try {
                return Board.Period.valueOf(milestonePeriod == null ? "" : milestonePeriod.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException bad) {
                return Board.Period.DAILY;
            }
        }
    }

    public record Area(String world, String min, String max, String start, int heading) {
        public Area() {
            this("world", "0 100 0", "64 140 64", "4 110 32", 0);
        }
    }

    public record PathTuning(
            @Comment("How often each segment is drawn against the others; 0 never.")
            Map<String, Integer> weights,
            @Comment("The score each segment starts appearing at.")
            Map<String, Integer> unlocks,
            @Comment("Score -> the hardest difficulty (1-5) drawn from that score on.")
            Map<String, Integer> curve,
            @Comment("A wide rest platform after about this many jumps. 0 never.")
            int restEvery,
            @Comment("The chance (0-1) a segment is a bonus one, one step harder and worth more.")
            double bonusChance,
            @Comment("The score bonus segments start at.")
            int bonusFrom,
            @Comment("What each jump of a bonus segment is worth.")
            int bonusPoints,
            @Comment("The chance (0-1) the path turns although there is room ahead.")
            double turnChance) {
        public PathTuning() {
            this(defaults(true), defaults(false), curveDefaults(), 20, 0.08, 30, 2, 0.3);
        }

        private static Map<String, Integer> defaults(boolean weights) {
            Map<String, Integer> values = new LinkedHashMap<>();
            for (SegmentType type : SegmentType.values()) {
                values.put(type.key(), weights ? type.defaultWeight() : type.defaultUnlock());
            }
            return values;
        }

        private static Map<String, Integer> curveDefaults() {
            Map<String, Integer> curve = new LinkedHashMap<>();
            Tuning.defaultCurve().forEach((score, level) -> curve.put(String.valueOf(score), level));
            return curve;
        }

        /** The generator's tuning; unknown keys are ignored, missing ones keep their default. */
        public Tuning tuning() {
            Tuning base = Tuning.defaults();
            Map<SegmentType, Integer> w = new java.util.EnumMap<>(base.weights());
            Map<SegmentType, Integer> u = new java.util.EnumMap<>(base.unlocks());
            for (SegmentType type : SegmentType.values()) {
                Integer weight = weights.get(type.key());
                if (weight != null) w.put(type, weight);
                Integer unlock = unlocks.get(type.key());
                if (unlock != null) u.put(type, unlock);
            }
            NavigableMap<Integer, Integer> steps = new TreeMap<>();
            for (Map.Entry<String, Integer> step : curve.entrySet()) {
                try {
                    steps.put(Integer.parseInt(step.getKey().trim()), step.getValue());
                } catch (NumberFormatException ignored) {
                    // a key that is not a score is skipped
                }
            }
            return new Tuning(w, u, steps.isEmpty() ? Tuning.defaultCurve() : steps, restEvery, bonusChance, bonusFrom, bonusPoints, turnChance);
        }
    }

    public record AntiCheat(
            @Comment("Flight is switched off for the run; flying anyway ends it.")
            boolean flight,
            @Comment("Gliding with an elytra is refused and ends the run.")
            boolean elytra,
            @Comment("A riptide trident ends the run.")
            boolean riptide,
            @Comment("Ender pearls and chorus fruit are refused and end the run.")
            boolean pearls,
            @Comment("Getting on a vehicle or mob is refused and ends the run.")
            boolean vehicles,
            @Comment("A game mode change ends the run.")
            boolean gameMode,
            @Comment("A teleport this module did not make ends the run.")
            boolean teleports,
            @Comment("Launches (launch pads, double jumps, knockback) are refused while running.")
            boolean launches,
            @Comment("The effects below are taken away for the run and refused while it lasts.")
            boolean effects,
            List<String> blockedEffects,
            @Comment("A course finished faster than its minimum time is refused and logged.")
            boolean minimumTime) {
        public AntiCheat() {
            this(true, true, true, true, true, true, true, true, true,
                    List.of("SPEED", "JUMP_BOOST", "LEVITATION", "SLOW_FALLING", "DOLPHINS_GRACE"), true);
        }
    }

    public record Tops(
            @Comment("Places kept per board (shown on leaderboards and placeholders).")
            int size,
            @Comment("Seconds between reading the boards again.")
            int refreshSeconds) {
        public Tops() {
            this(100, 60);
        }
    }

    public record Periods(
            @Comment("Time zone of the resets, such as Europe/Madrid. Blank is the server's own.")
            String timezone,
            @Comment("The hour (0-23) a new day starts at.")
            int resetHour,
            @Comment("The day a new week starts on: MONDAY ... SUNDAY.")
            String weekStart) {
        public Periods() {
            this("", 0, "MONDAY");
        }
    }

    public record BoardSpot(String world, double x, double y, double z) {
        public BoardSpot() {
            this("world", 0, 100, 0);
        }
    }

    public record Sounds(String start, String checkpoint, String finish, String record, String milestone, String fail) {
        public Sounds() {
            this("BLOCK_NOTE_BLOCK_HAT|0.7|1.4", "ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.4",
                    "BLOCK_NOTE_BLOCK_CHIME|0.7|1.2", "ENTITY_PLAYER_LEVELUP|0.5|1.6",
                    "BLOCK_NOTE_BLOCK_BELL|0.6|1.5", "ENTITY_VILLAGER_NO|0.6|1.0");
        }
    }
}
