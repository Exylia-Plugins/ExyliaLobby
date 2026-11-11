package net.exylia.exyliaLobby.module.parkour.endless;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PathGeneratorTest {

    private static final Bounds BOX = new Bounds(-30, 60, -30, 30, 100, 30);
    private static final Cell START = new Cell(0, 75, 0);

    /** Draws a path of at least {@code jumps} jumps, the score rising as a runner's would. */
    private static List<Jump> draw(long seed, Bounds box, int jumps) {
        PathGenerator generator = new PathGenerator(seed, box, START, (int) (seed & 3), Tuning.defaults());
        List<Jump> path = new ArrayList<>(List.of(generator.start()));
        int score = 0;
        while (path.size() <= jumps) {
            List<Jump> segment = generator.next(score);
            if (segment.isEmpty()) fail("seed " + seed + " walled itself in after " + path.size() + " jumps");
            for (Jump jump : segment) score += jump.points();
            path.addAll(segment);
        }
        return path;
    }

    @Test
    void everyJumpIsReachableFromTheOneBefore() {
        for (long seed = 0; seed < 150; seed++) {
            List<Jump> path = draw(seed, BOX, 400);
            for (int i = 1; i < path.size(); i++) {
                Jump jump = path.get(i);
                Jump before = path.get(i - 1);
                assertEquals(before.exit(), jump.takeoff(), "jumps must chain");
                assertEquals(before.ceilingOverExit(), jump.ceiling());
                if (jump.shape() == Jump.Shape.NEO || jump.shape() == Jump.Shape.CLIMB) {
                    assertEquals(2, Math.abs(jump.landing().x() - jump.takeoff().x()) + Math.abs(jump.landing().z() - jump.takeoff().z()));
                    continue;
                }
                int dx = jump.landing().x() - jump.takeoff().x();
                int dz = jump.landing().z() - jump.takeoff().z();
                int dy = jump.landing().y() - jump.takeoff().y();
                Role to = jump.shape() == Jump.Shape.REST ? Role.REST : jump.surface();
                assertTrue(JumpPhysics.reachable(jump.from(), jump.fallOntoFrom(), jump.ceiling(), dx, dz, dy, to),
                        "seed " + seed + " jump " + i + " is not reachable: " + jump);
            }
        }
    }

    @Test
    void neverLeavesTheBoxOrTheBand() {
        for (long seed = 0; seed < 150; seed++) {
            for (Jump jump : draw(seed, BOX, 400)) {
                for (Jump.Piece piece : jump.pieces()) assertTrue(BOX.contains(piece.at()), "seed " + seed + " left the box");
                if (jump.index() > 0) {
                    for (Cell cell : jump.standable()) assertTrue(BOX.inBand(cell), "seed " + seed + " left the band");
                }
            }
        }
    }

    @Test
    void neverCrossesItselfWhileAJumpIsRemembered() {
        for (long seed = 0; seed < 120; seed++) {
            List<Jump> path = draw(seed, BOX, 300);
            for (int i = 0; i < path.size(); i++) {
                Set<Cell> solids = solids(path.get(i));
                for (Cell cell : path.get(i).air()) assertFalse(solids.contains(cell), "a jump flies through itself");
                for (int j = Math.max(0, i - Tuning.MIN_MEMORY + 1); j < i; j++) {
                    Jump other = path.get(j);
                    Set<Cell> otherSolids = solids(other);
                    for (Cell cell : solids) {
                        assertFalse(otherSolids.contains(cell) || other.air().contains(cell),
                                "seed " + seed + ": jump " + i + " puts a block in jump " + j + " at " + cell);
                    }
                    for (Cell cell : path.get(i).air()) {
                        assertFalse(otherSolids.contains(cell), "seed " + seed + ": jump " + i + " flies into jump " + j);
                    }
                }
            }
        }
    }

    @Test
    void theSameSeedDrawsTheSamePath() {
        List<Jump> one = draw(42, BOX, 200);
        List<Jump> two = draw(42, BOX, 200);
        assertEquals(one, two);
    }

    @Test
    void aSmallBoxStillSnakesOn() {
        Bounds small = new Bounds(-10, 64, -10, 10, 84, 10);
        for (long seed = 0; seed < 60; seed++) draw(seed, small, 300);
    }

    @Test
    void theLibraryIsUsedAndPathsTurn() {
        Set<Jump.Shape> shapes = EnumSet.noneOf(Jump.Shape.class);
        Set<Role> surfaces = EnumSet.noneOf(Role.class);
        Set<Integer> headings = new HashSet<>();
        for (long seed = 0; seed < 40; seed++) {
            for (Jump jump : draw(seed, BOX, 400)) {
                shapes.add(jump.shape());
                surfaces.add(jump.surface());
                headings.add(Integer.signum(jump.landing().x() - jump.takeoff().x()) * 3
                        + Integer.signum(jump.landing().z() - jump.takeoff().z()));
                if (jump.ceiling()) surfaces.add(Role.CEILING);
            }
        }
        assertEquals(EnumSet.allOf(Jump.Shape.class), shapes);
        assertTrue(surfaces.containsAll(EnumSet.of(Role.ICE, Role.SLIME, Role.SOUL_SAND, Role.HONEY, Role.POST,
                Role.BONUS, Role.REST, Role.CEILING)), surfaces.toString());
        assertTrue(headings.size() >= 8, "paths should go every way: " + headings);
    }

    @Test
    void restPlatformsComeRegularly() {
        List<Jump> path = draw(7, BOX, 400);
        int since = 0;
        for (Jump jump : path) {
            since = jump.shape() == Jump.Shape.REST || jump.shape() == Jump.Shape.START ? 0 : since + 1;
            assertTrue(since < Tuning.defaults().restEvery() + 12, "no rest for " + since + " jumps");
        }
    }

    private static Set<Cell> solids(Jump jump) {
        Set<Cell> cells = new HashSet<>();
        for (Jump.Piece piece : jump.pieces()) cells.add(piece.at());
        return cells;
    }

    @Test
    void aFlatBoxStillGetsAPath() {
        Bounds flat = new Bounds(-20, 75, -20, 20, 75, 20).fitting(START);
        PathGenerator generator = new PathGenerator(7, flat, START, 0, Tuning.defaults());
        assertFalse(generator.next(0).isEmpty(), "corners picked on the ground must still leave room for jumps");
    }
}
