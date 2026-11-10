package net.exylia.exyliaLobby.module.parkour;

import net.exylia.lib.task.TaskHandle;
import net.exylia.lib.effect.Display;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One player's run. Everything in here is read and written on that player's own thread only: the registry hops
 * there before touching it.
 */
abstract class Run {

    final ParkourRuns runs;
    final Player player;
    /** Ticks counted so far: the run's time. */
    long ticks;
    TaskHandle timer;
    Display bar;
    /** The hotbar set the player wore before the run, given back after it. */
    @Nullable String hotbarBefore;
    /** Where a teleport this module started is going, so the guard lets it through. */
    @Nullable Location expected;
    boolean ended;
    /** What the guard took away for the run and gives back after it. */
    boolean flightBefore;
    final java.util.List<org.bukkit.potion.PotionEffect> effectsBefore = new java.util.ArrayList<>();

    Run(ParkourRuns runs, Player player) {
        this.runs = runs;
        this.player = player;
    }

    long millis() {
        return Times.millis(ticks);
    }

    /** The name of the run items' hotbar set. */
    abstract String hotbar();

    /** The run was registered: show its start. */
    abstract void begin();

    /** Once a tick while the run lasts. */
    abstract void tick();

    /** The runner moved to another position. */
    abstract void moved(Location from, Location to);

    /** The back to checkpoint item. */
    abstract void back();

    /** The restart item. */
    abstract void restart();

    /** The run is over: take down what this kind of run drew, and say what it has to. */
    abstract void ended(RunEnd end);

    /** Where a runner who fell into the void goes instead of spawn. */
    Optional<Location> rescue() {
        return Optional.empty();
    }
}
