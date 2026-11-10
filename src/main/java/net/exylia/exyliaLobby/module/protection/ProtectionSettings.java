package net.exylia.exyliaLobby.module.protection;

import net.exylia.lib.config.Comment;

import java.util.List;

/**
 * {@code modules/protection/config.yml}: every rule is a switch. A rule that is on is enforced inside the lobby
 * for everyone except an admin with build mode on ({@code /lobbyadmin build}).
 */
public record ProtectionSettings(
        @Comment("Cancel breaking blocks, buckets that pick fluids up and harvesting.")
        boolean blockBreak,
        @Comment("Cancel placing blocks, buckets, paintings, frames, fire by flint and steel and bone meal.")
        boolean blockPlace,
        @Comment("Cancel opening chests, barrels, furnaces, workbenches, anvils, beds, signs, cauldrons and the like.")
        boolean containers,
        @Comment("Cancel using doors, trapdoors and fence gates.")
        boolean doors,
        @Comment("Cancel pressing buttons and flipping levers.")
        boolean buttonsAndLevers,
        @Comment("Cancel stepping on pressure plates and tripwires.")
        boolean pressurePlates,
        @Comment("Cancel rotating, emptying and breaking item frames and paintings.")
        boolean frames,
        @Comment("Cancel moving, equipping and posing armor stands.")
        boolean armorStands,
        @Comment("Cancel trampling farmland.")
        boolean cropTrample,
        @Comment("Cancel dropping items. A player who dies in the lobby keeps their inventory.")
        boolean dropItems,
        @Comment("Cancel picking items up.")
        boolean pickupItems,
        @Comment("Cancel players hitting armor stands, animals and every other non-player entity.")
        boolean protectEntities,
        @Comment("Cancel the hunger bar dropping.")
        boolean hunger,
        @Comment("Cancel damage of these causes to players. PvP is let through only when the pvp module allows it.")
        @Comment("Causes: https://jd.papermc.io/paper/org/bukkit/event/entity/EntityDamageEvent.DamageCause.html")
        List<String> cancelledDamage,
        @Comment("Cancel monsters and animals that spawn on their own. Spawn eggs and plugins still work.")
        boolean mobSpawning,
        @Comment("Cancel explosions: no blocks destroyed and no damage.")
        boolean explosions,
        @Comment("Cancel fire spreading, burning blocks and fire from lava or lightning.")
        boolean fireSpread,
        @Comment("Cancel leaves decaying.")
        boolean leafDecay,
        @Comment("Cancel snow and ice forming.")
        boolean iceAndSnowForm,
        @Comment("Cancel snow and ice melting.")
        boolean iceAndSnowMelt,
        @Comment("Cancel mobs and falling blocks changing blocks: endermen, sheep eating grass, sand falling.")
        boolean entityChangeBlock,
        @Comment("Hide the 'has made the advancement' chat lines of players in the lobby.")
        boolean hideAdvancementMessages,
        @Comment("Hide the vanilla death message of players who die in the lobby.")
        boolean hideDeathMessages) {

    public ProtectionSettings() {
        this(true, true, true, true, true, true, true, true, true, true, true, true, true,
                List.of("CONTACT", "CRAMMING", "DROWNING", "DRYOUT", "ENTITY_ATTACK", "ENTITY_EXPLOSION", "ENTITY_SWEEP_ATTACK",
                        "FALL", "FALLING_BLOCK", "FIRE", "FIRE_TICK", "FLY_INTO_WALL", "FREEZE", "HOT_FLOOR", "LAVA",
                        "LIGHTNING", "MAGIC", "MELTING", "POISON", "PROJECTILE", "STARVATION", "SUFFOCATION", "THORNS",
                        "WITHER", "BLOCK_EXPLOSION"),
                true, true, true, true, true, true, true, true, true);
    }
}
