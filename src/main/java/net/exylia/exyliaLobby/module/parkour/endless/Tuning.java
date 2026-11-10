package net.exylia.exyliaLobby.module.parkour.endless;

import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * How an endless path is drawn, from the settings.
 *
 * @param weights     how often each segment is drawn; 0 never
 * @param unlocks     the score each segment unlocks at
 * @param curve       score → the hardest difficulty (1-5) drawn from that score on
 * @param restEvery   a wide rest platform after about this many jumps; 0 never
 * @param bonusChance the chance a segment is a bonus one, one difficulty harder
 * @param bonusFrom   the score bonus segments start at
 * @param bonusPoints what each jump of a bonus segment is worth
 * @param turnChance  the chance a segment turns although there is room ahead
 */
public record Tuning(Map<SegmentType, Integer> weights, Map<SegmentType, Integer> unlocks,
                     NavigableMap<Integer, Integer> curve, int restEvery, double bonusChance, int bonusFrom,
                     int bonusPoints, double turnChance) {

    /** Jumps kept in the generator's memory: everything a player can still see must be in here. */
    static final int MEMORY = 32;
    /**
     * What is never forgotten, even when the path is walled in: the jumps behind a runner (3 at most), the one
     * they stand on, those shown ahead (5 at most) and the rest of the segment being drawn (8 at most).
     */
    static final int MIN_MEMORY = 18;

    public static Tuning defaults() {
        Map<SegmentType, Integer> weights = new EnumMap<>(SegmentType.class);
        Map<SegmentType, Integer> unlocks = new EnumMap<>(SegmentType.class);
        for (SegmentType type : SegmentType.values()) {
            weights.put(type, type.defaultWeight());
            unlocks.put(type, type.defaultUnlock());
        }
        return new Tuning(weights, unlocks, defaultCurve(), 20, 0.08, 30, 2, 0.3);
    }

    public static NavigableMap<Integer, Integer> defaultCurve() {
        NavigableMap<Integer, Integer> curve = new TreeMap<>();
        curve.put(0, 1);
        curve.put(15, 2);
        curve.put(40, 3);
        curve.put(80, 4);
        curve.put(150, 5);
        return curve;
    }

    /** The hardest difficulty drawn at a score. */
    public int maxDifficulty(int score) {
        Map.Entry<Integer, Integer> step = curve.floorEntry(score);
        return step == null ? 1 : Math.max(1, Math.min(5, step.getValue()));
    }

    int weight(SegmentType type) {
        return Math.max(0, weights.getOrDefault(type, type.defaultWeight()));
    }

    int unlock(SegmentType type) {
        return unlocks.getOrDefault(type, type.defaultUnlock());
    }
}
