package net.exylia.exyliaLobby.module.visibility;

import net.exylia.lib.config.Comment;

import java.util.List;

/** {@code lang/<language>/modules/visibility/messages.yml}. */
public record VisibilityMessages(
        @Comment("The player switched what they see. %state% is the new mode, coloured.")
        String changed,
        @Comment("Pressed again too soon. %seconds% is the time left.")
        String cooling,
        @Comment("The mode as a coloured word, for the hotbar button and chat: %lobby_visibility_state%.")
        String stateEveryone,
        String stateFriends,
        String stateNobody,
        @Comment("The /settings entry: category, name, description and the three choices (plain text).")
        String category,
        List<String> categoryText,
        String name,
        List<String> description,
        String labelEveryone,
        String labelFriends,
        String labelNobody,
        @Comment("Shown on the /settings entry when hiding players is not possible here.")
        String unavailable) {

    public VisibilityMessages() {
        this("ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}You now see: %state%{letters}.",
                "ENTITY_VILLAGER_NO %prefix% {warning}Wait {highlight}%seconds%s{warning} before switching again.",
                "{success}Everyone",
                "{info}Friends and staff",
                "{error}Nobody",
                "Lobby",
                List.of("How the lobby looks to you."),
                "Player visibility",
                List.of("Who you see while you are in", "the lobby. Staff are always seen."),
                "Everyone",
                "Friends and staff",
                "Nobody",
                "Needs PacketEvents on this server.");
    }
}
