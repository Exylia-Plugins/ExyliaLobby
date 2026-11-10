package net.exylia.exyliaLobby.module.parkour;

import net.exylia.exyliaLobby.module.parkour.endless.Role;
import net.exylia.lib.config.Comment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code modules/parkour/themes.yml}: the block palettes endless paths are drawn in.
 *
 * <p>Ice, slime, soul sand and honey keep their own block in every theme: their physics is the jump.
 */
public record ParkourThemes(
        @Comment("Themes by id. A run moves through the enabled ones in order, starting at a random one.")
        @Comment("Blocks are block data, such as STONE_BRICKS or oak_fence. post is a fence or wall a runner lands on,")
        @Comment("ceiling the slab over a head-hitter, ladder a ladder or vine, wall what a ladder or neo jump is built on.")
        Map<String, Theme> themes) {

    public ParkourThemes() {
        this(defaults());
    }

    public record Theme(
            @Comment("Shown in the title when the path changes theme; colours allowed.")
            String name,
            boolean enabled,
            String floor, String bonus, String rest, String post, String ceiling, String ladder, String wall,
            String pillar) {

        public Theme() {
            this("Stone Ruins", true, "STONE_BRICKS", "CHISELED_STONE_BRICKS", "POLISHED_ANDESITE", "STONE_BRICK_WALL",
                    "STONE_BRICK_SLAB[type=top]", "LADDER", "CRACKED_STONE_BRICKS", "MOSSY_STONE_BRICKS");
        }

        /** The block for a role; null for the functional ones every theme shares. */
        public String block(Role role) {
            return switch (role) {
                case FLOOR -> floor;
                case BONUS -> bonus;
                case REST -> rest;
                case POST -> post;
                case CEILING -> ceiling;
                case LADDER -> ladder;
                case WALL -> wall;
                case PILLAR -> pillar;
                case ICE -> "PACKED_ICE";
                case SLIME -> "SLIME_BLOCK";
                case SOUL_SAND -> "SOUL_SAND";
                case HONEY -> "HONEY_BLOCK";
            };
        }
    }

    private static Map<String, Theme> defaults() {
        Map<String, Theme> themes = new LinkedHashMap<>();
        themes.put("stone-ruins", new Theme());
        themes.put("nether", new Theme("<gradient:{error}:{error_light}>Nether</gradient>", true, "NETHER_BRICKS",
                "GILDED_BLACKSTONE", "POLISHED_BLACKSTONE", "NETHER_BRICK_FENCE", "NETHER_BRICK_SLAB[type=top]",
                "TWISTING_VINES", "RED_NETHER_BRICKS", "BASALT"));
        themes.put("end", new Theme("<gradient:{secondary}:{secondary_light}>The End</gradient>", true, "END_STONE_BRICKS",
                "PURPUR_PILLAR", "PURPUR_BLOCK", "END_STONE_BRICK_WALL", "PURPUR_SLAB[type=top]", "LADDER",
                "END_STONE", "OBSIDIAN"));
        themes.put("frozen-peaks", new Theme("<gradient:{info}:{info_light}>Frozen Peaks</gradient>", true, "SNOW_BLOCK",
                "BLUE_ICE", "CALCITE", "SPRUCE_FENCE", "SPRUCE_SLAB[type=top]", "LADDER", "PACKED_ICE",
                "SPRUCE_LOG"));
        themes.put("ocean", new Theme("<gradient:{info}:{success_light}>Ocean</gradient>", true, "PRISMARINE_BRICKS",
                "SEA_LANTERN", "DARK_PRISMARINE", "PRISMARINE_WALL", "PRISMARINE_BRICK_SLAB[type=top]", "LADDER",
                "PRISMARINE", "DARK_PRISMARINE"));
        themes.put("sky-gardens", new Theme("<gradient:{success}:{success_light}>Sky Gardens</gradient>", true,
                "MOSS_BLOCK", "FLOWERING_AZALEA_LEAVES", "GRASS_BLOCK", "OAK_FENCE", "OAK_SLAB[type=top]", "VINE",
                "OAK_LEAVES", "OAK_LOG"));
        themes.put("deep-dark", new Theme("<gradient:{primary}:{secondary_light}>Deep Dark</gradient>", true,
                "SCULK", "SCULK_CATALYST", "DEEPSLATE_TILES", "DEEPSLATE_TILE_WALL", "DEEPSLATE_TILE_SLAB[type=top]",
                "LADDER", "REINFORCED_DEEPSLATE", "POLISHED_DEEPSLATE"));
        return themes;
    }

    /**
     * The theme of a jump: the enabled themes in order, one block of {@code every} jumps each, starting at
     * {@code offset}. Always the first when {@code every} is 0. Pure, so the daily path is the same for all.
     */
    public static int themeAt(int jump, int every, int count, int offset) {
        if (count <= 0) return 0;
        int step = every <= 0 ? 0 : jump / every;
        return Math.floorMod(offset + step, count);
    }
}
