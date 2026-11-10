package net.exylia.exyliaLobby.module.parkour;

import java.util.Locale;
import java.util.Optional;

/** The endless modes: each has its own board, named as the mode. */
enum EndlessMode {
    /** The score is how many jumps you make before falling. */
    ENDLESS,
    /** As many jumps as you can before the clock runs out. */
    SPRINT,
    /** The same path for every player today. */
    DAILY;

    /** The command argument and board name: {@code endless}, {@code sprint}, {@code daily}. */
    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<EndlessMode> parse(String typed) {
        for (EndlessMode mode : values()) {
            if (mode.id().equalsIgnoreCase(typed.trim())) return Optional.of(mode);
        }
        return Optional.empty();
    }

    boolean enabled(ParkourSettings.Modes modes) {
        return switch (this) {
            case ENDLESS -> modes.endless();
            case SPRINT -> modes.sprint();
            case DAILY -> modes.daily();
        };
    }

    String name(ParkourMessages text) {
        return switch (this) {
            case ENDLESS -> text.endlessName();
            case SPRINT -> text.sprintName();
            case DAILY -> text.dailyName();
        };
    }
}
