package net.exylia.exyliaLobby.module.triggers;

import net.exylia.lib.config.Comment;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code modules/triggers/config.yml}: blocks and areas that run actions. */
public record TriggersSettings(
        @Comment("The triggers, by id. Add them in game from /lobbyadmin → Triggers.")
        @Comment("A block trigger runs when the block is clicked or stepped on (buttons, levers, pressure plates,")
        @Comment("tripwires, any block); an area trigger runs its actions on entering and leave-actions on leaving.")
        @Comment("Actions are interaction lines: 'console: say hi', '[MESSAGE] ...', '[SOUND] ...', '[SERVER] games',")
        @Comment("'[TELEPORT] world;0;64;0', 'right: ...' for one click only. Guide: ExyliaLib docs/interactions.md.")
        Map<String, Trigger> list) {

    public TriggersSettings() {
        this(new LinkedHashMap<>());
    }

    /**
     * One trigger. A block trigger has {@code min} equal to {@code max}.
     *
     * @param min        a corner, {@code x y z}
     * @param max        the other corner, {@code x y z}
     * @param permission needed to set it off; empty is everyone
     * @param cooldown   before the same player sets it off again
     */
    public record Trigger(String world, String min, String max, boolean area, String permission, Duration cooldown,
                          List<String> actions, List<String> leaveActions) {
        public Trigger() {
            this("world", "0 64 0", "0 64 0", false, "", Duration.ofSeconds(1), List.of(), List.of());
        }

        Trigger withActions(List<String> lines) {
            return new Trigger(world, min, max, area, permission, cooldown, List.copyOf(lines), leaveActions);
        }

        Trigger withCooldown(Duration time) {
            return new Trigger(world, min, max, area, permission, time, actions, leaveActions);
        }
    }
}
