package com.createstats.book;

import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.FigureWorld;
import com.createstats.book.scene.WireFigures;
import com.createstats.power.ConnectorTier;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireFiguresTest {
    private static BlockFigure.Placement at(BlockFigure f, int[] cell) {
        for (final BlockFigure.Placement p : f.placements()) {
            if (p.x() == cell[0] && p.y() == cell[1] && p.z() == cell[2]) {
                return p;
            }
        }
        return null;
    }

    @Test
    void everySpanHangsFromAConnectorsTerminal() {
        int spans = 0;
        for (final BlockFigure f : WireFigures.all()) {
            final FigureWorld w = FigureWorld.of(f);
            for (final BlockFigure.Tie s : w.spans()) {
                final BlockFigure.Placement from = at(f, s.from());
                assertTrue(from != null && FigureWorld.CONNECTORS.containsKey(from.block()),
                        f.id() + ": a span starts on a connector");
                final List<Vec3> points = w.spanPoints(s);
                final Vec3 a = w.terminalPoint(new BlockPos(s.from()[0], s.from()[1], s.from()[2]));
                final Vec3 b = w.terminalPoint(new BlockPos(s.to()[0], s.to()[1], s.to()[2]));

                final double off = Math.max(points.get(0).distanceTo(a), points.get(points.size() - 1).distanceTo(b));
                assertTrue(off < 1.0e-3, f.id() + ": the wire ends " + off + " blocks off its terminals");
                spans++;
            }
        }
        assertTrue(spans >= 12, "spans checked: " + spans);
    }

    @Test
    void eachGradeIsStrungBetweenConnectorsItFills() {
        final BlockFigure f = WireFigures.WIRES;
        for (final BlockFigure.Tie s : FigureWorld.of(f).spans()) {
            for (final int[] end : new int[][]{s.from(), s.to()}) {
                final ConnectorTier c = FigureWorld.CONNECTORS.get(at(f, end).block());
                assertTrue(c.multiplier() >= s.tier().multiplier(), s.tier() + " passes whole through " + c);
                for (final ConnectorTier lesser : ConnectorTier.values()) {
                    if (lesser.multiplier() < c.multiplier()) {
                        assertTrue(lesser.multiplier() < s.tier().multiplier(), s.tier() + " on more than it needs");
                    }
                }
            }
        }
    }

    @Test
    void theGradePinsSitOnTheirOwnWires() {
        final BlockFigure f = WireFigures.WIRES;
        final FigureWorld w = FigureWorld.of(f);
        for (final WireTier tier : WireTier.values()) {
            final String id = tier.name().toLowerCase(Locale.ROOT);
            final BlockFigure.Pin pin = f.pins().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
            final BlockFigure.Tie span = w.spans().stream().filter(s -> s.tier() == tier).findFirst().orElseThrow();
            double nearest = Double.MAX_VALUE;
            for (final Vec3 p : w.spanPoints(span)) {
                nearest = Math.min(nearest, Math.sqrt(Math.pow(p.x * 16 - pin.x(), 2) + Math.pow(p.y * 16 - pin.y(), 2)
                        + Math.pow(p.z * 16 - pin.z(), 2)));
            }
            assertTrue(nearest <= 0.5, id + "'s pin is " + nearest + " px off its wire");
        }
    }

    @Test
    void theTensionsHangInTheirOrder() {
        final FigureWorld w = FigureWorld.of(WireFigures.TENSIONS);
        final List<BlockFigure.Tie> spans = w.spans();
        assertEquals(WireTension.values().length, spans.size());
        double above = Double.MAX_VALUE;
        for (int i = 0; i < spans.size(); i++) {
            final BlockFigure.Tie s = spans.get(i);
            assertEquals(WireTension.values()[i], s.tension());
            final List<Vec3> points = w.spanPoints(s);
            final Vec3 a = points.get(0);
            final Vec3 b = points.get(points.size() - 1);
            double sag = 0.0;
            for (final Vec3 p : points) {
                sag = Math.max(sag, (a.y + (b.y - a.y) * (p.x - a.x) / (b.x - a.x)) - p.y);
            }
            final double designed = s.tension().sagOver(a.distanceTo(b));
            assertEquals(designed, sag, designed * 0.05, s.tension() + " hangs its level's sag");
            assertTrue(sag < above, s.tension() + " hangs tighter than the one above it");
            above = sag;
        }
    }

    @Test
    void theRingsHubHoldsEveryGradeAndLevel() {
        final List<BlockFigure.Tie> spans = WireFigures.HUB_SPANS;
        assertEquals(List.of(WireTier.values()), spans.stream().map(BlockFigure.Tie::tier).toList());
        assertEquals(WireTension.values().length, spans.stream().map(BlockFigure.Tie::tension).distinct().count());
        final com.createstats.book.scene.BlockView v = WireFigures.RING_WORLD.view();
        for (final BlockFigure.Tie s : spans) {
            final double[] far = v.project(s.to()[0] + 0.5, s.to()[1] + 0.5, s.to()[2] + 0.5);
            assertTrue(far[0] < 0 || far[1] < 0 || far[0] > WireFigures.RING_WORLD.width()
                    || far[1] > WireFigures.RING_WORLD.height(), s.tier() + "'s far end stands off the plate");
        }
    }
}
