package net.exylia.exyliaLobby.module.spawn;

import net.exylia.lib.config.Comment;

/** {@code lang/<language>/modules/spawn/messages.yml}. */
public record SpawnMessages(
        @Comment("/spawn when no spawn point could be found")
        String noSpawn,
        @Comment("/spawn with a warmup. %seconds% is the wait.")
        String teleporting,
        String cancelled,
        @Comment("An admin set the spawn of a world. %world% is its name.")
        String spawnSet,
        @Comment("Shown on the Spawn screen: a spawn is set here, or not")
        String stateSet,
        String stateUnset,
        @Comment("Lobby areas editor: kinds and states shown on each card")
        String kindWorld,
        String kindBox,
        String stateLabelWorld,
        String stateLabelBox,
        String worldLoaded,
        String worldMissing,
        @Comment("Size of a box. %x%, %y% and %z% are its sides in blocks.")
        String boxSize,
        @Comment("Prompts of the areas editor")
        String selectBox,
        String promptName,
        String nameTaken,
        String confirmWorld,
        String confirmBox) {

    public SpawnMessages() {
        this("ENTITY_VILLAGER_NO %prefix% {error}No spawn is set. Ask an admin.",
                "%prefix% {letters}Teleporting in {highlight}%seconds%s{letters}. Stand still.",
                "ENTITY_VILLAGER_NO %prefix% {error}Teleport cancelled.",
                "ENTITY_EXPERIENCE_ORB_PICKUP %prefix% {letters}Spawn of {highlight}%world%{letters} set to your position.",
                "{success}{icon:on} Set",
                "{warning}{icon:warning} Not set",
                "Lobby world",
                "Lobby box",
                "State",
                "Size",
                "{success}{icon:on} Loaded",
                "{error}{icon:off} Not loaded",
                "{info}%x%×%y%×%z% blocks",
                "{letters}Select the box with the golden axe: {highlight}left-click{letters} a corner, {highlight}right-click{letters} the other, then {highlight}shift + left-click{letters}.",
                "{letters}Name this lobby box (letters, numbers, dashes).",
                "{error}That name is already used.",
                "Stop treating world {highlight}%area%{letters} as lobby?",
                "Remove lobby box {highlight}%area%{letters}?");
    }
}
