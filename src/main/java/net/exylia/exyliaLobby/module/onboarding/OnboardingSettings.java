package net.exylia.exyliaLobby.module.onboarding;

import net.exylia.lib.config.Comment;

import java.time.Duration;
import java.util.List;

/** {@code modules/onboarding/config.yml}: how first-join players are handed to ExyliaJourney. */
public record OnboardingSettings(
        @Comment("The id of the ExyliaJourney journey that welcomes new players.")
        @Comment("Leave that journey's own FIRST_JOIN trigger off, or players start it twice.")
        String journey,

        @Comment("Only players joining for the first time. Off hands every joining player over; the journey's own")
        @Comment("rules (already completed, cooldown, requirements) still apply.")
        boolean firstJoinOnly,

        @Comment("How long after joining the journey starts, so the loading screen is gone.")
        Duration delay,

        @Comment("Commands run instead when ExyliaJourney is not installed (console: ..., player: ..., [ACTION] ...).")
        @Comment("%player_name% works. Empty does nothing without ExyliaJourney.")
        List<String> fallbackCommands) {

    public OnboardingSettings() {
        this("welcome", true, Duration.ofSeconds(3), List.of());
    }
}
