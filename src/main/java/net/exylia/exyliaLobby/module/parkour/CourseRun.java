package net.exylia.exyliaLobby.module.parkour;

import net.exylia.lib.guide.GuideBeacon;
import net.exylia.lib.text.Values;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A run on a built course: start, checkpoints in order, finish. */
final class CourseRun extends Run {

    static final String HOTBAR = "parkour-course";

    final String id;
    final ParkourCourses.Course course;
    final World world;
    /** The best run's splits, to compare with; null without one. */
    final @Nullable List<Long> best;
    final List<Long> splits = new ArrayList<>();
    private final Set<Material> killBlocks = EnumSet.noneOf(Material.class);
    int reached;
    /** The spot the runner stands in now ({@link CourseTrack}); the clock waits while it is the start. */
    int spot = CourseTrack.START;
    /** A teleport back to a checkpoint is on its way: falls are not counted twice. */
    boolean returning;
    private @Nullable GuideBeacon beacon;

    CourseRun(ParkourRuns runs, Player player, String id, ParkourCourses.Course course, World world,
              @Nullable List<Long> best) {
        super(runs, player);
        this.id = id;
        this.course = course;
        this.world = world;
        this.best = best;
        for (String name : course.killBlocks()) {
            Material material = Material.matchMaterial(name);
            if (material != null) killBlocks.add(material);
        }
    }

    @Override
    String hotbar() {
        return HOTBAR;
    }

    int total() {
        return course.checkpoints().size() + 1;
    }

    Values values() {
        return Values.of("course", course.name()).put("time", Times.format(millis()))
                .put("checkpoint", reached).put("total", total());
    }

    @Override
    void begin() {
        ParkourMessages text = runs.messages();
        runs.title(player, text.startTitle(), text.startSubtitle(), values());
        runs.sound(player, runs.settings().sounds().start());
        aim();
    }

    @Override
    void tick() {
        if (spot != CourseTrack.START) ticks++;
        if (course.timeLimit() > 0 && millis() > course.timeLimit() * 1000) {
            runs.close(this, RunEnd.TIME_UP);
            return;
        }
        if (ticks % 2 == 0) {
            bar.name(runs.component(player, runs.messages().bossBar(), values()));
            bar.progress(Math.min(1f, (float) reached / total()));
        }
    }

    @Override
    void moved(Location from, Location to) {
        if (returning) return;
        int depth = runs.settings().courseFallDepth();
        if (depth > 0 && to.getY() < course.lowestY() - depth || onKillBlock(to)) {
            runs.sound(player, runs.settings().sounds().fail());
            back();
            return;
        }
        int now = CourseTrack.spotAt(course, reached, to.getX(), to.getY(), to.getZ());
        if (now == spot) return;
        spot = now;
        switch (CourseTrack.step(now, reached, course.checkpoints().size())) {
            case RESTART -> restartHere();
            case CHECKPOINT -> reach();
            case FINISH -> runs.finish(this);
            case MISSED -> runs.send(player, runs.messages().missedCheckpoint(), values());
            case NOTHING -> {
            }
        }
    }

    private boolean onKillBlock(Location to) {
        if (killBlocks.isEmpty()) return false;
        return killBlocks.contains(to.getBlock().getType())
                || killBlocks.contains(world.getBlockAt(to.getBlockX(), (int) Math.floor(to.getY() - 0.1), to.getBlockZ()).getType());
    }

    private void reach() {
        long millis = millis();
        splits.add(millis);
        reached++;
        ParkourMessages text = runs.messages();
        Long delta = Times.compare(best, reached - 1, millis);
        String deltaLine = delta == null ? "" : Values.of("delta", Times.delta(delta))
                .apply(delta < 0 ? text.deltaAhead() : text.deltaBehind());
        runs.title(player, text.checkpointTitle(), text.checkpointSubtitle(), values().put("delta", deltaLine));
        runs.sound(player, runs.settings().sounds().checkpoint());
        aim();
    }

    /** Stepped back onto the start: the run starts over from here. */
    private void restartHere() {
        ticks = 0;
        reached = 0;
        splits.clear();
        begin();
    }

    /** The beam over the next checkpoint, or the finish. */
    private void aim() {
        if (!runs.settings().checkpointBeacon()) return;
        ParkourCourses.Spot next = reached < course.checkpoints().size() ? course.checkpoints().get(reached) : course.finish();
        Location at = new Location(world, next.x(), next.y(), next.z());
        if (beacon != null) {
            beacon.retarget(at);
            return;
        }
        beacon = runs.beacon(player, at, metres -> runs.component(player, runs.messages().beaconLabel(),
                Values.of("checkpoint", reached + 1).put("total", total()).put("distance", Math.round(metres))));
    }

    /** Where the runner goes back to: the last checkpoint reached, or the start. */
    Location lastCheckpoint() {
        ParkourCourses.Spot spot = reached == 0 ? course.start() : course.checkpoints().get(reached - 1);
        return ParkourRuns.location(world, spot);
    }

    @Override
    void back() {
        if (returning) return;
        returning = true;
        runs.move(this, lastCheckpoint()).whenComplete((result, failed) ->
                runs.onPlayer(player, () -> returning = false));
    }

    @Override
    void restart() {
        if (returning) return;
        returning = true;
        runs.move(this, ParkourRuns.location(world, course.start())).whenComplete((result, failed) ->
                runs.onPlayer(player, () -> {
                    returning = false;
                    if (ended) return;
                    spot = CourseTrack.START;
                    restartHere();
                }));
    }

    @Override
    Optional<Location> rescue() {
        Location at = lastCheckpoint();
        expected = at;
        return Optional.of(at);
    }

    @Override
    void ended(RunEnd end) {
        if (beacon != null) beacon.remove();
        if (end == RunEnd.TIME_UP) runs.send(player, runs.messages().timeUp(), values());
    }
}
