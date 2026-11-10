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
        String addSpotTitle,
        List<String> addSpotText,
        String addSpotClick,
        String addSpotNext,
        String addBlockTitle,
        List<String> addBlockText,
        String addBlockClick,
        String addBlockNext,
        @Comment("What the setup wizards name on screen (plain text, no colours).")
        String wizardSpot,
        String wizardBlock) {

    public HologramsMessages() {
        this("[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Hologram {highlight}%name%{letters} added.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Hologram {highlight}%name%{letters} removed.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Hologram {highlight}%name%{letters} updated.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Hologram {highlight}%name%{letters} moved.",
                "{letters}The lines of the hologram, separated by {highlight}<nl>{letters}.",
                "Remove hologram {highlight}%name%{letters}?",
                "Holograms",
                "Hologram",
                "Text hologram",
                "Place",
                "Lines",
                List.of("{primary}{icon:click} Left-click {letters_black}· {letters}edit the text",
                        "{primary}{icon:right_click} Right-click {letters_black}· {letters}move it",
                        "{error}{icon:right_click} Shift-right-click {letters_black}· {letters}remove",
                        "{letters_black}Removing asks you to confirm."),
                "Free spot",
                List.of(" {letters}Walk to the spot and", " {highlight}sneak-click{letters}; it floats at", " {letters}the height of your eyes."),
                "Click to start",
                "Then edit its text here.",
                "Over a block",
                List.of(" {letters}Click a block; the hologram", " {letters}floats above it."),
                "Click to start",
                "Then edit its text here.",
                "Hologram",
                "Hologram block");
    }
}
