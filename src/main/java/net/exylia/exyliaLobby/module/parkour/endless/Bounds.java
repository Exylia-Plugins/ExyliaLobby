package net.exylia.exyliaLobby.module.parkour.endless;

/** The box a path stays inside, corners included. */
public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public Bounds {
        int x0 = Math.min(minX, maxX), x1 = Math.max(minX, maxX);
        int y0 = Math.min(minY, maxY), y1 = Math.max(minY, maxY);
        int z0 = Math.min(minZ, maxZ), z1 = Math.max(minZ, maxZ);
        minX = x0;
        maxX = x1;
        minY = y0;
        maxY = y1;
        minZ = z0;
        maxZ = z1;
    }

    public boolean contains(Cell cell) {
        return cell.x() >= minX && cell.x() <= maxX && cell.y() >= minY && cell.y() <= maxY
                && cell.z() >= minZ && cell.z() <= maxZ;
    }

    /** Landings keep this far from the floor and the ceiling of the box, so climbs and drops have room. */
    boolean inBand(Cell landing) {
        return landing.y() >= minY + 2 && landing.y() <= maxY - 4;
    }

    double middleY() {
        return (minY + 2 + maxY - 4) / 2.0;
    }

    /** Blocks from a cell to the edge along a heading. */
    int room(Cell from, int heading) {
        return switch (Math.floorMod(heading, 4)) {
            case 0 -> maxX - from.x();
            case 1 -> maxZ - from.z();
            case 2 -> from.x() - minX;
            default -> from.z() - minZ;
        };
    }
}
