package com.createstats.client.ponder.seismic;

import com.createstats.blockentity.SeismicScannerBE;
import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicWave;
import com.createstats.seismic.VeinVisibility;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicPonderSurveyTest {
    private static double[] middle(int[] rig) {
        return new double[] {rig[0] + 0.5, rig[1] + 1.0, rig[2] + 0.5};
    }

    private static double dist(double[] a, Vec3 b) {
        return Math.sqrt(Math.pow(a[0] - b.x, 2) + Math.pow(a[1] - b.y, 2) + Math.pow(a[2] - b.z, 2));
    }

    @Test
    void theCutAwaysVeinsAnswerOneATimeNearestFirst() {
        final SeismicPonderSurvey.Survey s = SeismicPonderBeats.Survey.ground();
        assertEquals(SeismicStage.RIG_VEINS.length, s.veins().size(), "one vein per connected set of iron");
        int last = 0;
        double near = 0;
        for (SeismicPonderSurvey.Vein v : s.veins()) {
            assertTrue(v.echo() > last, "each echo on its own tick, nearest first: " + v.label() + " at " + v.echo());
            assertTrue(v.nearest() >= near, "nearest first");
            last = v.echo();
            near = v.nearest();
            assertEquals(SeismicWave.echoTick(v.nearest()), v.echo(), "the echo is the two-way time");
            assertTrue(v.label().startsWith("IRON "), v.label());
            assertEquals(v.count(), v.cells().length, "every block of it");
            assertTrue(v.runs().length > 0 && v.runs().length % 5 == 0, "an outline");
        }

        assertEquals(s.veins().size() * 2, s.echoes().length);
        assertEquals(1.0, s.strength(), 1e-9, "a full-mast blow throws the pen to its stop");
        assertEquals(SeismicCharge.MAX_RADIUS, s.reach());
        assertEquals(SeismicWave.listenTicks(SeismicCharge.MAX_RADIUS), s.readingTick());
    }

    @Test
    void theCoalIsNeverPartOfAnIronSurvey() {
        final Set<String> coal = new HashSet<>();
        for (int[] c : SeismicStage.RIG_COAL) {
            coal.add(c[0] + "," + c[1] + "," + c[2]);
        }
        for (SeismicPonderSurvey.Vein v : SeismicPonderBeats.Survey.ground().veins()) {
            for (int[] c : v.cells()) {
                assertFalse(coal.contains(c[0] + "," + c[1] + "," + c[2]), "coal counted as iron at " + c[0] + "," + c[1]);
            }
        }
    }

    @Test
    void theReadingIsTheRecordersOwnLine() {
        final SeismicPonderSurvey.Survey s = SeismicPonderBeats.Survey.ground();
        assertEquals(5, s.veins().size());

        assertEquals(Math.round((float) s.veins().get(0).nearest()), s.nearest());
        assertEquals(SeismicStage.RIG_Y - SeismicCharge.MAX_RADIUS, s.bottom());

        final SeismicPonderSurvey.Vein v = s.veins().get(0);
        assertEquals("Y " + (int) Math.floor(64 + v.centre()[1] - SeismicStage.RIG[1]), v.depth());
    }

    @Test
    void theWitnessesStandWhereTheGameWouldShowThemTheVeins() {
        final double[] w = SeismicStage.RIG_WITNESS;
        assertTrue(dist(middle(SeismicStage.RIG), new Vec3(w[0], w[1], w[2])) <= SeismicScannerBE.WITNESS_RADIUS);
        final Walker.Walk walk = SeismicPonderBeats.Reading.walk();
        final int impact = SeismicPonderBeats.Reading.impact();
        assertTrue(dist(middle(SeismicStage.STRIP_RIG), walk.at(impact)) <= SeismicScannerBE.WITNESS_RADIUS);
    }

    @Test
    void theCutAwaysWitnessIsNearEveryVeinSoTheyStayLit() {
        final double[] w = SeismicStage.RIG_WITNESS;
        for (SeismicPonderSurvey.Vein v : SeismicPonderBeats.Survey.ground().veins()) {
            final double d = SeismicPonderSurvey.distance(v, w[0], w[1] + Walker.EYE, w[2]);
            assertEquals(1.0, VeinVisibility.proximity(d), 1e-9, v.label() + " at " + d);
        }
    }

    @Test
    void theStripShowsEveryBandOfTheProximityLaw() {
        final SeismicPonderSurvey.Survey s = SeismicPonderBeats.Reading.ground();
        assertEquals(SeismicStage.STRIP_VEINS.length, s.veins().size(), "the whole strip is in the survey");
        final Walker.Walk walk = SeismicPonderBeats.Reading.walk();
        final Vec3 here = walk.eye(SeismicPonderBeats.Reading.WALK);
        final Vec3 there = walk.eye(SeismicPonderBeats.Reading.walkEnd());
        final List<SeismicPonderSurvey.Vein> v = s.veins();
        final SeismicPonderSurvey.Vein first = v.get(0);
        final SeismicPonderSurvey.Vein farthest = v.get(v.size() - 1);

        assertEquals(1.0, VeinVisibility.proximity(SeismicPonderSurvey.distance(first, here.x, here.y, here.z)), 1e-9);
        assertTrue(VeinVisibility.proximity(SeismicPonderSurvey.distance(farthest, here.x, here.y, here.z)) < 0.05,
                "the farthest vein is gone past 32 blocks");
        boolean between = false;
        for (SeismicPonderSurvey.Vein x : v) {
            final double p = VeinVisibility.proximity(SeismicPonderSurvey.distance(x, here.x, here.y, here.z));
            between |= p > 0.2 && p < 0.9;
        }
        assertTrue(between, "a vein fading between 16 and 32 blocks");

        assertEquals(1.0, VeinVisibility.proximity(SeismicPonderSurvey.distance(farthest, there.x, there.y, there.z)), 1e-9);
        assertTrue(VeinVisibility.proximity(SeismicPonderSurvey.distance(first, there.x, there.y, there.z)) < 0.9,
                "the vein by the rig has faded behind the walk");

        final double look = dist(middle(SeismicStage.STRIP_RIG), there);
        assertTrue(look > SeismicScannerBE.WITNESS_RADIUS && look < 64.0, "the look is from " + look + " blocks");
    }
}
