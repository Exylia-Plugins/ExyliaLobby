package net.exylia.exyliaLobby.module.protection;

import net.exylia.lib.config.Comment;

/** {@code lang/<language>/modules/protection/messages.yml}. */
public record ProtectionMessages(
        @Comment("/lobbyadmin build switched on or off")
        String buildOn,
        String buildOff,
        @Comment("Boss bar shown while build mode is on")
        String buildBar,
        @Comment("/lobbyadmin build used outside the lobby")
        String notInLobby) {

    public ProtectionMessages() {
        this("[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Build mode {success}on{letters}. The lobby protection ignores you.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Build mode {error}off{letters}. The lobby protection is back.",
                "{warning}&lBUILD MODE {letters_black}• {letters}Protection is off for you",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}Stand inside the lobby to use build mode.");
    }
}
