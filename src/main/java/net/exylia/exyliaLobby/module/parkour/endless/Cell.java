package net.exylia.exyliaLobby.module.parkour.endless;

/** One block position. */
public record Cell(int x, int y, int z) {

    /** The four headings a path walks in: +x, +z, -x, -z. A heading's right-hand side is the next one. */
    static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    public Cell plus(int dx, int dy, int dz) {
        return new Cell(x + dx, y + dy, z + dz);
    }

    /** {@code forward} blocks along a heading, {@code side} to its right and {@code up}. */
    Cell move(int heading, int forward, int side, int up) {
        int[] f = DIRECTIONS[Math.floorMod(heading, 4)];
        int[] s = DIRECTIONS[Math.floorMod(heading + 1, 4)];
        return plus(f[0] * forward + s[0] * side, up, f[1] * forward + s[1] * side);
    }

    public Cell above(int blocks) {
        return plus(0, blocks, 0);
    }

    public boolean sameColumn(Cell other) {
        return x == other.x && z == other.z;
    }
}
