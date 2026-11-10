package net.exylia.exyliaLobby.module.visibility;

import net.exylia.lib.config.Comment;

import java.util.List;

/** {@code lang/<language>/modules/visibility/messages.yml}. */
public record VisibilityMessages(
        @Comment("The player switched what they see. %state% is the new mode, coloured.")
        String changed,
        @Comment("Pressed again too soon. %time% is the time left (0.4s).")
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
        this("[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}You now see: %state%{letters}.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {warning}Wait {highlight}%time%{warning} before switching again.",
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
