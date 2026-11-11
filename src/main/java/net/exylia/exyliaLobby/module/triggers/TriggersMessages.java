package net.exylia.exyliaLobby.module.triggers;

import net.exylia.lib.config.Comment;

import java.util.List;

/** {@code lang/<language>/modules/triggers/messages.yml}. */
public record TriggersMessages(
        @Comment("Trigger editor: chat lines. %trigger% is the trigger id.")
        String added,
        String removed,
        String changed,
        String samePlace,
        @Comment("Prompts of the trigger editor.")
        String promptAction,
        String promptCooldown,
        String confirmRemove,
        @Comment("Trigger editor: card texts.")
        String listTitle,
        String listKind,
        String blockSubtitle,
        String areaSubtitle,
        String labelPlace,
        String labelActions,
        String labelCooldown,
        String labelPermission,
        @Comment("The actions stat of a trigger with none yet: it does nothing until one is added.")
        String noActions,
        String everyone,
        List<String> clicks,
        String addBlockTitle,
        List<String> addBlockText,
        String addBlockClick,
        String addBlockNext,
        String addAreaTitle,
        List<String> addAreaText,
        String addAreaClick,
        String addAreaNext,
        @Comment("What the setup wizards name on screen (plain text, no colours).")
        String wizardBlock,
        String wizardArea) {

    public TriggersMessages() {
        this("[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Trigger {highlight}%trigger%{letters} added. Give it an action next.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Trigger {highlight}%trigger%{letters} removed.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Trigger {highlight}%trigger%{letters} updated.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}There is already a trigger on that block.",
                "{letters}An action line, such as {highlight}console: say hi{letters} or {highlight}[SERVER] games{letters}.",
                "{letters}Time before the same player sets it off again.",
                "Remove trigger {highlight}%trigger%{letters}?",
                "Triggers",
                "Trigger",
                "Runs when clicked or stepped on",
                "Runs on entering and leaving",
                "Place",
                "Actions",
                "Cooldown",
                "Permission",
                "{warning}{icon:warning} None yet",
                "{info}Everyone",
                List.of("{primary}{icon:click} Left-click {letters_black}· {letters}add an action",
                        "{primary}{icon:right_click} Right-click {letters_black}· {letters}edit cooldown",
                        "{primary}{icon:click} Shift-left-click {letters_black}· {letters}drop the last action",
                        "{error}{icon:right_click} Shift-right-click {letters_black}· {letters}remove",
                        "{letters_black}Removing asks you to confirm."),
                "Block trigger",
                List.of(" {letters}Click a button, lever, plate", " {letters}or any block; it runs its", " {letters}actions when used."),
                "Click to start",
                "Then add its actions here.",
                "Area trigger",
                List.of(" {letters}Select an area; it runs its", " {letters}actions when a player", " {letters}walks in."),
                "Click to start",
                "Then add its actions here.",
                "Trigger block",
                "Trigger area");
    }
}
