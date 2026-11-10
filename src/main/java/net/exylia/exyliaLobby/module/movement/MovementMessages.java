package net.exylia.exyliaLobby.module.movement;

import net.exylia.lib.config.Comment;

import java.util.List;

/** {@code lang/<language>/modules/movement/messages.yml}. */
public record MovementMessages(
        @Comment("Pad editor: chat lines. %pad% is the pad id.")
        String padAdded,
        String padRemoved,
        String padChanged,
        String noTarget,
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
        String addHereTitle,
        List<String> addHereText,
        String addHereClick,
        String addHereNext,
        String addLookTitle,
        List<String> addLookText,
        String addLookClick,
        String addLookNext,
        String backName) {

    public MovementMessages() {
        this("ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Launch pad {highlight}%pad%{letters} added.",
                "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Launch pad {highlight}%pad%{letters} removed.",
                "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Launch pad {highlight}%pad%{letters} updated.",
                "ENTITY_VILLAGER_NO %prefix% {error}Look at a block within 8 blocks.",
                "ENTITY_VILLAGER_NO %prefix% {error}There is already a pad on that block.",
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
                        "{primary}{icon:click} Shift-left-click {letters_black}· {letters}fix or free direction",
                        "{error}{icon:right_click} Shift-right-click {letters_black}· {letters}remove",
                        "{letters_black}Removing asks you to confirm."),
                "At my feet",
                List.of(" {letters}Makes the block you stand", " {letters}on a launch pad."),
                "Click to add",
                "Then edit its power here.",
                "Where I look",
                List.of(" {letters}Makes the block you look", " {letters}at a launch pad."),
                "Click to add",
                "Then edit its power here.",
                "Movement");
    }
}
