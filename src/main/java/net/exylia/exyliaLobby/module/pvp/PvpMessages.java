package net.exylia.exyliaLobby.module.pvp;

import net.exylia.lib.config.Comment;

import java.util.List;

/** {@code lang/<language>/modules/pvp/messages.yml}. */
public record PvpMessages(
        @Comment("Boss bars while the kit goes on or off. %seconds% is the time left.")
        String equipBar,
        String unequipBar,
        @Comment("Chat lines. %seconds% is the combat time left, %victim%, %killer% and %streak% name the fight.")
        String equipped,
        String unequipped,
        String tagged,
        String noPermission,
        String busyElsewhere,
        String killer,
        String victim,
        String quitKiller,
        String streakReward,
        @Comment("The sword every player holds in the lobby: its name and card lore.")
        String swordName,
        List<String> swordLore,
        @Comment("The hologram board of the top. %rank%, %name% and %kills% fill each row.")
        String boardTitle,
        String boardRow,
        String boardEmpty,
        @Comment("What a top placeholder answers for a rank nobody holds yet.")
        String topEmpty,
        @Comment("Admin screen: the zones.")
        String listTitle,
        String listKind,
        String subtitleZone,
        String labelPlace,
        String labelSize,
        List<String> clicksZone,
        String addZoneTitle,
        List<String> addZoneText,
        String addZoneClick,
        String addZoneNext,
        String addHereTitle,
        List<String> addHereText,
        String addHereClick,
        String addHereNext,
        String selectZone,
        String added,
        String removed,
        String confirmRemove,
        String spawnSet,
        String boardSet) {

    public PvpMessages() {
        this("{success}&lEQUIPPING {letters_black}» {letters}Keep holding your sword {letters_black}({info}%seconds%s{letters_black})",
                "{error}&lSTANDING DOWN {letters_black}» {letters}Keep your sword away {letters_black}({info}%seconds%s{letters_black})",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {success}You are in the fight.{letters} Only players with their sword out can hit you.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}You are out of the fight and your things are back.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}You are in combat for {highlight}%seconds%s{error}. Keep your sword out.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}You cannot fight in the lobby.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {warning}You cannot fight while you are doing something else.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}You killed {highlight}%victim%{letters}. Streak {accent}%streak%{letters}.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {letters}You were killed by {highlight}%killer%{letters}.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {highlight}%victim%{letters} left in combat. Streak {accent}%streak%{letters}.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {success}Streak reward{letters} for {accent}%streak%{letters} kills.",
                "{letters}PvP {letters_black}▸ <gradient:{error}:{error_light}>Sword</gradient>",
                List.of("{letters_black}Lobby PvP", "", "{error}✎ Information", " {letters}Hold it to fight players",
                        " {letters}who did the same.", "", "{error}{icon:click} Hold to fight", "{letters_black}Your kit goes on after a short wait."),
                "{primary}&lKILLS TOP",
                "{highlight}%rank%. {letters}%name% {letters_black}· {info}%kills%",
                "{muted}Nobody has won a fight yet.",
                "-",
                "Lobby PvP",
                "PvP zone",
                "PvP zone",
                "Place",
                "Size",
                List.of("{primary}{icon:click} Left-click {letters_black}· {letters}show its outline",
                        "{error}{icon:right_click} Shift-right-click {letters_black}· {letters}remove",
                        "{letters_black}Removing asks you to confirm."),
                "Zone",
                List.of(" {letters}Select a box; the kit goes on", " {letters}when players walk into it."),
                "Click to select",
                "Walking out takes the kit off.",
                "Around you",
                List.of(" {letters}A box of 13x13 blocks", " {letters}centred on where you stand."),
                "Click to add",
                "Resize it later in the config.",
                "{letters}Select the zone with the golden axe: {highlight}left-click{letters} a corner, {highlight}right-click{letters} the other, then {highlight}shift + left-click{letters}.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {highlight}%what%{letters} added.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {highlight}%what%{letters} removed.",
                "Remove {highlight}%what%{letters}?",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}PvP respawn set to your position.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Top hologram placed at your position.");
    }
}
