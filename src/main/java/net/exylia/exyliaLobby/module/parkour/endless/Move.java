package net.exylia.exyliaLobby.module.parkour.endless;

/**
 * One jump of a segment, relative to the heading: turn first ({@code -1} left, {@code 1} right), then
 * {@code forward} and {@code side} (to the right) blocks and {@code up}.
 */
record Move(int turn, int forward, int side, int up, Role surface, Jump.Shape shape, int size,
            boolean ceilingOverExit) {

    static Move single(int forward, int side, int up, Role surface) {
        return new Move(0, forward, side, up, surface, Jump.Shape.SINGLE, 1, false);
    }

    static Move straight(int forward, int up) {
        return single(forward, 0, up, Role.FLOOR);
    }

    Move turned(int turn) {
        return new Move(turn, forward, side, up, surface, shape, size, ceilingOverExit);
    }

    Move shaped(Jump.Shape shape, int size) {
        return new Move(turn, forward, side, up, surface, shape, size, ceilingOverExit);
    }

    Move withCeiling() {
        return new Move(turn, forward, side, up, surface, shape, size, true);
    }
}
