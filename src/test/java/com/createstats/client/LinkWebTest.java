package com.createstats.client;

import com.createstats.client.LinkWeb.Link;
import com.createstats.client.LinkWeb.Mark;
import com.createstats.client.LinkWeb.Selection;
import com.createstats.client.LinkWeb.Spot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkWebTest {
    private static final double P = 0.5;

    private static Spot at(int x, int y, int z) {
        return new Spot(x, y, z);
    }

    private static Link link(Spot controller, Spot fixture) {
        return new Link(controller, fixture);
    }

    private static LinkWeb web(Link... links) {
        return LinkWeb.near(List.of(links), P, P, P);
    }

    @Test
    void aBindingWithOneEndInRangeIsDrawnWholeThoughTheOtherIsFarther() {
        final Link run = link(at(10, 0, 0), at(50, 0, 0));
        assertEquals(List.of(run), web(run).links(),
                "a switch 10 blocks off driving a lamp 50 off is one run, and the near end puts it on screen");
    }

    @Test
    void aBindingWithNoEndInRangeIsNotDrawn() {
        assertTrue(web(link(at(40, 0, 0), at(50, 0, 0))).isEmpty(),
                "neither end within 32 blocks: nothing to draw for this player");
    }

    @Test
    void aFarEndPastReachDropsTheBinding() {
        assertTrue(web(link(at(5, 0, 0), at(70, 0, 0))).isEmpty(),
                "a run reaching 70 blocks away is cut, not drawn towards a point nobody can see");
        assertEquals(1, web(link(at(5, 0, 0), at(63, 0, 0))).links().size(), "63 blocks is inside the reach");
    }

    @Test
    void aSableSubLevelEndIsNeverDrawn() {
        final Spot plotLamp = at(20_480_000 + 3, 70, 20_480_000 + 5);
        final Link toShip = link(at(2, 0, 0), plotLamp);
        final Link toPorch = link(at(2, 0, 0), at(4, 0, 0));
        assertEquals(List.of(toPorch), web(toShip, toPorch).links(),
                "the ground switch's run to the ship is left out; its run to the porch lamp stays");
        assertTrue(web(link(plotLamp, at(20_480_000, 70, 20_480_000))).isEmpty(),
                "a binding wholly aboard a ship is nowhere near a player in world space");
    }

    @Test
    void rangeIsMeasuredBetweenBlockCentresAndIncludesItsEdge() {
        assertEquals(1, web(link(at(32, 0, 0), at(33, 0, 0))).links().size(), "an end exactly 32 away counts");
        assertTrue(web(link(at(33, 0, 0), at(34, 0, 0))).isEmpty(), "33 does not");
        assertTrue(LinkWeb.inRange(link(at(0, 0, 32), at(0, 64, 0)), P, P, P), "64 is the reach, inclusive");
    }

    @Test
    void eachPairIsDrawnOnceAndAHostBoundToItselfNever() {
        final Link run = link(at(1, 0, 0), at(2, 0, 0));
        assertEquals(List.of(run), web(run, link(at(1, 0, 0), at(2, 0, 0)), link(at(3, 0, 0), at(3, 0, 0))).links());
    }

    @Test
    void theNearestBindingsComeFirst() {
        final Link far = link(at(20, 0, 0), at(21, 0, 0));
        final Link near = link(at(3, 0, 0), at(30, 0, 0));
        final Link middle = link(at(0, 0, 9), at(0, 0, 10));
        assertEquals(List.of(near, middle, far), web(far, near, middle).links(),
                "ordered by the nearer end, so the bead budget is spent beside the player");
    }

    @Test
    void aControllerSelectsItsFixturesInItsColour() {
        final Spot sw = at(0, 1, 0);
        final Link a = link(sw, at(3, 2, 0));
        final Link b = link(sw, at(-2, 2, 1));
        final Link other = link(at(5, 1, 5), at(6, 2, 5));
        final Selection selection = web(a, b, other).select(sw);
        assertEquals(List.of(a, b), selection.links());
        final int colour = LinkWeb.colourOf(sw);
        assertEquals(List.of(new Mark(at(3, 2, 0), colour), new Mark(at(-2, 2, 1), colour)), selection.outlined(),
                "every fixture outlined in its switch's colour");
        assertTrue(selection.contains(a) && !selection.contains(other));
    }

    @Test
    void aFixtureSelectsEveryControllerDrivingItEachInItsOwnColour() {
        final Spot lamp = at(4, 5, 0);
        final Spot top = at(0, 5, 0);
        final Spot bottom = at(0, 1, 3);
        final Selection selection = web(link(top, lamp), link(bottom, lamp)).select(lamp);
        assertEquals(2, selection.links().size(), "both runs are drawn back from the lamp");
        assertEquals(Set.of(new Mark(top, LinkWeb.colourOf(top)), new Mark(bottom, LinkWeb.colourOf(bottom))),
                new HashSet<>(selection.outlined()), "each switch outlined, each in its own colour");
        assertNotEquals(LinkWeb.colourOf(top), LinkWeb.colourOf(bottom), "and those colours differ here");
    }

    @Test
    void anUnboundBlockSelectsNothingAndTheLookedBlockIsNeverOutlined() {
        final LinkWeb w = web(link(at(0, 1, 0), at(1, 1, 0)));
        assertTrue(w.select(at(9, 9, 9)).isEmpty());
        assertTrue(w.select(null).isEmpty());
        for (final Mark mark : w.select(at(0, 1, 0)).outlined()) {
            assertNotEquals(at(0, 1, 0), mark.spot(), "the game's own box is already on the looked-at block");
        }
    }

    @Test
    void aControllersColourIsAFunctionOfWhereItStandsAlone() {
        final Spot sw = at(-37, 64, 1201);
        assertEquals(LinkWeb.colourOf(sw), LinkWeb.colourOf(new Spot(-37, 64, 1201)),
                "the same block, the same colour, every time the card comes out and for every player");
        final Set<Integer> used = new HashSet<>();
        for (int x = 0; x < 7; x++) {
            used.add(LinkWeb.colourOf(at(x, 0, 0)));
        }
        assertEquals(LinkWeb.PALETTE.length, used.size(), "a row of seven switches uses every colour once");
    }

    @Test
    void faceAndEdgeNeighboursNeverShareAColour() {
        final List<String> shared = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -4; z <= 4; z++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                final int touching = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                                if (touching == 0 || touching == 3) {
                                    continue;
                                }
                                if (LinkWeb.colourOf(at(x, y, z)) == LinkWeb.colourOf(at(x + dx, y + dy, z + dz))) {
                                    shared.add(x + "," + y + "," + z + " +" + dx + "," + dy + "," + dz);
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(shared.isEmpty(), "neighbours sharing a colour: " + shared);
    }

    @Test
    void aRowOnAnyAxisRunsThroughAllSevenColoursBeforeOneRepeats() {
        final int[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (final int[] axis : axes) {
            final Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < 7; i++) {
                seen.add(LinkWeb.colourOf(at(5 + i * axis[0], -3 + i * axis[1], 11 + i * axis[2])));
            }
            assertEquals(7, seen.size(), "seven in a row along " + axis[0] + "," + axis[1] + "," + axis[2]);
        }
    }

    private static final int[] BACKGROUNDS = {0x7D7D7D, 0xA2824E, 0x6E9B46, 0x0A0E1C};
    private static final String[] BACKGROUND_NAMES = {"stone", "oak", "grass", "night sky"};

    @Test
    void everyColourReadsOnStoneOakGrassAndTheNightSky() {
        final List<String> faint = new ArrayList<>();
        for (final int colour : LinkWeb.PALETTE) {
            for (int b = 0; b < BACKGROUNDS.length; b++) {
                final double lit = deltaE(colour, BACKGROUNDS[b]);
                final double shaded = deltaE(shade(colour, 0.72), BACKGROUNDS[b]);
                if (Math.min(lit, shaded) < 16.0) {
                    faint.add(String.format("%06X on %s: %.1f", colour, BACKGROUND_NAMES[b], Math.min(lit, shaded)));
                }
            }
        }
        assertTrue(faint.isEmpty(), "colours that would not read as a thin line: " + faint);
    }

    @Test
    void everyTwoColoursAreFarApart() {
        final List<String> close = new ArrayList<>();
        for (int i = 0; i < LinkWeb.PALETTE.length; i++) {
            for (int j = i + 1; j < LinkWeb.PALETTE.length; j++) {
                final double d = deltaE(LinkWeb.PALETTE[i], LinkWeb.PALETTE[j]);
                if (d < 21.0) {
                    close.add(String.format("%06X and %06X: %.1f", LinkWeb.PALETTE[i], LinkWeb.PALETTE[j], d));
                }
            }
        }
        assertTrue(close.isEmpty(), "two controllers' runs a player could not tell apart: " + close);
    }

    @Test
    void aStillRunHasEqualBeadsAndGapsInsideIt() {
        final double[] beads = LinkWeb.beads(1.0, 0.0);
        assertArrayEquals(new double[]{0.0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875}, beads, 1.0e-9,
                "four beads to a block, each as long as the gap after it, starting at the controller");
    }

    @Test
    void beadsNeverLeaveTheRunNorOutgrowABead() {
        for (double length = 0.01; length < 40; length += 0.37) {
            for (double phase = 0; phase < 3; phase += 0.13) {
                final double[] beads = LinkWeb.beads(length, phase);
                assertTrue(beads.length / 2 <= LinkWeb.beadCount(length), "beadCount bounds the beads");
                for (int i = 0; i < beads.length; i += 2) {
                    assertTrue(beads[i] >= 0 && beads[i + 1] <= length + 1.0e-9, "inside the run");
                    assertTrue(beads[i + 1] > beads[i], "never empty");
                    assertTrue(beads[i + 1] - beads[i] <= LinkWeb.BEAD + 1.0e-9, "never longer than a bead");
                    if (i > 0) {
                        assertTrue(beads[i] >= beads[i - 1], "in order, never overlapping");
                    }
                }
            }
        }
    }

    @Test
    void aMarchMovesTheBeadsTowardsTheFixtureAndWrapsAfterOnePeriod() {
        final double[] still = LinkWeb.beads(2.0, 0.0);
        final double[] half = LinkWeb.beads(2.0, 0.5);
        assertEquals(still[2] + LinkWeb.PERIOD / 2, half[2], 1.0e-9, "half a period on, every bead half a gap further");
        assertArrayEquals(still, LinkWeb.beads(2.0, 1.0), 1.0e-9, "one whole period draws exactly the still pattern");
        assertArrayEquals(LinkWeb.beads(2.0, 0.25), LinkWeb.beads(2.0, 3.25), 1.0e-9, "only the fraction counts");
        assertEquals(1.0, LinkWeb.marchAt(LinkWeb.MARCH_TICKS), 1.0e-9, "one period per MARCH_TICKS ticks");
    }

    @Test
    void aBeadMarchingInAtTheControllerIsClippedNotDrawnBehindIt() {
        final double[] beads = LinkWeb.beads(1.0, 0.75);
        assertEquals(0.0, beads[0], 1.0e-9, "the bead straddling the controller starts at the controller");
        assertEquals(0.0625, beads[1], 1.0e-9, "and shows only the part that has marched in");
    }

    @Test
    void aRunShorterThanABeadStillShowsOneAndAZeroRunNone() {
        assertArrayEquals(new double[]{0.0, 0.05}, LinkWeb.beads(0.05, 0.0), 1.0e-9);
        assertEquals(0, LinkWeb.beads(0.0, 0.3).length);
        assertEquals(0, LinkWeb.beadCount(0.0));
    }

    @Test
    void thePaletteIsSevenDistinctColours() {
        final Set<Integer> distinct = new HashSet<>();
        for (final int colour : LinkWeb.PALETTE) {
            distinct.add(colour);
        }
        assertEquals(7, LinkWeb.PALETTE.length);
        assertEquals(7, distinct.size());
        assertFalse(distinct.contains(0x000000), "black would vanish at night");
    }

    private static int shade(int rgb, double factor) {
        final int r = (int) (((rgb >> 16) & 0xFF) * factor);
        final int g = (int) (((rgb >> 8) & 0xFF) * factor);
        final int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static double[] lab(int rgb) {
        final double r = linear(((rgb >> 16) & 0xFF) / 255.0);
        final double g = linear(((rgb >> 8) & 0xFF) / 255.0);
        final double b = linear((rgb & 0xFF) / 255.0);
        final double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        final double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        final double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        final double fx = f(x);
        final double fy = f(y);
        final double fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double linear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 216.0 / 24389.0 ? Math.cbrt(t) : (24389.0 / 27.0 * t + 16) / 116;
    }

    private static double deltaE(int rgb1, int rgb2) {
        final double[] l1 = lab(rgb1);
        final double[] l2 = lab(rgb2);
        final double c1 = Math.hypot(l1[1], l1[2]);
        final double c2 = Math.hypot(l2[1], l2[2]);
        final double cBar = (c1 + c2) / 2;
        final double g = 0.5 * (1 - Math.sqrt(Math.pow(cBar, 7) / (Math.pow(cBar, 7) + Math.pow(25, 7))));
        final double a1 = (1 + g) * l1[1];
        final double a2 = (1 + g) * l2[1];
        final double c1p = Math.hypot(a1, l1[2]);
        final double c2p = Math.hypot(a2, l2[2]);
        final double h1 = hue(a1, l1[2]);
        final double h2 = hue(a2, l2[2]);
        final double dL = l2[0] - l1[0];
        final double dC = c2p - c1p;
        double dh = 0;
        if (c1p * c2p != 0) {
            dh = h2 - h1;
            if (dh > 180) {
                dh -= 360;
            } else if (dh < -180) {
                dh += 360;
            }
        }
        final double dH = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dh / 2));
        final double lBar = (l1[0] + l2[0]) / 2;
        final double cBarP = (c1p + c2p) / 2;
        double hBar = h1 + h2;
        if (c1p * c2p != 0) {
            hBar = Math.abs(h1 - h2) <= 180 ? (h1 + h2) / 2 : (h1 + h2 < 360 ? (h1 + h2 + 360) / 2 : (h1 + h2 - 360) / 2);
        }
        final double t = 1 - 0.17 * Math.cos(Math.toRadians(hBar - 30)) + 0.24 * Math.cos(Math.toRadians(2 * hBar))
                + 0.32 * Math.cos(Math.toRadians(3 * hBar + 6)) - 0.20 * Math.cos(Math.toRadians(4 * hBar - 63));
        final double dTheta = 30 * Math.exp(-Math.pow((hBar - 275) / 25, 2));
        final double rc = 2 * Math.sqrt(Math.pow(cBarP, 7) / (Math.pow(cBarP, 7) + Math.pow(25, 7)));
        final double sl = 1 + 0.015 * Math.pow(lBar - 50, 2) / Math.sqrt(20 + Math.pow(lBar - 50, 2));
        final double sc = 1 + 0.045 * cBarP;
        final double sh = 1 + 0.015 * cBarP * t;
        final double rt = -Math.sin(Math.toRadians(2 * dTheta)) * rc;
        return Math.sqrt(Math.pow(dL / sl, 2) + Math.pow(dC / sc, 2) + Math.pow(dH / sh, 2)
                + rt * (dC / sc) * (dH / sh));
    }

    private static double hue(double a, double b) {
        final double h = Math.toDegrees(Math.atan2(b, a));
        return h < 0 ? h + 360 : h;
    }
}
