package net.exylia.exyliaLobby.module.parkour.endless;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Draws an endless path, one segment at a time, inside a box.
 *
 * <p>Every jump it hands out is {@linkplain JumpPhysics#reachable reachable} from the one before, keeps every
 * block inside the box and its landings inside the height band, and touches no block or flight space of the
 * jumps still in its memory (the last {@value Tuning#MEMORY}, which covers everything a runner is shown). Old
 * jumps leave the memory, so the path can wander the box forever, turning and snaking.
 *
 * <p>Pure and deterministic: the same seed, box, start and tuning draw the same path (the daily mode).
 */
public final class PathGenerator {

    private static final int ATTEMPTS = 48;

    private final Random random;
    private final Bounds bounds;
    private final Tuning tuning;
    private final ArrayDeque<Jump> live = new ArrayDeque<>();
    private final Map<Cell, Integer> solid = new HashMap<>();
    private final Map<Cell, Integer> air = new HashMap<>();
    private final Jump first;
    private Jump last;
    private int heading;
    private int sinceRest;
    private int index;

    /**
     * @param start   the block under the runner's feet at the start; a 3x3 platform is drawn around it
     * @param heading where the path sets off: 0 +x, 1 +z, 2 -x, 3 -z
     */
    public PathGenerator(long seed, Bounds bounds, Cell start, int heading, Tuning tuning) {
        this.random = new Random(seed);
        this.bounds = bounds;
        this.tuning = tuning;
        this.heading = Math.floorMod(heading, 4);
        List<Jump.Piece> pieces = new ArrayList<>();
        List<Cell> standable = new ArrayList<>();
        Set<Cell> headroom = new LinkedHashSet<>();
        for (int forward = -1; forward <= 1; forward++) {
            for (int side = -1; side <= 1; side++) {
                Cell cell = start.move(this.heading, forward, side, 0);
                pieces.add(new Jump.Piece(cell, Role.REST, 0));
                standable.add(cell);
                headroom(cell, headroom);
            }
        }
        first = new Jump(0, start, Role.REST, 0, false, Jump.Shape.START, start, Role.REST, List.copyOf(standable),
                start.move(this.heading, 1, 0, 0), false, List.copyOf(pieces), Set.copyOf(headroom), 0, false);
        commit(first);
    }

    /** The start platform, jump 0. */
    public Jump start() {
        return first;
    }

    /**
     * The next segment of the path for a runner at {@code score}: a few jumps, or a single rest platform.
     * Empty only when the path is walled in (a box far too small).
     */
    public List<Jump> next(int score) {
        if (tuning.restEvery() > 0 && sinceRest >= tuning.restEvery()) {
            List<Jump> rest = place(List.of(new Move(0, 2 + random.nextInt(2), 0, 0, Role.REST, Jump.Shape.REST, 3, false)),
                    false, 0);
            if (rest != null) return rest;
        }
        boolean bonus = score >= tuning.bonusFrom() && random.nextDouble() < tuning.bonusChance();
        int max = tuning.maxDifficulty(score);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            SegmentType type = pick(score, max, bonus && attempt < ATTEMPTS / 2);
            boolean isBonus = bonus && attempt < ATTEMPTS / 2 && type.difficulty() > max;
            int level = Math.min(5, isBonus ? max + 1 : max);
            List<Move> moves = type.build(new SegmentType.Draw(random, level, heightBias()));
            List<Jump> placed = place(moves, isBonus, attempt);
            if (placed != null) return placed;
        }
        List<Jump> placed = anyJump();
        if (placed != null) return placed;
        // Walled in by its own memory: forget what no runner can still see and look again.
        while (live.size() > Tuning.MIN_MEMORY) forget(live.removeFirst());
        placed = anyJump();
        return placed == null ? List.of() : placed;
    }

    /** Any single plain jump from the exit, in any direction: the way out of a tight spot. */
    private List<Jump> anyJump() {
        for (int up : new int[]{0, -1, 1}) {
            for (int forward = 2; forward <= 4; forward++) {
                for (int side = 0; side <= 2; side++) {
                    for (int sign = 1; sign >= -1; sign -= 2) {
                        for (int turn = 0; turn < 4; turn++) {
                            List<Jump> placed = place(List.of(Move.single(forward, side * sign, up, Role.FLOOR).turned(turn)),
                                    false, -1);
                            if (placed != null) return placed;
                        }
                        if (side == 0) break;
                    }
                }
            }
        }
        return null;
    }

    private SegmentType pick(int score, int max, boolean bonus) {
        List<SegmentType> pool = new ArrayList<>();
        int total = 0;
        for (SegmentType type : SegmentType.values()) {
            int weight = tuning.weight(type);
            if (weight == 0) continue;
            boolean fits = bonus ? type.difficulty() == Math.min(5, max + 1) && max < 5
                    : type.difficulty() <= max && score >= tuning.unlock(type);
            if (!fits) continue;
            pool.add(type);
            total += weight;
        }
        if (pool.isEmpty()) return bonus ? pick(score, max, false) : SegmentType.STRAIGHT;
        int roll = random.nextInt(total);
        for (SegmentType type : pool) {
            roll -= tuning.weight(type);
            if (roll < 0) return type;
        }
        return pool.get(pool.size() - 1);
    }

    private double heightBias() {
        double half = Math.max(1.0, (bounds.maxY() - 4 - (bounds.minY() + 2)) / 2.0);
        return Math.max(-1.0, Math.min(1.0, (bounds.middleY() - last.exit().y()) / half));
    }

    /** The heading a segment sets off in: straight on while there is room, else towards the most room. */
    private int pickHeading(int attempt) {
        if (attempt == 0 && bounds.room(last.exit(), heading) >= 12 && random.nextDouble() >= tuning.turnChance()) {
            return heading;
        }
        int[] options = {heading, heading + 1, heading + 3, heading + 2};
        double[] weights = new double[options.length];
        double total = 0;
        for (int i = 0; i < options.length; i++) {
            double room = Math.max(0, bounds.room(last.exit(), options[i]) - 3);
            weights[i] = room * room * (i == 3 ? 0.05 : 1.0) + 0.01;
            total += weights[i];
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < options.length; i++) {
            roll -= weights[i];
            if (roll < 0) return Math.floorMod(options[i], 4);
        }
        return heading;
    }

    /** Places a segment from the current exit, or answers null and changes nothing when it does not fit. */
    private List<Jump> place(List<Move> moves, boolean bonus, int attempt) {
        int localHeading = attempt < 0 ? heading : pickHeading(attempt);
        Map<Cell, Integer> planSolid = new HashMap<>();
        Set<Cell> planAir = new HashSet<>();
        List<Jump> jumps = new ArrayList<>(moves.size());
        Jump previous = last;
        for (Move move : moves) {
            localHeading = Math.floorMod(localHeading + move.turn(), 4);
            Jump jump = build(previous, move, localHeading, bonus, index + jumps.size() + 1);
            if (jump == null || !fits(jump, planSolid, planAir)) return null;
            for (Jump.Piece piece : jump.pieces()) planSolid.merge(piece.at(), 1, Integer::sum);
            planAir.addAll(jump.air());
            jumps.add(jump);
            previous = jump;
        }
        for (Jump jump : jumps) commit(jump);
        heading = localHeading;
        return jumps;
    }

    private boolean fits(Jump jump, Map<Cell, Integer> planSolid, Set<Cell> planAir) {
        for (Cell cell : jump.standable()) {
            if (!bounds.inBand(cell)) return false;
        }
        for (Jump.Piece piece : jump.pieces()) {
            Cell at = piece.at();
            if (!bounds.contains(at) || solid.containsKey(at) || air.containsKey(at)
                    || planSolid.containsKey(at) || planAir.contains(at)) {
                return false;
            }
        }
        for (Cell cell : jump.air()) {
            if (solid.containsKey(cell) || planSolid.containsKey(cell)) return false;
        }
        return true;
    }

    private Jump build(Jump previous, Move move, int heading, boolean bonus, int number) {
        Cell takeoff = previous.exit();
        Role from = previous.surface();
        double fall = previous.fallOntoFrom();
        if (previous.shape() != Jump.Shape.START && previous.landing().equals(takeoff)) {
            fall = Math.max(0, previous.takeoff().y() - previous.landing().y()) + JumpPhysics.JUMP_HEIGHT;
        }
        boolean ceiling = previous.ceilingOverExit();
        Role surface = bonus && move.surface() == Role.FLOOR ? Role.BONUS : move.surface();
        List<Jump.Piece> pieces = new ArrayList<>();
        List<Cell> standable = new ArrayList<>();
        Set<Cell> needed = new LinkedHashSet<>();
        Cell landing;
        Cell exit;
        switch (move.shape()) {
            case NEO -> {
                if (ceiling) return null;
                int swing = move.side() >= 0 ? 1 : -1;
                landing = takeoff.move(heading, 2, 0, 0);
                for (int up = 0; up <= 2; up++) {
                    pieces.add(new Jump.Piece(takeoff.move(heading, 1, 0, up), Role.WALL, 0));
                    pieces.add(new Jump.Piece(takeoff.move(heading, 1, -swing, up), Role.WALL, 0));
                }
                Cell around = takeoff.move(heading, 1, swing, 0);
                flight(takeoff, around, needed);
                flight(around, landing, needed);
                pieces.add(new Jump.Piece(landing, surface, 0));
                standable.add(landing);
                exit = landing;
            }
            case CLIMB -> {
                if (ceiling) return null;
                int height = move.size();
                landing = takeoff.move(heading, 2, 0, height);
                for (int up = 1; up <= height; up++) {
                    pieces.add(new Jump.Piece(takeoff.move(heading, 1, 0, up), Role.LADDER, Math.floorMod(heading + 2, 4)));
                    if (up < height) pieces.add(new Jump.Piece(takeoff.move(heading, 2, 0, up), Role.WALL, 0));
                }
                for (int up = 1; up <= height + 2; up++) needed.add(takeoff.move(heading, 1, 0, up));
                headroom(takeoff, needed);
                pieces.add(new Jump.Piece(landing, surface, 0));
                standable.add(landing);
                exit = landing;
            }
            default -> {
                landing = takeoff.move(heading, move.forward(), move.side(), move.up());
                int dx = landing.x() - takeoff.x();
                int dz = landing.z() - takeoff.z();
                if (!JumpPhysics.reachable(from, fall, ceiling, dx, dz, move.up(), surface)) return null;
                flight(takeoff, landing, needed);
                if (move.shape() == Jump.Shape.REST) {
                    for (int forward = 0; forward <= 2; forward++) {
                        for (int side = -1; side <= 1; side++) {
                            Cell cell = landing.move(heading, forward, side, 0);
                            pieces.add(new Jump.Piece(cell, Role.REST, 0));
                            standable.add(cell);
                        }
                    }
                    exit = landing.move(heading, 2, 0, 0);
                } else if (move.shape() == Jump.Shape.STRIP) {
                    exit = landing;
                    for (int forward = 0; forward < Math.max(1, move.size()); forward++) {
                        Cell cell = landing.move(heading, forward, 0, 0);
                        pieces.add(new Jump.Piece(cell, surface, 0));
                        standable.add(cell);
                        exit = cell;
                    }
                } else {
                    pieces.add(new Jump.Piece(landing, surface, 0));
                    standable.add(landing);
                    exit = landing;
                    if (move.shape() == Jump.Shape.PILLAR) {
                        for (int down = 1; down <= move.size(); down++) {
                            pieces.add(new Jump.Piece(landing.above(-down), Role.PILLAR, 0));
                        }
                    }
                }
            }
        }
        if (move.ceilingOverExit()) pieces.add(new Jump.Piece(exit.above(3), Role.CEILING, 0));
        for (Cell cell : standable) headroom(cell, needed);
        for (Jump.Piece piece : pieces) needed.remove(piece.at());
        int points = surface == Role.REST ? 1 : bonus ? tuning.bonusPoints() : 1;
        Role landed = move.shape() == Jump.Shape.REST ? Role.REST : surface;
        return new Jump(number, takeoff, from, fall, ceiling, move.shape(), landing, landed, List.copyOf(standable),
                exit, move.ceilingOverExit(), List.copyOf(pieces), Set.copyOf(needed), points, bonus);
    }

    /** Two cells of headroom over a block a player stands on. */
    private static void headroom(Cell cell, Set<Cell> out) {
        out.add(cell.above(1));
        out.add(cell.above(2));
    }

    /**
     * The space a jump flies through: every column between take-off and landing, from just above the lower
     * block to three above the higher one (the head at the top of a jump). The end columns only get their
     * headroom, so a slab can hang over a take-off.
     */
    private static void flight(Cell from, Cell to, Set<Cell> out) {
        double distance = Math.hypot(to.x() - from.x(), to.z() - from.z());
        int steps = Math.max(2, (int) Math.ceil(distance * 3));
        int low = Math.min(from.y(), to.y());
        int high = Math.max(from.y(), to.y());
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            int x = (int) Math.floor(from.x() + (to.x() - from.x()) * t + 0.5);
            int z = (int) Math.floor(from.z() + (to.z() - from.z()) * t + 0.5);
            int bottom;
            int top;
            if (x == from.x() && z == from.z()) {
                bottom = from.y() + 1;
                top = from.y() + 2;
            } else if (x == to.x() && z == to.z()) {
                bottom = to.y() + 1;
                top = to.y() + 2;
            } else {
                bottom = low + 1;
                top = high + 3;
            }
            for (int y = bottom; y <= top; y++) out.add(new Cell(x, y, z));
        }
    }

    private void commit(Jump jump) {
        live.addLast(jump);
        for (Jump.Piece piece : jump.pieces()) solid.merge(piece.at(), 1, Integer::sum);
        for (Cell cell : jump.air()) air.merge(cell, 1, Integer::sum);
        while (live.size() > Tuning.MEMORY) forget(live.removeFirst());
        last = jump;
        index = jump.index();
        sinceRest = jump.shape() == Jump.Shape.REST || jump.shape() == Jump.Shape.START ? 0 : sinceRest + 1;
    }

    private void forget(Jump jump) {
        for (Jump.Piece piece : jump.pieces()) solid.computeIfPresent(piece.at(), (cell, count) -> count > 1 ? count - 1 : null);
        for (Cell cell : jump.air()) air.computeIfPresent(cell, (c, count) -> count > 1 ? count - 1 : null);
    }
}
