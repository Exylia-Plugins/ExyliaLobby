package net.exylia.exyliaLobby.module.parkour;

import java.util.List;
import java.util.Locale;

/** Run times: counted in server ticks, shown to the millisecond, and compared split by split. */
public final class Times {

    public static final long MILLIS_PER_TICK = 50L;

    private Times() {
    }

    public static long millis(long ticks) {
        return ticks * MILLIS_PER_TICK;
    }

    /** {@code 12.350}, {@code 1:02.350}, {@code 1:02:03.350}. */
    public static String format(long millis) {
        long ms = Math.max(0, millis);
        long hours = ms / 3_600_000;
        long minutes = ms / 60_000 % 60;
        long seconds = ms / 1000 % 60;
        long rest = ms % 1000;
        if (hours > 0) return String.format(Locale.ROOT, "%d:%02d:%02d.%03d", hours, minutes, seconds, rest);
        if (minutes > 0) return String.format(Locale.ROOT, "%d:%02d.%03d", minutes, seconds, rest);
        return String.format(Locale.ROOT, "%d.%03d", seconds, rest);
    }

    /** A difference with its sign: {@code -1.200} ahead, {@code +0.350} behind. */
    public static String delta(long millis) {
        return (millis < 0 ? "-" : "+") + format(Math.abs(millis));
    }

    /**
     * How a split compares with the same split of the best run: negative is ahead. {@code null} when there is
     * no best run, or it has no such split (the course gained a checkpoint since).
     */
    public static Long compare(List<Long> best, int split, long millis) {
        if (best == null || split < 0 || split >= best.size()) return null;
        Long previous = best.get(split);
        return previous == null ? null : millis - previous;
    }
}
