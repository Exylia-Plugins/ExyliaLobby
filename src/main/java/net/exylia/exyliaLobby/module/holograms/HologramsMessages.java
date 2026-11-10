package net.exylia.exyliaLobby.module.holograms;

import net.exylia.lib.config.Comment;

import java.util.List;

/** {@code lang/<language>/modules/holograms/messages.yml}. */
public record HologramsMessages(
        @Comment("Chat lines. %name% is the hologram's name.")
        String added,
        String removed,
        String changed,
        String moved,
        String noTarget,
        @Comment("The prompt of the text editor. Separate the lines with <nl>.")
        String promptLines,
        String confirmRemove,
        @Comment("Admin screen.")
        String listTitle,
        String listKind,
        String rowSubtitle,
        String labelPlace,
        String labelLines,
        List<String> clicks,
        String addHereTitle,
        List<String> addHereText,
        String addHereClick,
        String addHereNext,
        String addLookTitle,
        List<String> addLookText,
        String addLookClick,
        String addLookNext) {

    public HologramsMessages() {
        this("[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Hologram {highlight}%name%{letters} added.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Hologram {highlight}%name%{letters} removed.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Hologram {highlight}%name%{letters} updated.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Hologram {highlight}%name%{letters} moved to your position.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}Look at a block within 8 blocks.",
                "{letters}The lines of the hologram, separated by {highlight}<nl>{letters}.",
                "Remove hologram {highlight}%name%{letters}?",
                "Holograms",
                "Hologram",
                "Text hologram",
                "Place",
                "Lines",
                List.of("{primary}{icon:click} Left-click {letters_black}· {letters}edit the text",
                        "{primary}{icon:right_click} Right-click {letters_black}· {letters}move here",
                        "{error}{icon:right_click} Shift-right-click {letters_black}· {letters}remove",
                        "{letters_black}Removing asks you to confirm."),
                "At my feet",
                List.of(" {letters}Floats a hologram at the", " {letters}height of your eyes."),
                "Click to add",
                "Then edit its text here.",
                "Where I look",
                List.of(" {letters}Floats a hologram above", " {letters}the block you look at."),
                "Click to add",
                "Then edit its text here.");
    }
}
