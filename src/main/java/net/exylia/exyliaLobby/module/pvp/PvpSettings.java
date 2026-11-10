package net.exylia.exyliaLobby.module.pvp;

import net.exylia.lib.config.Comment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code modules/pvp/config.yml}: how the lobby PvP works, the kit, the zones, the tops and the streak rewards. */
public record PvpSettings(
        @Comment("Labels of the player command that opens the kills top. Empty removes the command.")
        List<String> topCommand,

        @Comment("Permission needed to fight. Empty is everyone.")
        String permission,

        @Comment("Seconds a player holds the sword before the kit goes on.")
        int equipSeconds,

        @Comment("Seconds a player stays off the sword before the kit comes off.")
        int unequipSeconds,

        @Comment("Seconds a player stays in combat after a hit, given or taken. Nobody can put the sword away meanwhile.")
        int tagSeconds,

        @Comment("The hotbar slot (0-8) of the sword when the hotbar module is off. With it on, its swordSlot is used.")
        int swordSlot,

        @Comment("Material of the sword every player holds in the lobby. Holding it starts the fight countdown.")
        String swordMaterial,

        @Comment("Quitting while in combat counts as a death for the quitter and a kill for their opponent.")
        boolean quitCounts,

        @Comment("What a fighter wears and holds. Any item a menu accepts (a material, nexo:id...). Empty leaves a slot free.")
        Kit kit,

        @Comment("Where a fighter respawns after dying. Leave the world empty to respawn at the lobby spawn.")
        Point respawn,

        @Comment("Seconds between two refreshes of the tops (placeholders, menu and hologram).")
        int topRefreshSeconds,

        @Comment("Rows of the hologram board of the top.")
        int boardRows,

        @Comment("Where the top hologram stands (needs the holograms module). Leave the world empty for none.")
        Point board,

        @Comment("Rewards for reaching a kill streak, by streak. Each line is a console command (%player_name% works) or")
        @Comment("'item:MATERIAL:amount', which goes to the reward queue when the inventory is full. Empty is none.")
        Map<String, List<String>> streakRewards,

        @Comment("Boxes where the kit goes on when walking in and off when walking out. Placed from /lobbyadmin → Lobby PvP.")
        Map<String, Zone> zones) {

    public PvpSettings() {
        this(List.of("pvptop"), "", 5, 5, 10, 4, "WOODEN_SWORD", true, new Kit(), new Point(), 30, 5, new Point(),
                new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    /** The fighter's gear. */
    public record Kit(String helmet, String chestplate, String leggings, String boots, String weapon) {
        public Kit() {
            this("IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS", "IRON_SWORD");
        }
    }

    /** A place; an empty world means "not set". */
    public record Point(String world, double x, double y, double z, double yaw, double pitch) {
        public Point() {
            this("", 0, 64, 0, 0, 0);
        }

        public boolean set() {
            return !world.isBlank();
        }
    }

    /** A PvP zone: the box between two corners. */
    public record Zone(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        public Zone() {
            this("world", 0, 0, 0, 0, 0, 0);
        }
    }
}
