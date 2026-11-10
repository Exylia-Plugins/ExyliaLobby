package net.exylia.exyliaLobby.module.parkour;

import net.exylia.exyliaLobby.module.parkour.endless.Cell;
import net.exylia.exyliaLobby.module.parkour.endless.Jump;
import net.exylia.exyliaLobby.module.parkour.endless.JumpPhysics;
import net.exylia.exyliaLobby.module.parkour.endless.PathGenerator;
import net.exylia.exyliaLobby.module.parkour.endless.Role;
import net.exylia.exyliaLobby.module.parkour.endless.Window;
import net.exylia.lib.packet.FakeBlocks;
import net.exylia.lib.packet.Packets;
import net.exylia.lib.text.Values;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An endless, sprint or daily run: a generated path drawn only to its runner with fake blocks, a few jumps
 * ahead, cleared behind. Nothing is written to the world.
 */
final class EndlessRun extends Run {

    static final String HOTBAR = "parkour-endless";
    /**
     * The fastest a runner may move along the path, in jumps a second, after a head start of {@link #HEAD_START}
     * jumps. A sprint-jump chain that skips every other block stays under it; a flying client racing ahead does not.
     */
    private static final double JUMPS_PER_SECOND = 4.0;
    private static final int HEAD_START = 6;
    private static final BlockFace[] FACES = {BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH};

    final EndlessMode mode;
    final World world;
    /** Where the runner stood before the run: they go back there when it ends. */
    final Location origin;
    final Location start;
    private final PathGenerator generator;
    private final List<ParkourThemes.Theme> themes;
    private final int themeOffset;
    /** The all-time best score, for the marker block; 0 without one. */
    private final long best;
    /** Generated jumps from the first one drawn onwards. */
    private final ArrayDeque<Jump> path = new ArrayDeque<>();
    private final Set<Cell> drawn = new LinkedHashSet<>();
    private final Map<String, BlockData> blocks = new HashMap<>();
    private Window window;
    /** When the run began, wall clock: movement comes from the client, so lag does not slow it. */
    private long began;
    int current;
    int score;

    EndlessRun(ParkourRuns runs, Player player, EndlessMode mode, World world, Location origin, Location start,
               PathGenerator generator, List<ParkourThemes.Theme> themes, int themeOffset, long best) {
        super(runs, player);
        this.mode = mode;
        this.world = world;
        this.origin = origin;
        this.start = start;
        this.generator = generator;
        this.themes = themes;
        this.themeOffset = themeOffset;
        this.best = best;
    }

    @Override
    String hotbar() {
        return HOTBAR;
    }

    private ParkourSettings.Endless rules() {
        return runs.settings().endless();
    }

    private long sprintMillis() {
        return runs.settings().modes().sprintSeconds() * 1000L;
    }

    Values values() {
        return Values.of("mode", mode.name(runs.messages())).put("mode_id", mode.id()).put("score", score)
                .put("time", Times.format(millis())).put("left", Times.format(Math.max(0, sprintMillis() - millis())));
    }

    @Override
    void begin() {
        began = System.currentTimeMillis();
        path.add(generator.start());
        window = Window.around(0, rules().ahead(), rules().behind());
        fill(window.last());
        draw(path);
        runs.sound(player, runs.settings().sounds().start());
    }

    @Override
    void tick() {
        ticks++;
        if (mode == EndlessMode.SPRINT && millis() >= sprintMillis()) {
            runs.close(this, RunEnd.TIME_UP);
            return;
        }
        if (ticks % 2 != 0) return;
        ParkourMessages text = runs.messages();
        runs.bar(this, mode == EndlessMode.SPRINT ? text.sprintBossBar() : text.endlessBossBar(), values(),
                mode == EndlessMode.SPRINT ? 1f - (float) millis() / sprintMillis() : 1f);
    }

    @Override
    void moved(Location from, Location to) {
        Jump standing = null;
        Jump under = null;
        Cell on = null;
        for (Jump jump : path) {
            if (jump.index() == current) under = jump;
            if (jump.index() > current && window.contains(jump.index())) {
                on = Window.standingOn(jump, to.getX(), to.getY(), to.getZ());
                if (on != null) {
                    standing = jump;
                    break;
                }
            }
        }
        // The blocks are only on the client, so the server checks every landing it scores: feet on a drawn
        // block, the client saying it stands, a jump that reaches from the last one, and a human pace.
        if (standing != null && player.isOnGround()) {
            if (under == null || !JumpPhysics.canLand(under, standing, on) || tooFast(standing)) {
                runs.context().debug().warn("Ended the " + mode.id() + " run of " + player.getName() + ": the landing on jump "
                        + standing.index() + " from jump " + current + " could not be confirmed");
                runs.close(this, RunEnd.UNCONFIRMED);
                return;
            }
            land(standing);
            return;
        }
        if (under != null && to.getY() < under.lowestY() + 1 - rules().fallDepth()) runs.close(this, RunEnd.FELL);
    }

    private boolean tooFast(Jump next) {
        double seconds = (System.currentTimeMillis() - began) / 1000.0;
        return next.index() > HEAD_START + JUMPS_PER_SECOND * seconds;
    }

    /** Whether feet here are on or around the drawn path. */
    boolean near(Location feet) {
        return feet.getWorld() == world && Window.near(drawn, feet.getX(), feet.getY(), feet.getZ());
    }

    private void land(Jump jump) {
        int before = score;
        int themeBefore = theme(current);
        for (Jump passed : path) {
            if (passed.index() > current && passed.index() <= jump.index()) score += passed.points();
        }
        current = jump.index();
        advance();
        ParkourMessages text = runs.messages();
        boolean milestone = false;
        for (int at : rules().milestones()) {
            if (before < at && score >= at) {
                milestone = true;
                runs.title(player, text.milestoneTitle(), text.milestoneSubtitle(), values().put("score", at));
                List<Object> rewards = rules().milestoneRewards().get(String.valueOf(at));
                if (rewards != null) runs.milestone(this, at, rewards);
            }
        }
        if (milestone) {
            runs.sound(player, runs.settings().sounds().milestone());
        } else if (theme(current) != themeBefore) {
            runs.title(player, text.themeTitle(), "", values().put("theme", themes.get(theme(current)).name()));
        }
    }

    /** Moves the window to the jump stood on: draws what came into it, clears what left it. */
    private void advance() {
        Window next = Window.around(current, rules().ahead(), rules().behind());
        fill(next.last());
        List<Jump> leaving = new ArrayList<>();
        List<Jump> staying = new ArrayList<>();
        List<Jump> coming = new ArrayList<>();
        for (Jump jump : path) {
            if (!next.contains(jump.index())) {
                if (jump.index() < next.first()) leaving.add(jump);
                continue;
            }
            staying.add(jump);
            if (!window.contains(jump.index())) coming.add(jump);
        }
        window = next;
        Set<Cell> cleared = Window.leftBehind(leaving, staying);
        path.removeAll(leaving);
        if (!cleared.isEmpty()) {
            List<Location> at = new ArrayList<>();
            for (Cell cell : cleared) at.add(location(cell));
            drawn.removeAll(cleared);
            fakeBlocks().clear(player, at);
        }
        draw(coming);
    }

    /** Generates the path up to {@code last}. */
    private void fill(int last) {
        while (path.peekLast().index() < last) {
            List<Jump> segment = generator.next(path.peekLast().index());
            if (segment.isEmpty()) return;
            path.addAll(segment);
        }
    }

    private void draw(Iterable<Jump> jumps) {
        Map<Location, BlockData> shown = new LinkedHashMap<>();
        for (Jump jump : jumps) {
            if (!window.contains(jump.index())) continue;
            ParkourThemes.Theme theme = themes.get(theme(jump.index()));
            // ponytail: the marker goes on jump number <best>; bonus jumps worth more put the score ahead of the index
            boolean marker = best > 0 && jump.index() == best && !rules().bestMarker().isBlank();
            for (Jump.Piece piece : jump.pieces()) {
                String written = marker && piece.at().equals(jump.landing()) ? rules().bestMarker() : theme.block(piece.role());
                shown.put(location(piece.at()), block(written, piece));
                drawn.add(piece.at());
            }
        }
        if (!shown.isEmpty()) fakeBlocks().keep(player, shown);
    }

    private int theme(int index) {
        int every = rules().themeEvery();
        return Math.floorMod(themeOffset + (every > 0 ? index / every : 0), themes.size());
    }

    private BlockData block(String written, Jump.Piece piece) {
        BlockData data = blocks.computeIfAbsent(written, text -> {
            try {
                return Bukkit.createBlockData(text.trim().toLowerCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException bad) {
                runs.context().debug().warn("Parkour theme block " + text + " is not a block; drawing stone");
                return Material.STONE.createBlockData();
            }
        });
        if (piece.role() != Role.LADDER) return data;
        BlockData turned = data.clone();
        BlockFace face = FACES[Math.floorMod(piece.facing(), 4)];
        if (turned instanceof Directional directional && directional.getFaces().contains(face)) directional.setFacing(face);
        else if (turned instanceof MultipleFacing vine && vine.getAllowedFaces().contains(face.getOppositeFace())) vine.setFace(face.getOppositeFace(), true);
        return turned;
    }

    private Location location(Cell cell) {
        return new Location(world, cell.x(), cell.y(), cell.z());
    }

    private FakeBlocks fakeBlocks() {
        return Packets.of(runs.context().plugin()).fakeBlocks();
    }

    @Override
    void back() {
        // no checkpoints on an endless path
    }

    @Override
    void restart() {
        runs.startEndless(player, mode, origin);
    }

    @Override
    void ended(RunEnd end) {
        if (!drawn.isEmpty() && player.isOnline()) {
            List<Location> at = new ArrayList<>();
            for (Cell cell : drawn) at.add(location(cell));
            fakeBlocks().clear(player, at);
        }
        drawn.clear();
        // home() does nothing while the server stops: the drawn blocks are cleared and that is all
        if (end == RunEnd.LEFT || end == RunEnd.DISABLED || end.scored() || end.cheat() && end != RunEnd.TELEPORT) {
            runs.home(player, origin);
        }
        if (end.scored()) runs.scored(this);
    }
}
