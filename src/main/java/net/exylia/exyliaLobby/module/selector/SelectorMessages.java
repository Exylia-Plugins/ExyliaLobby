package net.exylia.exyliaLobby.module.selector;

import net.exylia.lib.config.Comment;

import java.util.List;

/** {@code lang/<language>/modules/selector/messages.yml}. */
public record SelectorMessages(
        @Comment("Chat lines. %server% is the server name from the catalogue (or its id).")
        String connecting,
        String offline,
        String noPermission,
        String here,
        String unreachable,
        String unknownServer,
        @Comment("Lines of a server card. The status words and the click prompt (two lines, joined by <nl>).")
        String statusOnline,
        String statusOffline,
        String statusUnknown,
        String clickJoin,
        String clickLocked,
        String clickOffline,
        String clickHere,
        String noLobbies,
        String lobbiesNext,
        @Comment("Admin screen: portals.")
        String listTitle,
        String listKind,
        String subtitlePortal,
        String labelServer,
        String labelPlace,
        String labelSize,
        String serverMissing,
        List<String> clicksPortal,
        String addPortalTitle,
        List<String> addPortalText,
        String addPortalClick,
        String addPortalNext,
        String promptServer,
        String noServers,
        String added,
        String removed,
        String changed,
        String confirmRemove,
        @Comment("What the setup wizard names on screen (plain text, no colours).")
        String wizardPortal) {

    public SelectorMessages() {
        this("[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Connecting to %server%{letters}...",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}%server%{error} is offline right now.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}You cannot join %server%{error}.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {warning}You are already on %server%{warning}.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}The network could not be reached. Try again in a moment.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}That server is not set up.",
                "{success}Online",
                "{error}Offline",
                "{muted}No data",
                "{info}{icon:click} Click to join<nl>{letters_black}You are moved at once.",
                "{warning}{icon:locked} Locked<nl>{letters_black}You need a rank to join.",
                "{error}{icon:off} Offline<nl>{letters_black}Try again in a moment.",
                "{success}{icon:on} You are here<nl>{letters_black}Pick another lobby to switch.",
                "{warning}{icon:warning} No lobbies set<nl>{letters_black}Ask an admin to list them.",
                "{info}{icon:click} Click to open<nl>{letters_black}Pick the lobby to play on.",
                "Selector",
                "Server portal",
                "Server portal",
                "Server",
                "Place",
                "Size",
                "{error}Not in the catalogue",
                List.of("{primary}{icon:click} Left-click {letters_black}· {letters}change server",
                        "{primary}{icon:right_click} Right-click {letters_black}· {letters}show its outline",
                        "{error}{icon:right_click} Shift-right-click {letters_black}· {letters}remove",
                        "{letters_black}Removing asks you to confirm."),
                "Portal",
                List.of(" {letters}Select a box; walking into", " {letters}it sends players away."),
                "Click to start",
                "You pick its server after.",
                "{letters}Which server does it send players to?",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}Add a server to the catalogue first (modules/selector/config.yml).",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {highlight}%what%{letters} added.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {highlight}%what%{letters} removed.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {highlight}%what%{letters} now sends players to %server%{letters}.",
                "Remove {highlight}%what%{letters}?",
                "Portal");
    }
}
