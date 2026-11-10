package net.exylia.exyliaLobby.module.shared;

import net.exylia.lib.config.Comment;
import net.exylia.lib.effect.Display;
import net.exylia.lib.effect.Effects;
import net.exylia.lib.task.TaskHandle;
import net.exylia.lib.task.TaskScheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * A short countdown for one player: a boss bar that empties smoothly with the time left in decimals, a quiet tick
 * each whole second whose pitch rises towards the end, and a sound when it finishes or is called off. Only that
 * player sees and hears it. Started, ticked and ended on the player's own thread.
 */
public final class Countdown {

    /** The sounds of a countdown, each {@code NAME|volume|pitch}. Blank plays nothing. */
    public record Sounds(
            @Comment("Each whole second; its pitch rises a little every second.")
            String tick,
            @Comment("When the countdown finishes.")
            String finish,
            @Comment("When it is called off.")
            String cancel) {

        public Sounds() {
            this("BLOCK_NOTE_BLOCK_HAT|0.6|1.0", "ENTITY_EXPERIENCE_ORB_PICKUP|0.6|1.2", "ENTITY_VILLAGER_NO|0.6|1.0");
        }
    }

    private static final long PERIOD = 2;
    private static final double PITCH_STEP = 0.12;

    private final Player player;
    private final Sounds sounds;
    private final long total;
    private Display bar;
    private TaskHandle task;
    private long elapsed;
    private long second;
    private int step;
    private volatile boolean over;

    private Countdown(Player player, Sounds sounds, long total) {
        this.player = player;
        this.sounds = sounds;
        this.total = total;
        this.second = (total + 19) / 20;
    }

    /**
     * Starts a countdown.
     *
     * @param text   the boss bar; {@code %time%} is the time left ("2.4s"), {@code %seconds%} still reads the same
     * @param colour the boss bar colour (GREEN, RED, PURPLE...)
     * @param done   run on the player's thread when it finishes, never when it is called off
     */
    public static @NotNull Countdown start(@NotNull Plugin plugin, @NotNull TaskScheduler tasks, @NotNull Player player,
                                           double seconds, @NotNull String text, @NotNull String colour,
                                           @NotNull Sounds sounds, @NotNull Runnable done) {
        Countdown countdown = new Countdown(player, sounds, Math.max(1, Math.round(seconds * 20)));
        String line = text.replace("%seconds%s", "%time%").replace("%seconds%", "%time%");
        countdown.bar = Effects.of(plugin).bossBar(line).countdown(seconds).timeStyle("precise").colour(colour)
                .every(PERIOD).show(player);
        countdown.tick();
        countdown.task = tasks.runAtEntityTimer(player, PERIOD, PERIOD, () -> countdown.advance(done));
        return countdown;
    }

    private void advance(Runnable done) {
        if (over) return;
        elapsed += PERIOD;
        if (elapsed >= total) {
            end();
            play(sounds.finish(), 0);
            done.run();
            return;
        }
        long now = (total - elapsed + 19) / 20;
        if (now != second) {
            second = now;
            step++;
            tick();
        }
    }

    private void tick() {
        play(sounds.tick(), Math.min(1.0, step * PITCH_STEP));
    }

    /** Calls it off with the cancel sound; {@code done} never runs. */
    public void cancel() {
        if (over) return;
        end();
        play(sounds.cancel(), 0);
    }

    /** Takes it down quietly; {@code done} never runs. */
    public void stop() {
        if (!over) end();
    }

    private void end() {
        over = true;
        if (task != null) task.cancel();
        if (bar != null) bar.stop();
    }

    private void play(String line, double raise) {
        if (line == null || line.isBlank()) return;
        String[] parts = line.split("\\|");
        double pitch = 1.0;
        if (parts.length > 2) {
            try {
                pitch = Double.parseDouble(parts[2].trim());
            } catch (NumberFormatException ignored) {
                // Effects reads the same line and falls back the same way.
            }
        }
        Effects.soundFrom(line).pitch(Math.min(2.0, pitch + raise)).show(player);
    }
}
