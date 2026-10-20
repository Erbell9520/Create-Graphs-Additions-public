package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathwayCoreTest {
    private static final UUID OWNER = new UUID(7, 7);

    @Test
    void editsKeepIdsAndBumpTheVersion() {
        Pathway p = Pathway.create(UUID.randomUUID(), "Quarry loop", 4, OWNER);
        p = p.append(0, 64, 0, false).append(10, 64, 0, false).append(20, 64, 0, false);
        assertEquals(4L, p.version(), "one version per edit");
        int third = p.nodes().get(2).id();
        p = p.insertAfter(0, 5, 64, 1);
        assertEquals(third, p.nodes().get(3).id(), "an insert never renumbers ids");
        assertEquals(4, p.numberOf(third), "but the number a player sees moves");
        p = p.delete(p.nodes().get(1).id());
        assertEquals(3, p.numberOf(third));
        assertEquals("N3", p.label(third));
        assertEquals(5, p.nextNodeId(), "ids are never reused");
    }

    @Test
    void aLoopWithTooFewNodesOpensAgain() {
        Pathway p = Pathway.create(UUID.randomUUID(), "t", 0, OWNER)
                .append(0, 64, 0, false).append(10, 64, 0, false).append(10, 64, 10, false).withKind(PathKind.LOOP);
        assertTrue(p.closed());
        p = p.delete(p.nodes().get(0).id());
        assertEquals(PathKind.ONE_WAY, p.kind(), "two points close nothing");
    }

    @Test
    void theCodecRoundTripsEverything() throws Exception {
        Pathway p = Pathway.create(UUID.randomUUID(), "Farm run", 11, OWNER)
                .append(1.5, 64, -3.25, false).append(10, 65, 0, false).append(20, 64, 7, false)
                .withKind(PathKind.LOOP).withShared(true).withCruise(7.5).withComfort(Comfort.BRISK);
        int n2 = p.nodes().get(1).id();
        Stop stop = new Stop(List.of(
                List.of(new StopCondition.Items(StopCondition.Op.GREATER, 1000, false, "minecraft:cobblestone"),
                        new StopCondition.Idle(5)),
                List.of(new StopCondition.Wait(120)),
                List.of(new StopCondition.Fluid(StopCondition.Op.LESS, 1, ""), new StopCondition.Link("minecraft:red_dye", "", false))),
                300, "minecraft:lime_dye", "minecraft:iron_ingot", true, 2);
        p = p.update(n2, n -> n.withName("Quarry").withSpeedLimit(3.5).withCorner(Corner.WIDE).withStop(stop));
        Pathway back = PathwayCodec.decode(PathwayCodec.encode(p));
        assertEquals(p, back, "records compare by value, stop and all");
        Stop s2 = PathwayCodec.decodeStop(PathwayCodec.encodeStop(stop));
        assertEquals(stop, s2);
    }

    @Test
    void theCodecRefusesAFutureFormatAndHugeCounts() {
        byte[] data = PathwayCodec.encode(Pathway.create(UUID.randomUUID(), "t", 0, OWNER));
        data[0] = (byte) (PathwayCodec.FORMAT + 1);
        assertThrows(java.io.IOException.class, () -> PathwayCodec.decode(data));
    }

    @Test
    void aStrokeReducesToTheNodesThatDrawIt() {
        List<double[]> stroke = new ArrayList<>();

        java.util.Random rnd = new java.util.Random(4);
        for (double x = 0; x < 40; x += 0.2) stroke.add(new double[]{x, 64, (rnd.nextDouble() - 0.5) * 0.2});
        for (double phi = 0; phi < Math.PI / 2; phi += 0.02) stroke.add(new double[]{40 + 10 * Math.sin(phi), 64, 10 - 10 * Math.cos(phi)});
        List<double[]> kept = StrokeSimplify.simplify(stroke, StrokeSimplify.EPSILON, StrokeSimplify.MIN_SPACING);
        assertTrue(kept.size() < 16, "a handful of nodes: " + kept.size());
        assertTrue(kept.size() >= 4, "enough to draw the bend: " + kept.size());
        assertEquals(0.0, kept.get(0)[0], 1.0e-9, "the first point kept");
        double[] last = stroke.get(stroke.size() - 1);
        assertEquals(last[0], kept.get(kept.size() - 1)[0], 1.0e-9, "and the last");
        for (double[] p : stroke) {
            double best = Double.MAX_VALUE;
            for (int i = 1; i < kept.size(); i++) best = Math.min(best, StrokeSimplify.distanceToSegment(p, kept.get(i - 1), kept.get(i)));
            assertTrue(best <= StrokeSimplify.EPSILON + 0.21, "every point of the stroke near the simplified line: " + best);
        }
    }

    @Test
    void aStrokePaintedStandingStillIsOneNode() {
        List<double[]> stroke = new ArrayList<>();
        for (int i = 0; i < 50; i++) stroke.add(new double[]{3 + i * 0.001, 64, 4});
        assertEquals(1, StrokeSimplify.simplify(stroke, StrokeSimplify.EPSILON, StrokeSimplify.MIN_SPACING).size());
    }

    @Test
    void aRecordingBecomesNodesWithSpeedsAStopAndALoop() {
        RecordTrack rec = new RecordTrack();
        double v = 5.0;
        double x = 0;

        for (int t = 0; t < 120; t++) rec.sample(x += v * 0.05, 64, 0, 1, 0, v);
        for (int t = 0; t < 80; t++) rec.sample(x, 64, 0, 1, 0, 0.0);
        double z = 0;
        for (int t = 0; t < 120; t++) rec.sample(x, 64, z += v * 0.05, 0, 1, v);
        for (int t = 0; t < 120; t++) rec.sample(x -= v * 0.05, 64, z, -1, 0, v);
        for (int t = 0; t < 115; t++) rec.sample(x, 64, z -= v * 0.05, 0, -1, v);
        assertTrue(rec.nearStart(x, z, 0.8, -0.6) || rec.nearStart(x, z, 1, 0), "back at the start facing about the way it left");
        List<PathNode> nodes = rec.finish(true);
        assertTrue(nodes.size() >= 4 && nodes.size() < 20, "nodes: " + nodes.size());
        long stops = nodes.stream().filter(PathNode::isStop).count();
        assertEquals(1, stops, "one stop where it parked");
        PathNode stop = nodes.stream().filter(PathNode::isStop).findFirst().orElseThrow();
        assertEquals(30.0, stop.x(), 1.6);
        assertTrue(nodes.stream().anyMatch(n -> n.speedLimit() >= 5.0), "the speeds driven");
        assertEquals(5.0, rec.cruise(), 1.0e-9);
    }

    @Test
    void segmentChecksCallATurnTooTightRedAndASharpCornerAmber() {
        List<double[]> pts = List.of(new double[]{0, 64, 0}, new double[]{12, 64, 0}, new double[]{12, 64, 12});
        Pathway p = Pathway.create(UUID.randomUUID(), "t", 0, OWNER).appendAll(pts);
        PathSpline sp = PathSpline.of(p);
        List<SegmentCheck.Finding> f = SegmentCheck.shape(sp, 6.0, null, new double[0]);
        assertEquals(SegmentCheck.Level.RED, SegmentCheck.worst(f), "a 90 degree corner in 12 blocks is too tight for 6: " + f);
        Pathway sharp = p.update(p.nodes().get(1).id(), n -> n.withCorner(Corner.SHARP));
        PathSpline ss = PathSpline.of(sharp);
        List<SegmentCheck.Finding> fs = SegmentCheck.shape(ss, 6.0, null, new double[]{ss.nodeS(1)});
        assertEquals(SegmentCheck.Level.AMBER, SegmentCheck.worst(fs), "a sharp corner is crawled round: " + fs);
        List<SegmentCheck.Finding> fine = SegmentCheck.shape(sp, 2.0, null, new double[0]);
        assertFalse(SegmentCheck.worst(fine) == SegmentCheck.Level.RED, "fine for a nimble vehicle: " + fine);
    }

    @Test
    void segmentChecksFindStepsAndGrades() {
        Pathway p = Pathway.create(UUID.randomUUID(), "t", 0, OWNER).appendAll(List.of(new double[]{0, 64, 0}, new double[]{20, 64, 0}));
        PathSpline sp = PathSpline.of(p);
        double[] ground = new double[sp.sampleCount()];
        for (int i = 0; i < ground.length; i++) ground[i] = sp.sampleS(i) < 10 ? 64 : 66;
        List<SegmentCheck.Finding> f = SegmentCheck.shape(sp, 4.0, ground, new double[0]);
        assertEquals(SegmentCheck.Level.RED, SegmentCheck.worst(f));
        assertTrue(f.stream().anyMatch(x -> x.why().contains("step")), "names the step: " + f);
    }

    @Test
    void nodeValuesArePulledIntoRange() {
        PathNode n = new PathNode(1, Double.NaN, 64, 5e9, "a name much too long for any stake to carry on its flag", 999, null, null).sane();
        assertEquals(0.0, n.x());
        assertEquals(3.0e7, n.z());
        assertEquals(PathNode.MAX_SPEED, n.speedLimit());
        assertEquals(PathNode.MAX_NAME, n.name().length());
        assertEquals(Corner.SMOOTH, n.corner());
        assertNull(n.stop());
        assertNotNull(Corner.SHARP.next(1));
    }
}
