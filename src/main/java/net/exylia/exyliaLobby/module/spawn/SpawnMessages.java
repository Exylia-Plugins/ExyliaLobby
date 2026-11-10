package net.exylia.exyliaLobby.module.spawn;

import net.exylia.lib.config.Comment;

/** {@code lang/<language>/modules/spawn/messages.yml}. */
public record SpawnMessages(
        @Comment("/spawn when no spawn point could be found")
        String noSpawn,
        @Comment("/spawn with a warmup. %time% is the wait (3s).")
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
        @Comment("What the setup wizards name on screen (plain text, no colours): the spawn of a world, a lobby box")
        String wizardSpawn,
        String wizardBox,
        @Comment("Prompts of the areas editor")
        String promptName,
        String nameTaken,
        String confirmWorld,
        String confirmBox) {

    public SpawnMessages() {
        this("[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}No spawn is set. Ask an admin.",
                "%prefix% {letters}Teleporting in {highlight}%time%{letters}. Stand still.",
                "[sound:ENTITY_VILLAGER_NO|0.7|1.0]%prefix% {error}Teleport cancelled.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2]%prefix% {letters}Spawn of {highlight}%world%{letters} set where you stood.",
                "{success}{icon:on} Set",
                "{warning}{icon:warning} Not set",
                "Lobby world",
                "Lobby box",
                "State",
                "Size",
                "{success}{icon:on} Loaded",
                "{error}{icon:off} Not loaded",
                "{info}%x%×%y%×%z% blocks",
                "Lobby spawn",
                "Lobby box",
                "{letters}Name this lobby box (letters, numbers, dashes).",
                "{error}That name is already used.",
                "Stop treating world {highlight}%area%{letters} as lobby?",
                "Remove lobby box {highlight}%area%{letters}?");
    }
}
