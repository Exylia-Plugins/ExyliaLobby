package net.exylia.exyliaLobby.module.movement;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.Menu;
import net.exylia.lib.effect.EffectConfig;

import net.exylia.lib.config.MutableConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
            @Menu.Range(min = 0, max = 5, step = 0.1)
            double forward,
            @Comment("Upward push.")
            @Menu.Range(min = 0, max = 5, step = 0.1)
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
            @Comment("The pads, by id. Edit them in game. One pad throws the same way from every block it lists.")
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
     * One pad: how it throws, and every block that throws that way.
     *
     * @param blocks   {@code world x y z} of each block
     * @param power    horizontal speed
     * @param vertical upward speed
     * @param fixed    throw in {@code yaw} (Minecraft degrees: 0 south, 90 west, 180 north, -90 east) instead of where the player looks
     */
    public record Pad(List<String> blocks, double power, double vertical, boolean fixed, double yaw) {
        public Pad() {
            this(new ArrayList<>(), 1.6, 0.9, false, 0);
        }

        public Pad withBlocks(List<String> blocks) {
            return new Pad(blocks, power, vertical, fixed, yaw);
        }

        public Pad withPower(double power) {
            return new Pad(blocks, power, vertical, fixed, yaw);
        }

        public Pad withVertical(double vertical) {
            return new Pad(blocks, power, vertical, fixed, yaw);
        }

        public Pad aimed(boolean fixed, double yaw) {
            return new Pad(blocks, power, vertical, fixed, yaw);
        }
    }

    /** {@code world x y z}, the way a pad lists a block. */
    public static String block(String world, int x, int y, int z) {
        return world + " " + x + " " + y + " " + z;
    }

    /**
     * Version 1 had one block per pad ({@code world, x, y, z}): each becomes a {@code blocks} list, and pads that
     * throw the same way are merged into the first of them.
     */
    static void oneBlockPerPad(MutableConfig data) {
        if (!(data.get("pads.list") instanceof ConfigurationSection list)) return;
        Map<String, String> firstBySetup = new LinkedHashMap<>();
        for (String id : List.copyOf(list.getKeys(false))) {
            ConfigurationSection pad = list.getConfigurationSection(id);
            if (pad == null || !pad.contains("world")) continue;
            String block = block(pad.getString("world"), pad.getInt("x"), pad.getInt("y"), pad.getInt("z"));
            String setup = pad.getDouble("power", 1.6) + "/" + pad.getDouble("vertical", 0.9) + "/"
                    + pad.getBoolean("fixed") + "/" + (pad.getBoolean("fixed") ? pad.getDouble("yaw") : 0);
            String first = firstBySetup.putIfAbsent(setup, id);
            if (first != null) {
                List<String> blocks = new ArrayList<>(list.getStringList(first + ".blocks"));
                blocks.add(block);
                list.set(first + ".blocks", blocks);
                list.set(id, null);
                continue;
            }
            for (String key : List.of("world", "x", "y", "z")) pad.set(key, null);
            pad.set("blocks", new ArrayList<>(List.of(block)));
        }
    }
}
