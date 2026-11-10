package net.exylia.exyliaLobby.module.chat;

import net.exylia.lib.config.Comment;

/** {@code lang/<language>/modules/chat/messages.yml}. */
public record ChatMessages(
        @Comment("A player tried to talk while the chat is locked")
        String locked,
        @Comment("A player talked too soon. %seconds% is the wait left.")
        String slowDenied,
        @Comment("Sent to the lobby when the chat is locked or unlocked. %player% is who did it.")
        String lockOn,
        String lockOff,
        @Comment("Sent to the lobby when slow mode changes. %seconds% is the new delay.")
        String slowOn,
        String slowOff,
        @Comment("Sent to the lobby after the chat was cleared")
        String cleared) {

    public ChatMessages() {
        this("ENTITY_VILLAGER_NO %prefix% {error}The chat is locked.",
                "ENTITY_VILLAGER_NO %prefix% {error}Wait {highlight}%seconds%s{error} before talking again.",
                "%prefix% {highlight}%player%{letters} locked the chat.",
                "%prefix% {highlight}%player%{letters} unlocked the chat.",
                "%prefix% {letters}Slow mode on: one message every {highlight}%seconds%s{letters}.",
                "%prefix% {letters}Slow mode off.",
                "%prefix% {letters}The chat was cleared.");
    }
}
