package net.exylia.exyliaLobby.module.movement;

import net.exylia.lib.config.Comment;

import java.util.List;

/** {@code lang/<language>/modules/movement/messages.yml}. */
public record MovementMessages(
        @Comment("Pad editor: chat lines. %pad% is the pad id.")
        String padAdded,
        String padRemoved,
        String padChanged,
        String samePlace,
        @Comment("Prompts of the pad editor.")
        String promptPower,
        String promptVertical,
        String confirmRemove,
        @Comment("Pad editor: card texts.")
        String listTitle,
        String listKind,
        String rowSubtitle,
        String labelPlace,
        String labelPower,
        String labelVertical,
        String labelDirection,
        String directionLook,
        @Comment("The direction of a fixed pad. %yaw% is the angle in degrees.")
        String directionFixed,
        List<String> clicks,
        String addPadTitle,
        List<String> addPadText,
        String addPadClick,
        String addPadNext,
        String addAimedTitle,
        List<String> addAimedText,
        String addAimedClick,
        String addAimedNext,
        String backName,
        @Comment("What the setup wizards name on screen (plain text, no colours).")
        String wizardPad,
        String wizardAimed,
        String wizardAim,
        @Comment("A pad throws from every block it lists: the card line, its click, the wizard and its chat lines.")
        String labelBlocks,
        String clickBlocks,
        String wizardBlocks,
        String blockAdded,
        String blockRemoved) {

    public MovementMessages() {
        this("[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Launch pad {highlight}%pad%{letters} added.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Launch pad {highlight}%pad%{letters} removed.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Launch pad {highlight}%pad%{letters} updated.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}There is already a pad on that block.",
                "{letters}Horizontal speed of this pad (0.1 to 8).",
                "{letters}Upward speed of this pad (0.1 to 4).",
                "Remove launch pad {highlight}%pad%{letters}?",
                "Movement",
                "Launch pad",
                "Throws whoever steps on it",
                "Block",
                "Power",
                "Lift",
                "Direction",
                "{info}Where you look",
                "{info}Fixed at %yaw%°",
                List.of("{primary}{icon:click} Left-click {letters_black}· {letters}edit power",
                        "{primary}{icon:right_click} Right-click {letters_black}· {letters}edit lift",
                        "{primary}{icon:click} Shift-left-click {letters_black}· {letters}aim it, or free it",
                        "{error}{icon:right_click} Shift-right-click {letters_black}· {letters}remove",
                        "{letters_black}Removing asks you to confirm."),
                "Launch pad",
                List.of(" {letters}Click the block to turn into", " {letters}a pad; it throws players the", " {letters}way they look."),
                "Click to start",
                "Then edit its power here.",
                "Aimed pad",
                List.of(" {letters}Stand on the block, face where", " {letters}it should throw and", " {highlight}sneak-click{letters}."),
                "Click to start",
                "Always throws that way.",
                "Movement",
                "Launch pad",
                "Aimed pad",
                "Pad direction",
                "Blocks",
                "{primary}{icon:click} Q {letters_black}· {letters}add or remove blocks",
                "Pad blocks",
                "[sound:BLOCK_NOTE_BLOCK_PLING|0.6|1.6]%prefix% {letters}Block added to {highlight}%pad%{letters} ({highlight}%count%{letters}). Click another, or cancel to finish.",
                "[sound:BLOCK_NOTE_BLOCK_BASS|0.6|1.0]%prefix% {letters}Block removed from {highlight}%pad%{letters} ({highlight}%count%{letters}). Click another, or cancel to finish.");
    }
}
