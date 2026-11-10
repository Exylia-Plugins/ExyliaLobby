package net.exylia.exyliaLobby.module.parkour.endless;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * The library of jump patterns an endless path is made of. Each one is 3 to 8 jumps, has a difficulty (1-5,
 * matched against the curve) and a score it unlocks at; weights and unlocks are the owner's in the settings.
 */
public enum SegmentType {
    STRAIGHT(1, 0, 10, draw -> repeat(draw, 3, 6, () -> Move.straight(draw.gap() + 1, 0))),
    STEPS(1, 0, 8, draw -> repeat(draw, 3, 6, () -> {
        int up = draw.vertical();
        int gap = up > 0 ? Math.min(draw.gap(), 3) : draw.gap();
        return Move.straight(gap + 1, up);
    })),
    DIAGONAL(2, 10, 8, draw -> {
        int side = draw.random.nextBoolean() ? 1 : -1;
        boolean zigzag = draw.random.nextBoolean();
        return repeat(draw, 3, 5, () -> Move.single(2 + draw.random.nextInt(2),
                (zigzag && draw.random.nextBoolean() ? -side : side) * (1 + draw.random.nextInt(2)), 0, Role.FLOOR));
    }),
    CORNER(2, 10, 6, draw -> {
        List<Move> moves = repeat(draw, 2, 3, () -> Move.straight(draw.gap() + 1, 0));
        int turn = draw.random.nextBoolean() ? 1 : -1;
        moves.add(Move.straight(draw.gap() + 1, 0).turned(turn));
        moves.addAll(repeat(draw, 1, 2, () -> Move.straight(draw.gap() + 1, 0)));
        return moves;
    }),
    PILLARS(2, 20, 6, draw -> repeat(draw, 3, 5, () -> Move.straight(Math.min(draw.gap(), 3) + 1, draw.random.nextInt(3) == 0 ? draw.vertical() : 0)
            .shaped(Jump.Shape.PILLAR, 2 + draw.random.nextInt(3)))),
    CLIMB(2, 25, 4, draw -> {
        List<Move> moves = new ArrayList<>();
        moves.add(Move.straight(2 + draw.random.nextInt(2), 0));
        int height = 3 + draw.random.nextInt(3);
        moves.add(Move.straight(2, height).shaped(Jump.Shape.CLIMB, height));
        moves.add(Move.straight(2 + draw.random.nextInt(2), 0));
        return moves;
    }),
    SLOW(2, 30, 4, draw -> List.of(
            Move.single(2 + draw.random.nextInt(2), 0, 0, Role.SOUL_SAND),
            Move.straight(2 + draw.random.nextInt(2), 0),
            Move.single(2 + draw.random.nextInt(2), 0, 0, Role.HONEY),
            Move.straight(2, draw.random.nextBoolean() ? 0 : -1))),
    POSTS(3, 30, 6, draw -> {
        List<Move> moves = repeat(draw, 3, 5, () -> Move.single(2 + draw.random.nextInt(2), 0, 0, Role.POST));
        moves.add(Move.straight(2 + draw.random.nextInt(2), 0));
        return moves;
    }),
    NEO(3, 35, 4, draw -> List.of(
            Move.straight(2 + draw.random.nextInt(2), 0),
            Move.single(2, draw.random.nextBoolean() ? 1 : -1, 0, Role.FLOOR).shaped(Jump.Shape.NEO, 1),
            Move.straight(2 + draw.random.nextInt(2), 0))),
    SLIME(3, 40, 4, draw -> {
        int drop = 2 + draw.random.nextInt(2);
        return List.of(
                Move.straight(2 + draw.random.nextInt(2), 0),
                Move.single(2 + draw.random.nextInt(2), 0, -drop, Role.SLIME),
                Move.straight(2 + draw.random.nextInt(2), drop == 3 ? 2 : 1));
    }),
    HEAD_HITTER(3, 45, 4, draw -> {
        List<Move> moves = repeat(draw, 2, 3, () -> Move.straight(2 + draw.random.nextInt(2), 0)
                .shaped(Jump.Shape.STRIP, 2).withCeiling());
        moves.add(Move.straight(2 + draw.random.nextInt(2), 0));
        return moves;
    }),
    ICE(3, 50, 4, draw -> List.of(
            Move.single(draw.gap() + 1, 0, 0, Role.ICE).shaped(Jump.Shape.STRIP, 3),
            Move.straight(4 + (draw.level >= 4 ? draw.random.nextInt(2) : 0), 0),
            Move.straight(draw.gap() + 1, 0))),
    MOMENTUM(4, 70, 4, draw -> repeat(draw, 4, 6, () -> draw.random.nextInt(4) == 0
            ? Move.straight(5, -1) : Move.straight(4, 0)));

    private final int difficulty;
    private final int unlock;
    private final int weight;
    private final Function<Draw, List<Move>> builder;

    SegmentType(int difficulty, int unlock, int weight, Function<Draw, List<Move>> builder) {
        this.difficulty = difficulty;
        this.unlock = unlock;
        this.weight = weight;
        this.builder = builder;
    }

    public int difficulty() {
        return difficulty;
    }

    /** The score it unlocks at unless the settings say otherwise. */
    public int defaultUnlock() {
        return unlock;
    }

    /** How often it is drawn against the others unless the settings say otherwise. */
    public int defaultWeight() {
        return weight;
    }

    /** The settings key: {@code head-hitter}. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    List<Move> build(Draw draw) {
        return builder.apply(draw);
    }

    private static List<Move> repeat(Draw draw, int min, int max, java.util.function.Supplier<Move> move) {
        int count = min + draw.random.nextInt(max - min + 1);
        List<Move> moves = new ArrayList<>(count);
        for (int i = 0; i < count; i++) moves.add(move.get());
        return moves;
    }

    /** What a builder is handed: the random source, the level (1-5) and which way the height should go. */
    static final class Draw {
        final Random random;
        final int level;
        /** -1 wants to go down, 1 up. */
        final double heightBias;

        Draw(Random random, int level, double heightBias) {
            this.random = random;
            this.level = level;
            this.heightBias = heightBias;
        }

        /** A gap for a flat jump at this level: 1-2 at level 1 up to 3-4 at level 5. */
        int gap() {
            return switch (level) {
                case 1 -> 1 + random.nextInt(2);
                case 2 -> 1 + random.nextInt(3);
                case 3 -> 2 + random.nextInt(2);
                case 4 -> random.nextInt(5) == 0 ? 4 : 2 + random.nextInt(2);
                default -> random.nextInt(3) == 0 ? 4 : 3;
            };
        }

        /** -1, 0 or 1, leaning towards the middle of the height band. */
        int vertical() {
            double roll = random.nextDouble() * 2 - 1 + heightBias;
            return roll > 0.35 ? 1 : roll < -0.35 ? -1 : 0;
        }
    }
}
