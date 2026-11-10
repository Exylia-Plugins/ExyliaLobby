package net.exylia.exyliaLobby.module.movement;

import net.exylia.lib.config.Comment;
import net.exylia.lib.effect.EffectConfig;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@code modules/movement/config.yml}: double jump and launch pads. */
public record MovementSettings(
        @Comment("Press jump again in the air to jump a second time.")
        DoubleJump doubleJump,

        @Comment("Pads that throw whoever steps on them. Placed in game from /lobbyadmin → Movement.")
        Pads pads) {

    public MovementSettings() {
        this(new DoubleJump(), new Pads());
    }

    public record DoubleJump(
            @Comment("Turn the double jump on.")
            boolean enabled,
            @Comment("Horizontal push, in the direction the player looks.")
            double forward,
            @Comment("Upward push.")
            double vertical,
            @Comment("Time before the same player can double jump again (re-armed on landing).")
            Duration cooldown,
            @Comment("Permission needed to double jump. Empty is everyone.")
            String permission,
            @Comment("What the player hears and sees on the jump.")
            EffectConfig effect) {

        public DoubleJump() {
            this(true, 0.9, 0.75, Duration.ofMillis(400), "",
                    new EffectConfig(new EffectConfig.Title(), new EffectConfig.ActionBar(), new EffectConfig.BossBar(),
                            new EffectConfig.Sound("ENTITY_BAT_TAKEOFF", 0.4, 1.4, "MASTER"),
                            new EffectConfig.Particle(), new EffectConfig.Firework()));
        }
    }

    public record Pads(
            @Comment("Turn the launch pads on.")
            boolean enabled,
            @Comment("Time before the same player can be thrown again, shared by every pad.")
            Duration cooldown,
            @Comment("What the player hears and sees when thrown.")
            EffectConfig effect,
            @Comment("The pads, by id. Edit them in game; the block a pad sits on is world, x, y, z.")
            Map<String, Pad> list) {

        public Pads() {
            this(true, Duration.ofMillis(500),
                    new EffectConfig(new EffectConfig.Title(), new EffectConfig.ActionBar(), new EffectConfig.BossBar(),
                            new EffectConfig.Sound("ENTITY_BAT_TAKEOFF", 0.4, 0.8, "MASTER"),
                            new EffectConfig.Particle(), new EffectConfig.Firework()),
                    new LinkedHashMap<>());
        }
    }

    /**
     * One pad.
     *
     * @param power    horizontal speed
     * @param vertical upward speed
     * @param fixed    throw in {@code yaw} (Minecraft degrees: 0 south, 90 west, 180 north, -90 east) instead of where the player looks
     */
    public record Pad(String world, int x, int y, int z, double power, double vertical, boolean fixed, double yaw) {
        public Pad() {
            this("world", 0, 64, 0, 1.6, 0.9, false, 0);
        }
    }
}
