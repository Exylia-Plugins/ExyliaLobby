package net.exylia.exyliaLobby.module.parkour.endless;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowTest {

    @Test
    void drawsAFewBehindAndAhead() {
        assertEquals(new Window(0, 3), Window.around(0, 3, 2), "nothing before the start");
        assertEquals(new Window(8, 13), Window.around(10, 3, 2));
        assertEquals(new Window(10, 11), Window.around(10, 0, 0), "always the next jump at least");
        Window before = Window.around(10, 3, 2);
        Window after = Window.around(12, 3, 2);
        assertFalse(after.contains(9));
        assertTrue(before.contains(9) && !after.contains(9), "jump 9 is cleared");
        assertTrue(after.contains(15) && !before.contains(15), "jump 15 is drawn");
    }

    @Test
    void clearsOnlyBlocksNoDrawnJumpStillUses() {
        Cell shared = new Cell(0, 70, 0);
        Cell own = new Cell(1, 70, 0);
        Jump leaving = jump(0, List.of(shared, own));
        Jump staying = jump(1, List.of(shared, new Cell(2, 70, 0)));
        assertEquals(Set.of(own), Window.leftBehind(List.of(leaving), List.of(staying)));
    }

    @Test
    void landingNeedsFeetOnTheBlockTop() {
        PathGenerator generator = new PathGenerator(7, new Bounds(-30, 60, -30, 30, 100, 30), new Cell(0, 75, 0), 0, Tuning.defaults());
        Jump start = generator.start();
        assertTrue(Window.standsOn(start, 0.5, 76, 0.5));
        assertTrue(Window.standsOn(start, 1.7, 76.1, 0.5), "on the edge of the 3x3 platform");
        assertFalse(Window.standsOn(start, 0.5, 77, 0.5), "in the air over it");
        assertFalse(Window.standsOn(start, 0.5, 74, 0.5), "under it");
        List<Jump> path = new ArrayList<>(generator.next(0));
        Jump next = path.get(0);
        Cell landing = next.landing();
        assertTrue(Window.standsOn(next, landing.x() + 0.5, landing.y() + next.surface().surface(), landing.z() + 0.5));
    }

    private static Jump jump(int index, List<Cell> cells) {
        List<Jump.Piece> pieces = cells.stream().map(cell -> new Jump.Piece(cell, Role.FLOOR, 0)).toList();
        return new Jump(index, cells.get(0), Role.FLOOR, 0, false, Jump.Shape.SINGLE, cells.get(0), Role.FLOOR, cells,
                cells.get(0), false, pieces, Set.of(), 1, false);
    }
}
