package net.exylia.exyliaLobby.module.parkour;

import net.exylia.lib.util.PeriodClock;

import java.util.Locale;

/**
 * A leaderboard: a course (fastest first) or an endless mode (highest first), over one period.
 *
 * <p>Written {@code <name>} for all time or {@code <name>-<period>}: {@code endless-weekly}, {@code tower-daily}.
 * The daily mode only has a daily board.
 */
public record Board(String name, Period period) {

    public static final String ENDLESS = "endless";
    public static final String SPRINT = "sprint";
    public static final String DAILY = "daily";

    /** How long a board lasts. */
    public enum Period {
        ALL, MONTHLY, WEEKLY, DAILY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Period next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /** The key rows of this period carry now: {@code all}, {@code m2026-11-01}, {@code w2026-11-09}, {@code d2026-11-10}. */
        public String key(PeriodClock clock) {
            return switch (this) {
                case ALL -> "all";
                case MONTHLY -> "m" + clock.key(PeriodClock.Period.MONTHLY);
                case WEEKLY -> "w" + clock.key(PeriodClock.Period.WEEKLY);
                case DAILY -> "d" + clock.key(PeriodClock.Period.DAILY);
            };
        }
    }

    public Board {
        if (DAILY.equals(name)) period = Period.DAILY;
    }

    public static boolean isMode(String name) {
        return ENDLESS.equals(name) || SPRINT.equals(name) || DAILY.equals(name);
    }

    public boolean isMode() {
        return isMode(name);
    }

    /** {@code endless-weekly} → endless, weekly; {@code tower} → tower, all time. */
    public static Board parse(String written) {
        String text = written.toLowerCase(Locale.ROOT);
        int dash = text.lastIndexOf('-');
        if (dash > 0) {
            String suffix = text.substring(dash + 1);
            for (Period period : Period.values()) {
                if (period.id().equals(suffix)) return new Board(text.substring(0, dash), period);
            }
        }
        return new Board(text, Period.ALL);
    }

    public String id() {
        return period == Period.ALL ? name : name + "-" + period.id();
    }

    /** The key of this board's cache and rows now. */
    public String key(PeriodClock clock) {
        return name + "|" + period.key(clock);
    }
}
