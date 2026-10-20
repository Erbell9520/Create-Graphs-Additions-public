package com.createstats.gui.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanPainterTest {
    private static final int STONE = 0x707070;
    private static final int OAK = 0x8F7748;

    private static PlanGrid room() {
        final PlanGrid g = new PlanGrid(0, 0, 5, 5);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                final boolean edge = x == 0 || z == 0 || x == 4 || z == 4;
                g.set(x, z, edge ? PlanGrid.WALL : PlanGrid.FLOOR, edge ? STONE : OAK);
                g.under(x, z, OAK);
                g.flag(x, z, PlanGrid.F_ROOFED);
            }
        }
        return g;
    }

    private static int px(PlanPainter.Picture p, int x, int y) {
        return p.argb()[y * p.w() + x] & 0xFFFFFF;
    }

    @Test
    @DisplayName("a shut door lies along its closed edge, and its swing stays inside its own block")
    void doorSwingsInsideItsBlock() {
        final int s = 8;
        final PlanGrid g = room();
        g.set(2, 4, PlanGrid.DOOR, OAK);
        g.doorEdges(2, 4, PlanGrid.NORTH, PlanGrid.WEST);
        g.settle();
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);

        final int outer = PlanPainter.shade(OAK, PlanPainter.LOWEST);
        final int inner = PlanPainter.shade(OAK, PlanPainter.HIGH);
        for (int i = 0; i < s; i++) {
            assertEquals(outer, px(p, 2 * s + i, 4 * s), "leaf pixel " + i);
            assertEquals(inner, px(p, 2 * s + i, 4 * s + 1), "leaf pixel " + i + " second row");
        }

        final PlanPainter.Picture bare = PlanPainter.paint(room(), s, List.of(), false);
        for (int y = 3 * s; y < 4 * s; y++) {
            for (int x = 2 * s; x < 3 * s; x++) {
                assertEquals(px(bare, x, y), px(p, x, y), "the room north of the door at " + x + "," + y);
            }
        }
    }

    @Test
    @DisplayName("an open door lies along the other edge; the hinge is the corner the two share")
    void openDoorLiesAlongItsOpenEdge() {
        final int s = 8;
        final PlanGrid g = room();
        g.set(2, 4, PlanGrid.DOOR, OAK);
        g.doorEdges(2, 4, PlanGrid.NORTH, PlanGrid.WEST);
        g.flag(2, 4, PlanGrid.F_OPEN);
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
        final int outer = PlanPainter.shade(OAK, PlanPainter.LOWEST);
        final int inner = PlanPainter.shade(OAK, PlanPainter.HIGH);
        for (int j = 0; j < s; j++) {
            assertEquals(outer, px(p, 2 * s, 4 * s + j), "open leaf along the west edge, row " + j);
            assertEquals(inner, px(p, 2 * s + 1, 4 * s + j), "the leaf's inner pixel, row " + j);
        }

        final int base = px(p, 2 * s + 3, 4 * s + 4);
        assertEquals(PlanPainter.dark(base, 0.55f), px(p, 2 * s + s - 1, 4 * s));
    }

    @Test
    @DisplayName("a window across a wall that runs east to west: frame, glass, sash, glass, frame")
    void windowAcrossTheWall() {
        final int s = 8;
        final PlanGrid g = room();
        g.set(2, 0, PlanGrid.WINDOW, STONE);
        g.settle();
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
        final int frame = PlanPainter.shade(STONE, PlanPainter.LOWEST);
        for (int i = 0; i < s; i++) {
            assertEquals(frame, px(p, 2 * s + i, 0), "outer frame");
            assertEquals(frame, px(p, 2 * s + i, s - 1), "inner frame");
            assertEquals(frame, px(p, 2 * s + i, s / 2), "sash");
            assertEquals(PlanPainter.GLASS, px(p, 2 * s + i, 1), "glass");
        }
    }

    @Test
    @DisplayName("a window in a wall that runs north to south turns with it")
    void windowTurnsWithItsWall() {
        final int s = 8;
        final PlanGrid g = room();
        g.set(0, 2, PlanGrid.WINDOW, STONE);
        g.settle();
        assertTrue(g.has(0, 2, PlanGrid.F_AXIS_Z));
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
        final int frame = PlanPainter.shade(STONE, PlanPainter.LOWEST);
        for (int j = 0; j < s; j++) {
            assertEquals(frame, px(p, 0, 2 * s + j));
            assertEquals(frame, px(p, s / 2, 2 * s + j));
            assertEquals(PlanPainter.GLASS, px(p, 1, 2 * s + j));
        }
    }

    @Test
    @DisplayName("a lamp's light pools on its own floor and stops at the wall: the next room stays dark")
    void lightStopsAtWalls() {
        final int s = 8;
        final PlanGrid g = new PlanGrid(0, 0, 7, 3);
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 3; z++) {
                g.set(x, z, x == 3 ? PlanGrid.WALL : PlanGrid.FLOOR, x == 3 ? STONE : OAK);
                g.flag(x, z, PlanGrid.F_ROOFED);
            }
        }
        final PlanPainter.Picture dark = PlanPainter.paint(g, s, List.of(), false);
        final PlanPainter.Picture lit = PlanPainter.paint(g, s, List.of(PlanPainter.Lamp.full(PlanPainter.pack(2, 1))), false);
        assertNotEquals(px(dark, 2 * s + 4, s + 4), px(lit, 2 * s + 4, s + 4), "the lamp's own block is lit");
        for (int y = 0; y < 3 * s; y++) {
            for (int x = 4 * s; x < 7 * s; x++) {
                assertEquals(px(dark, x, y), px(lit, x, y), "beyond the wall at " + x + "," + y);
            }
        }
        for (int y = 0; y < 3 * s; y++) {
            for (int x = 3 * s; x < 4 * s; x++) {
                assertEquals(px(dark, x, y), px(lit, x, y), "the wall itself at " + x + "," + y);
            }
        }
    }

    @Test
    @DisplayName("the Light layer paints block light, not pools: a lamp list changes nothing there")
    void lightViewHasNoPools() {
        final PlanGrid g = room();
        g.light(2, 2, 0);
        final PlanPainter.Picture a = PlanPainter.paint(g, 8, List.of(), true);
        final PlanPainter.Picture b = PlanPainter.paint(g, 8, List.of(PlanPainter.Lamp.full(PlanPainter.pack(2, 2))), true);
        for (int i = 0; i < a.argb().length; i++) {
            assertEquals(a.argb()[i], b.argb()[i]);
        }
        final PlanPainter.Picture day = PlanPainter.paint(room(), 8, List.of(), true);
        assertNotEquals(px(day, 2 * 8 + 4, 2 * 8 + 4), px(a, 2 * 8 + 4, 2 * 8 + 4), "a dark column is darker");
    }

    @Test
    @DisplayName("a flight's arrow points up it; the same flight seen from above points down it")
    void stairsPointTheWayAWalkerGoes() {
        final int s = 8;
        for (byte kind : new byte[] {PlanGrid.STAIRS, PlanGrid.STAIRS_BELOW}) {
            final PlanGrid g = new PlanGrid(0, 0, 6, 1);
            for (int x = 0; x < 6; x++) {
                g.set(x, 0, PlanGrid.FLOOR, OAK);
                g.flag(x, 0, PlanGrid.F_ROOFED);
            }
            for (int x = 1; x < 5; x++) {
                g.set(x, 0, kind, STONE);
                g.dir(x, 0, PlanGrid.EAST);
                g.flag(x, 0, PlanGrid.F_ROOFED);
            }
            final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
            final int mid = s / 2;

            final int tipEast = 5 * s - 3;
            final int tipWest = s + 2;
            final int shaft = px(p, 3 * s, mid);
            if (kind == PlanGrid.STAIRS) {
                assertEquals(shaft, px(p, tipEast - 1, mid - 1), "an up flight's head is at its east end");
            } else {
                assertEquals(shaft, px(p, tipWest + 1, mid - 1), "seen from above the head is at the west end");
            }
        }
    }

    @Test
    @DisplayName("every scale MapFit can choose paints, and every column stays inside its own block")
    void everyScalePaints() {
        for (int s : new int[] {4, 5, 6, 7, 8, 10, 12, 16}) {
            final PlanGrid g = room();
            g.set(2, 4, PlanGrid.DOOR, OAK);
            g.doorEdges(2, 4, PlanGrid.NORTH, PlanGrid.EAST);
            g.set(2, 0, PlanGrid.WINDOW, STONE);
            g.set(1, 1, PlanGrid.RAISED, 0x993333);
            g.sym(1, 1, PlanGrid.SYM_BED);
            g.dir(1, 1, PlanGrid.NORTH);
            g.flag(1, 1, PlanGrid.F_HEAD);
            g.set(3, 3, PlanGrid.RAISED, OAK);
            g.sym(3, 3, PlanGrid.SYM_CHEST);
            g.dir(3, 3, PlanGrid.SOUTH);
            g.settle();
            final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(PlanPainter.Lamp.full(PlanPainter.pack(2, 2))), false);
            assertEquals(5 * s, p.w());
            assertEquals(5 * s, p.h());
            for (int c : p.argb()) {
                assertEquals(0xFF, c >>> 24, "every pixel of a painted storey is opaque at scale " + s);
            }
        }
    }

    private static PlanGrid everything() {
        final PlanGrid g = new PlanGrid(0, 0, 12, 3);
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 3; z++) {
                g.set(x, z, PlanGrid.FLOOR, OAK);
                g.under(x, z, OAK);
                g.flag(x, z, PlanGrid.F_ROOFED);
            }
        }
        final byte[] raisedSyms = {PlanGrid.SYM_BENCH, PlanGrid.SYM_ANVIL, PlanGrid.SYM_CAULDRON, PlanGrid.SYM_LECTERN,
                PlanGrid.SYM_BOOK, PlanGrid.SYM_LANTERN, PlanGrid.SYM_CANDLE};
        for (int i = 0; i < raisedSyms.length; i++) {
            g.set(i, 1, PlanGrid.RAISED, STONE);
            g.sym(i, 1, raisedSyms[i]);
            g.dir(i, 1, PlanGrid.NORTH);
        }
        g.accent(2, 1, 0x4040FF);
        final byte[] floorSyms = {PlanGrid.SYM_CANDLE, PlanGrid.SYM_LANTERN, PlanGrid.SYM_CROP, PlanGrid.SYM_CANE};
        for (int i = 0; i < floorSyms.length; i++) {
            g.sym(7 + i, 1, floorSyms[i]);
        }
        g.flag(7, 1, PlanGrid.F_LIT);
        g.flag(9, 1, PlanGrid.F_FURROW | PlanGrid.F_GROWN);
        g.set(11, 1, PlanGrid.WATER, 0x4040FF);
        g.flag(11, 1, PlanGrid.F_DEEP);
        g.set(11, 0, PlanGrid.LAVA, 0xFF0000);
        g.flag(0, 0, PlanGrid.F_PATH);
        g.flag(1, 0, PlanGrid.F_CANOPY);
        g.set(2, 0, PlanGrid.WINDOW, STONE);
        g.flag(2, 0, PlanGrid.F_BARS);
        g.set(3, 0, PlanGrid.WINDOW, STONE);
        g.accent(3, 0, 0xB02E26);
        return g;
    }

    @Test
    @DisplayName("every symbol of the polish round paints at every scale, inside its block, opaque")
    void everyNewSymbolPaintsAtEveryScale() {
        for (int s : new int[] {4, 5, 6, 7, 8, 10, 12, 16}) {
            final PlanGrid g = everything();
            g.settle();
            final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
            for (int c : p.argb()) {
                assertEquals(0xFF, c >>> 24, "opaque at scale " + s);
            }

            final PlanPainter.Picture bare = PlanPainter.paint(everythingBare(), s, List.of(), false);
            for (int x = 0; x < 7 * s; x++) {
                assertEquals(px(bare, x, 2 * s + s / 2), px(p, x, 2 * s + s / 2), "row below untouched at scale " + s);
            }
        }
    }

    private static PlanGrid everythingBare() {
        final PlanGrid g = everything();
        for (int x = 0; x < 7; x++) {
            g.sym(x, 1, PlanGrid.SYM_NONE);
        }
        return g;
    }

    @Test
    @DisplayName("a dimmer lamp pools less light, and less far, than one at full")
    void dimmerPoolsLess() {
        final int s = 8;
        final PlanGrid g = new PlanGrid(0, 0, 7, 7);
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                g.set(x, z, PlanGrid.FLOOR, OAK);
                g.flag(x, z, PlanGrid.F_ROOFED);
            }
        }
        final PlanPainter.Picture off = PlanPainter.paint(g, s, List.of(), false);
        final PlanPainter.Picture full = PlanPainter.paint(g, s, List.of(PlanPainter.Lamp.full(PlanPainter.pack(3, 3))), false);
        final PlanPainter.Picture low = PlanPainter.paint(g, s,
                List.of(new PlanPainter.Lamp(new long[] {PlanPainter.pack(3, 3)}, 0.1f, PlanPainter.GLOW)), false);
        final int floor = px(off, 3 * s + 4, 3 * s + 4);
        final int cFull = px(full, 3 * s + 4, 3 * s + 4);
        final int cLow = px(low, 3 * s + 4, 3 * s + 4);
        assertNotEquals(floor, cLow, "the lamp's own block is lit even at the lowest notch");
        assertTrue(distance(cLow, floor) < distance(cFull, floor), "but less brightly than at full");

        final int farX = 3 * s + 4 + Math.round(1.5f * s);
        assertNotEquals(floor, px(full, farX, 3 * s + 4), "a full lamp reaches 1.5 blocks");
        assertEquals(floor, px(low, farX, 3 * s + 4), "a lamp at 10% does not");
    }

    private static int distance(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
                + Math.abs((a & 255) - (b & 255));
    }

    @Test
    @DisplayName("the dimmer's top notch is the full pool: a lamp at mode 9 of 10 paints exactly as Lamp.full")
    void topNotchIsTheFullPool() {
        final int s = 8;
        final PlanPainter.Picture full = PlanPainter.paint(room(), s,
                List.of(PlanPainter.Lamp.full(PlanPainter.pack(2, 2))), false);
        final PlanPainter.Picture top = PlanPainter.paint(room(), s,
                List.of(new PlanPainter.Lamp(new long[] {PlanPainter.pack(2, 2)}, LampLight.level(9, 10),
                        PlanPainter.GLOW)), false);
        assertArrayEquals(full.argb(), top.argb());

        final PlanPainter.Picture none = PlanPainter.paint(room(), s, List.of(), false);
        final PlanPainter.Picture floor = PlanPainter.paint(room(), s, List.of(), false);
        assertArrayEquals(none.argb(), floor.argb());
        assertNotEquals(px(none, 2 * s + 4, 2 * s + 4), px(full, 2 * s + 4, 2 * s + 4), "and the full pool shows");
    }

    @Test
    @DisplayName("a lamp burning a dye pools that colour: a red lamp's floor is redder than a white lamp's")
    void dyeTintsThePool() {
        final int s = 8;
        final PlanGrid g = room();
        final PlanPainter.Picture warm = PlanPainter.paint(g, s, List.of(PlanPainter.Lamp.full(PlanPainter.pack(2, 2))), false);
        final PlanPainter.Picture red = PlanPainter.paint(g, s,
                List.of(new PlanPainter.Lamp(new long[] {PlanPainter.pack(2, 2)}, 1f, 0xB02E26)), false);
        final int w = px(warm, 2 * s + 4, 2 * s + 4);
        final int r = px(red, 2 * s + 4, 2 * s + 4);
        assertTrue(((r >> 8) & 255) < ((w >> 8) & 255), "less green under the red lamp");
        assertTrue((r & 255) < (w & 255), "less blue under the red lamp");
    }

    @Test
    @DisplayName("a shut door stops a lamp's light; the same door standing open lets it through")
    void shutDoorStopsLight() {
        final int s = 8;

        final PlanGrid g = new PlanGrid(0, 0, 7, 3);
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 3; z++) {
                g.set(x, z, x == 3 ? PlanGrid.WALL : PlanGrid.FLOOR, x == 3 ? STONE : OAK);
                g.under(x, z, OAK);
                g.flag(x, z, PlanGrid.F_ROOFED);
            }
        }
        g.set(3, 1, PlanGrid.DOOR, OAK);
        g.doorEdges(3, 1, PlanGrid.WEST, PlanGrid.NORTH);
        final PlanPainter.Picture dark = PlanPainter.paint(g, s, List.of(), false);
        final PlanPainter.Picture shut = PlanPainter.paint(g, s, List.of(PlanPainter.Lamp.full(PlanPainter.pack(2, 1))), false);
        assertEquals(px(dark, 4 * s + 2, s + 4), px(shut, 4 * s + 2, s + 4), "beyond the shut door: dark");
        g.flag(3, 1, PlanGrid.F_OPEN);
        final PlanPainter.Picture open = PlanPainter.paint(g, s, List.of(PlanPainter.Lamp.full(PlanPainter.pack(2, 1))), false);
        assertNotEquals(px(dark, 4 * s + 2, s + 4), px(open, 4 * s + 2, s + 4), "through the open door: lit");
    }

    @Test
    @DisplayName("a strip lamp's run pools one capsule: the light beside the middle of the run equals the light beside its end")
    void runPoolsOneCapsule() {
        final int s = 8;
        final PlanGrid g = new PlanGrid(0, 0, 9, 5);
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 5; z++) {
                g.set(x, z, PlanGrid.FLOOR, OAK);
                g.flag(x, z, PlanGrid.F_ROOFED);
            }
        }
        final long[] run = {PlanPainter.pack(2, 2), PlanPainter.pack(3, 2), PlanPainter.pack(4, 2), PlanPainter.pack(5, 2),
                PlanPainter.pack(6, 2)};
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(PlanPainter.Lamp.full(run)), false);
        final int y = s + 2;
        for (int x = 2; x <= 6; x++) {
            assertEquals(px(p, 4 * s + 4, y), px(p, x * s + 4, y), "one block north of cell " + x);

            if (x < 6) {
                assertEquals(px(p, 4 * s + 4, y), px(p, x * s + 8, y), "between cells " + x + " and " + (x + 1));
            }
        }
    }

    @Test
    @DisplayName("a wall ends at a door with a jamb: its edge is drawn on the side that faces the door")
    void wallHasJambAtDoor() {
        final int s = 8;
        final PlanGrid g = room();
        g.set(2, 4, PlanGrid.DOOR, OAK);
        g.doorEdges(2, 4, PlanGrid.NORTH, PlanGrid.WEST);
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
        final int edge = PlanPainter.shade(STONE, PlanPainter.LOWEST);
        for (int j = 0; j < s; j++) {
            assertEquals(edge, px(p, 2 * s - 1, 4 * s + j), "the wall west of the door, its east face, row " + j);
            assertEquals(edge, px(p, 3 * s, 4 * s + j), "the wall east of the door, its west face, row " + j);
        }

        assertNotEquals(edge, px(p, s - 1, 4 * s + 4));
    }

    @Test
    @DisplayName("seats in a row facing the same way are one sofa: no edge between them, a seam instead")
    void seatsInARowAreOneSofa() {
        final int s = 8;

        final PlanGrid g = new PlanGrid(0, 0, 7, 5);
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 5; z++) {
                final boolean edge = x == 0 || z == 0 || x == 6 || z == 4;
                g.set(x, z, edge ? PlanGrid.WALL : PlanGrid.FLOOR, edge ? STONE : OAK);
                g.under(x, z, OAK);
                g.flag(x, z, PlanGrid.F_ROOFED);
            }
        }
        for (int x = 2; x <= 4; x++) {
            g.set(x, 2, PlanGrid.RAISED, 0x993333);
            g.sym(x, 2, PlanGrid.SYM_SEAT);
            g.dir(x, 2, PlanGrid.NORTH);
        }
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
        final int edge = PlanPainter.shade(0x993333, PlanPainter.LOWEST);

        assertEquals(edge, px(p, 2 * s, 2 * s + 5));
        assertEquals(edge, px(p, 5 * s - 1, 2 * s + 5));
        assertNotEquals(edge, px(p, 3 * s, 2 * s + 5), "no full edge where the next cushion joins");
        assertNotEquals(px(p, 3 * s + 3, 2 * s + 5), px(p, 3 * s - 1, 2 * s + 5), "but a seam");

        g.dir(3, 2, PlanGrid.SOUTH);
        final PlanPainter.Picture q = PlanPainter.paint(g, s, List.of(), false);
        assertEquals(edge, px(q, 3 * s, 2 * s + 5), "a seat facing the other way is edged");
    }

    @Test
    @DisplayName("a table of several blocks has one rim round the whole top")
    void tableTopIsOnePiece() {
        final int s = 8;
        final PlanGrid g = room();
        g.set(1, 2, PlanGrid.RAISED, OAK);
        g.sym(1, 2, PlanGrid.SYM_TABLE);
        g.set(2, 2, PlanGrid.RAISED, OAK);
        g.sym(2, 2, PlanGrid.SYM_TABLE);
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
        final int top = PlanPainter.shade(OAK, PlanPainter.HIGH);
        final int rim = PlanPainter.mix(top, PlanPainter.shade(OAK, PlanPainter.LOWEST), 0.45f);
        assertEquals(rim, px(p, s + 1, 2 * s + 4), "the rim on the table's west side");
        assertEquals(rim, px(p, 3 * s - 2, 2 * s + 4), "and its east side");
        assertEquals(top, px(p, 2 * s - 2, 2 * s + 4), "none where the two blocks join");
        assertEquals(top, px(p, 2 * s + 1, 2 * s + 4));
    }

    @Test
    @DisplayName("iron bars are a window with the bars as iron points down the wall's centre; stained glass tints its pane")
    void barredAndStainedWindows() {
        final int s = 8;
        final PlanGrid g = room();
        g.set(2, 0, PlanGrid.WINDOW, STONE);
        g.flag(2, 0, PlanGrid.F_BARS);
        g.set(3, 0, PlanGrid.WINDOW, STONE);
        g.accent(3, 0, 0xB02E26);
        g.settle();
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
        assertEquals(PlanPainter.IRON, px(p, 2 * s, s / 2), "a bar seen end on");
        assertNotEquals(PlanPainter.IRON, px(p, 2 * s + 1, s / 2), "the gap between bars");
        assertNotEquals(PlanPainter.GLASS, px(p, 2 * s + 1, 1), "no glass in a barred opening");
        final int stained = px(p, 3 * s + 1, 1);
        assertNotEquals(PlanPainter.GLASS, stained);
        assertTrue(((stained >> 16) & 255) > ((stained >> 8) & 255), "red glass reads red");
    }

    @Test
    @DisplayName("deep water is darker than a block of it; lava casts its light on the ground beside it")
    void deepWaterAndLavaCast() {
        final int s = 8;
        final PlanGrid g = new PlanGrid(0, 0, 4, 1);
        g.set(0, 0, PlanGrid.WATER, 0x4040FF);
        g.set(1, 0, PlanGrid.WATER, 0x4040FF);
        g.flag(1, 0, PlanGrid.F_DEEP);
        g.set(2, 0, PlanGrid.GROUND, 0x7FB238);
        g.set(3, 0, PlanGrid.LAVA, 0xFF0000);
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
        final int shallow = px(p, 3, 4);
        final int deep = px(p, s + 3, 4);
        assertTrue(PlanPainter.luma(deep) < PlanPainter.luma(shallow), "deeper is darker");
        final int nearLava = px(p, 3 * s - 1, 4);
        final int farFromLava = px(p, 2 * s + 1, 4);
        assertTrue(((nearLava >> 16) & 255) > ((farFromLava >> 16) & 255), "the ground beside lava takes its cast");
    }

    @Test
    @DisplayName("a canopy tints the ground under it and ends in a dotted edge; a path is edged where it meets grass")
    void canopyAndPath() {
        final int s = 8;
        final PlanGrid g = new PlanGrid(0, 0, 4, 1);
        for (int x = 0; x < 4; x++) {
            g.set(x, 0, PlanGrid.GROUND, 0x7FB238);
        }
        g.flag(1, 0, PlanGrid.F_CANOPY);
        g.flag(3, 0, PlanGrid.F_PATH);
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
        final PlanPainter.Picture bare = PlanPainter.paint(plainGround(), s, List.of(), false);
        assertNotEquals(px(bare, s + 4, 4), px(p, s + 4, 4), "under the canopy the ground is tinted");
        assertEquals(px(bare, 2 * s + 4, 4), px(p, 2 * s + 4, 4), "beside it, not");
        final int edge = px(p, s, 4);
        final int gap = px(p, s, 5);
        assertNotEquals(edge, gap, "the canopy's edge is dotted");

        assertTrue(PlanPainter.luma(px(p, 3 * s, 4)) < PlanPainter.luma(px(p, 3 * s + 3, 4)), "the path is edged");
    }

    private static PlanGrid plainGround() {
        final PlanGrid g = new PlanGrid(0, 0, 4, 1);
        for (int x = 0; x < 4; x++) {
            g.set(x, 0, PlanGrid.GROUND, 0x7FB238);
        }
        return g;
    }

    @Test
    @DisplayName("farmland is drawn in furrows, and a label may sit only on open floor")
    void furrowsAndClearFloor() {
        final int s = 8;
        final PlanGrid g = new PlanGrid(0, 0, 2, 1);
        g.set(0, 0, PlanGrid.GROUND, 0x976D4D);
        g.flag(0, 0, PlanGrid.F_FURROW);
        g.set(1, 0, PlanGrid.GROUND, 0x976D4D);
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
        assertNotEquals(px(p, 3, 0), px(p, 3, 1), "furrow rows alternate");
        assertEquals(px(p, 3, 0), px(p, 3, 2));
        assertTrue(g.isClearFloor(1, 0));
        g.sym(1, 0, PlanGrid.SYM_CROP);
        assertFalse(g.isClearFloor(1, 0), "a crop is something to hide");
        final PlanGrid r = room();
        assertTrue(r.isClearFloor(2, 2));
        assertFalse(r.isClearFloor(0, 0), "a wall is not floor");
    }

    @Test
    @DisplayName("ground seen far below a storey is fainter than ground beside it, and never a hole's black")
    void groundFarBelowIsFaded() {
        final int s = 8;
        final PlanGrid g = new PlanGrid(0, 0, 3, 1);
        g.set(0, 0, PlanGrid.GROUND, 0x7FB238);
        g.set(1, 0, PlanGrid.GROUND, 0x7FB238);
        g.flag(1, 0, PlanGrid.F_DEEP);
        g.set(2, 0, PlanGrid.HOLE, -1);
        g.flag(2, 0, PlanGrid.F_DEEP);
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
        final int beside = px(p, 4, 4);
        final int below = px(p, s + 4, 4);
        final int hole = px(p, 2 * s + 4, 4);

        assertTrue(distance(below, PlanPainter.PAPER) < distance(beside, PlanPainter.PAPER), "faded toward the paper");
        assertTrue(PlanPainter.luma(below) > PlanPainter.luma(hole) + 60, "nothing like a hole");

        assertTrue(((below >> 8) & 255) * ((PlanPainter.PAPER >> 16) & 255)
                > ((below >> 16) & 255) * ((PlanPainter.PAPER >> 8) & 255), "still a lawn");

        for (int y = 0; y < s; y++) {
            for (int x = s; x < 2 * s; x++) {
                assertEquals(below, px(p, x, y), "a wash, no grain, at " + x + "," + y);
            }
        }

        final PlanGrid w = new PlanGrid(0, 0, 1, 1);
        w.set(0, 0, PlanGrid.GROUND, 0x4040FF);
        w.flag(0, 0, PlanGrid.F_DEEP);
        final int pond = px(PlanPainter.paint(w, s, List.of(), false), 4, 4);
        assertTrue((pond & 255) > ((pond >> 16) & 255), "blue leads red");
        assertTrue((pond & 255) - ((pond >> 8) & 255) < 40, "and it is a grey-blue, not a saturated one");
    }

    @Test
    @DisplayName("where the ground seen below steps up or down, a faint edge keeps the shape of a mound")
    void groundBelowKeepsItsShape() {
        final int s = 8;
        final PlanGrid g = new PlanGrid(0, 0, 3, 1);
        for (int x = 0; x < 3; x++) {
            g.set(x, 0, PlanGrid.GROUND, 0x7FB238);
            g.flag(x, 0, PlanGrid.F_DEEP);
            g.dir(x, 0, (byte) (x == 1 ? 4 : 5));
        }
        final PlanPainter.Picture p = PlanPainter.paint(g, s, List.of(), false);
        final int wash = px(p, 4, 4);
        assertNotEquals(wash, px(p, s, 4), "the mound's west edge is drawn");
        assertNotEquals(wash, px(p, 2 * s - 1, 4), "and its east edge");
        assertEquals(wash, px(p, s + 4, 4), "its top is the same wash");
        assertEquals(wash, px(p, 1, 4), "and so is the lawn beside it");
    }

    @Test
    @DisplayName("a column nobody has loaded stays clear, so the paper shows through")
    void unloadedStaysClear() {
        final PlanGrid g = new PlanGrid(0, 0, 2, 1);
        g.set(0, 0, PlanGrid.FLOOR, OAK);
        final PlanPainter.Picture p = PlanPainter.paint(g, 4, List.of(), false);
        assertEquals(0, p.argb()[4] >>> 24, "the second column was never read");
        assertEquals(0xFF, p.argb()[0] >>> 24);
    }

    @Test
    @DisplayName("a wall standing alone is a post, drawn round; a bookshelf faces the room")
    void settleReadsNeighbours() {
        final PlanGrid g = new PlanGrid(0, 0, 3, 3);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                g.set(x, z, PlanGrid.GROUND, 0x7FB238);
            }
        }
        g.set(1, 1, PlanGrid.WALL, 0x6B5A3A);
        g.settle();
        assertEquals(PlanGrid.POST, g.kind(1, 1));

        final PlanGrid h = room();
        h.set(3, 2, PlanGrid.RAISED, OAK);
        h.sym(3, 2, PlanGrid.SYM_SHELF);
        h.settle();
        assertEquals(PlanGrid.WEST, h.dir(3, 2), "against the east wall, its books face west into the room");
    }
}
