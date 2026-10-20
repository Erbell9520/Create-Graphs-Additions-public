package com.createstats.client.ponder.powertrack;

import com.createstats.powertrack.CollectorGeometry;
import com.createstats.powertrack.SectionView;
import com.createstats.powertrack.core.CollectorPose;
import com.createstats.powertrack.core.JunctionClearance;
import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.RailPower;
import com.createstats.powertrack.core.SectionGraph;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerTrackPonderTruthTest {
    private static final double EPS = 1e-6;

    @Test
    void theLineIsOneSectionWithoutTheInsulatorAndTwoWithIt() {
        final RailLayout open = PowerTrackStage.line(false).layout();
        assertEquals(1, open.sections().sectionCount());
        final List<SectionGraph.Piece> one = open.sections().pieces(PowerTrackStage.LINE_WEST, PowerTrackStage.LINE_EAST);
        assertEquals(1, one.size());
        assertEquals(SectionGraph.END_GAP, one.get(0).railFrom(), EPS, "the rail starts half a block in from the line's west node");
        assertEquals(PowerTrackStage.lineLength() - SectionGraph.END_GAP, one.get(0).railTo(), EPS);
        assertEquals(SectionGraph.RailEnd.RAMP_END, one.get(0).fromEnd());

        final RailLayout cut = PowerTrackStage.line(true).layout();
        assertEquals(2, cut.sections().sectionCount(), "the insulator splits the line in two");
        final List<SectionGraph.Piece> two = cut.sections().pieces(PowerTrackStage.LINE_WEST, PowerTrackStage.LINE_EAST);
        assertEquals(2, two.size());
        final double at = PowerTrackStage.lineAt(PowerTrackStage.INSULATOR[0]);
        assertEquals(at - SectionGraph.INSULATOR_GAP / 2, two.get(0).railTo(), EPS, "the rail stops half the gap before the cut");
        assertEquals(at + SectionGraph.INSULATOR_GAP / 2, two.get(1).railFrom(), EPS, "and starts half the gap after it");
        assertEquals(SectionGraph.RailEnd.RAMP_INSULATED, two.get(0).toEnd());
        assertEquals(SectionGraph.RailEnd.RAMP_INSULATED, two.get(1).fromEnd());

        assertEquals(PowerTrackStage.INSULATOR[0], PowerTrackStage.LINE_X[0] + two.get(0).railTo(), EPS);
        assertEquals(PowerTrackStage.INSULATOR[0] + 1, PowerTrackStage.LINE_X[0] + two.get(1).railFrom(), EPS);
        assertFalse(cut.sections().contact(PowerTrackStage.LINE_WEST, PowerTrackStage.LINE_EAST, at), "nothing conducts at the cut");

        final PowerTrackStage.Stage st = PowerTrackStage.line(true);
        final int a = cut.sections().sectionAt(PowerTrackStage.LINE_WEST, PowerTrackStage.LINE_EAST, st.taps().get(0).at());
        final int b = cut.sections().sectionAt(PowerTrackStage.LINE_WEST, PowerTrackStage.LINE_EAST, st.taps().get(1).at());
        assertTrue(a >= 0 && b >= 0 && a != b, "the feeders tap different sections");
        assertEquals(1, st.taps().get(0).side(), "feeder A stands north of the line, on the camera's side: its tap's lug too");
        assertEquals(1, st.taps().get(1).side());
    }

    @Test
    void theYardsGapsAreTheRulesGaps() {
        final PowerTrackStage.Stage st = PowerTrackStage.yard();
        final SectionGraph g = st.layout().sections();
        final RailLayout.Shape west = st.layout().shape(PowerTrackStage.YARD_WEST, PowerTrackStage.YARD_JUNCTION);
        final RailLayout.Shape east = st.layout().shape(PowerTrackStage.YARD_JUNCTION, PowerTrackStage.YARD_EAST);
        final RailLayout.Shape curve = st.layout().shape(PowerTrackStage.YARD_JUNCTION, PowerTrackStage.YARD_CURVE_END);
        final List<SectionGraph.Piece> w = g.pieces(west.lo(), west.hi());
        final List<SectionGraph.Piece> e = g.pieces(east.lo(), east.hi());
        final List<SectionGraph.Piece> c = g.pieces(curve.lo(), curve.hi());

        assertEquals(2, w.size());
        assertEquals(SectionGraph.RailEnd.RAMP_INSULATED, w.get(0).toEnd());
        assertEquals(SectionGraph.RailEnd.RAMP_JUMPERED, w.get(1).toEnd());
        assertEquals(west.length() - SectionGraph.JUNCTION_GAP_MIN, w.get(1).railTo(), EPS, "the trunk stops the floor's gap short");
        assertTrue(w.get(1).hasRail(), "the stretch between the insulator and the junction keeps its rail");

        final double clearing = JunctionClearance.clearing(walker(east), east.length(), walker(curve), curve.length());
        assertTrue(clearing > SectionGraph.JUNCTION_GAP_MIN, "a 6 by 6 turn takes longer to clear than the floor");
        assertEquals(clearing, e.get(0).railFrom(), EPS, "the east straight stops where the curve has cleared it");
        assertEquals(clearing, c.get(0).railFrom(), EPS, "and the curve the same");
        assertEquals(SectionGraph.RailEnd.RAMP_JUMPERED, e.get(0).fromEnd());
        assertEquals(SectionGraph.RailEnd.RAMP_JUMPERED, c.get(0).fromEnd());
        assertTrue(e.get(0).hasRail() && c.get(0).hasRail(), "both keep rail beyond the points");
        assertEquals(SectionGraph.RailEnd.THROUGH, c.get(0).toEnd(), "the curve runs on into the branch");
        final RailLayout.Shape branch = st.layout().shape(PowerTrackStage.YARD_CURVE_END, PowerTrackStage.YARD_BRANCH);
        assertEquals(SectionGraph.RailEnd.RAMP_END, g.pieces(branch.lo(), branch.hi()).get(0).toEnd(), "the branch ends in a plain ramp");

        assertEquals(g.sectionAt(east.lo(), east.hi(), 5.0), g.sectionAt(curve.lo(), curve.hi(), 5.0));
        assertEquals(g.sectionAt(west.lo(), west.hi(), 6.0), g.sectionAt(east.lo(), east.hi(), 5.0), "the junction does not end the section");

        assertEquals(SectionGraph.Gap.INSULATED, g.gapAt(west.lo(), west.hi(), PowerTrackStage.yardWestAt(PowerTrackStage.YARD_INSULATOR[0])));
        assertEquals(SectionGraph.Gap.JUNCTION, g.gapAt(west.lo(), west.hi(), west.length() - 0.5));
        assertEquals(SectionGraph.Gap.JUNCTION, g.gapAt(east.lo(), east.hi(), 1.0));
        assertEquals(SectionGraph.Gap.END, g.gapAt(branch.lo(), branch.hi(), branch.length() - 0.1));

        final RailLayout.Shape low = st.layout().shape(PowerTrackStage.YARD_SLOPE_START, PowerTrackStage.YARD_SLOPE_FOOT);
        final RailLayout.Shape rise = st.layout().shape(PowerTrackStage.YARD_SLOPE_FOOT, PowerTrackStage.YARD_SLOPE_CREST);
        final RailLayout.Shape high = st.layout().shape(PowerTrackStage.YARD_SLOPE_CREST, PowerTrackStage.YARD_SLOPE_END);
        assertEquals(low.length() - RailLayout.GRADE_GAP, g.pieces(low.lo(), low.hi()).get(0).railTo(), EPS);
        assertEquals(RailLayout.GRADE_GAP, g.pieces(rise.lo(), rise.hi()).get(0).railFrom(), EPS);
        assertEquals(rise.length() - RailLayout.GRADE_GAP, g.pieces(rise.lo(), rise.hi()).get(0).railTo(), EPS);
        assertEquals(RailLayout.GRADE_GAP, g.pieces(high.lo(), high.hi()).get(0).railFrom(), EPS);
        assertTrue(g.pieces(rise.lo(), rise.hi()).get(0).hasRail(), "four blocks of slope keep a rail between their ramps");
        assertTrue(rise.length() - 2 * RailLayout.GRADE_GAP >= SectionGraph.MIN_STUB, "longer than the shortest stub laid");
        assertEquals(SectionGraph.Gap.GRADE, g.gapAt(low.lo(), low.hi(), low.length() - 0.5));
        assertEquals(SectionGraph.Gap.GRADE, g.gapAt(rise.lo(), rise.hi(), 0.5));
        assertEquals(3, g.sectionCount(), "the slope line is its own section; the main line's two make three");
    }

    private static JunctionClearance.Walk walker(RailLayout.Shape s) {
        return (d, xz) -> {
            final double[] p = s.point(d);
            xz[0] = p[0];
            xz[1] = p[2];
        };
    }

    @Test
    void theCurveIsCreatesCurve() {
        final PowerTrackStage.Curve curve = PowerTrackStage.yardCurve();
        final double radius = 5.5;
        assertEquals(radius * 4.0 / 3.0 * Math.tan(Math.PI / 8.0), curve.handle(), 1e-9, "a quarter turn's handle");
        assertTrue(curve.length() > 8.5 && curve.length() < 8.8, "about a quarter of a circle of radius 5.5: " + curve.length());
        final double[] start = curve.at(0.0);
        final double[] end = curve.at(1.0);
        assertEquals(PowerTrackStage.yardNode(PowerTrackStage.YARD_JUNCTION)[0], start[0], 1e-9);
        assertEquals(PowerTrackStage.yardNode(PowerTrackStage.YARD_CURVE_END)[2], end[2], 1e-9);
        assertEquals(Math.max(3, (int) Math.ceil(curve.length() * 4.0) + 1) * 3, curve.samples().length, "four samples a block");

        final double[] a = curve.at(0.01);
        final double[] b = curve.at(0.99);
        assertTrue(a[0] > start[0] && Math.abs(a[2] - start[2]) < 1e-3, "leaves the junction eastward");
        assertTrue(end[2] < b[2] && Math.abs(b[0] - end[0]) < 1e-3, "arrives at the branch heading north");
    }

    @Test
    void whatIsDrawnTouchingIsWhatConducts() {
        final RailLayout layout = PowerTrackPonderBeats.Collector.stage().layout();
        int touched = 0;
        int off = 0;
        for (RailStory.Frame f : PowerTrackPonderBeats.Collector.frames()) {
            for (int s = 0; s < f.shoes().length; s++) {
                final RailStory.Shoe shoe = f.shoes()[s];
                final Vec3 centre = new Vec3(PowerTrackStage.LINE_CAR_X + PowerTrackStage.CAR_COLLECTORS[s] + 0.5 + f.carX(),
                        PowerTrackStage.TRACK_Y + 1.5, PowerTrackStage.LINE_Z + 0.5);
                final Vec3 trail = new Vec3(PowerTrackStage.trailSign(PowerTrackStage.CAR_COLLECTORS[s]), 0, 0);
                final CollectorPose.Rest rest = CollectorGeometry.rest(centre, trail, PowerTrackStage.TRACK_Y + 1, p -> {
                    final RailLayout.Hit hit = layout.nearest(p.x, p.y, p.z, RailStory.REACH, 1.0, 2.0);
                    return hit != null && hit.contact() ? hit.railTopY() : Double.NaN;
                });
                assertEquals(rest.touching(), shoe.touching(), "tick " + f.tick() + " shoe " + s);
                if (shoe.touching()) {
                    touched++;
                    assertEquals(rest.angleDeg(), shoe.angle(), 1e-6, "on metal the drawn arm is the rest, tick " + f.tick());
                    assertTrue(shoe.section() >= 0, "a touching shoe is on a section");
                } else {
                    off++;
                    assertEquals(-1, shoe.section(), "a shoe off the rail is on no section");
                    assertTrue(shoe.angle() >= CollectorPose.STOP_DEG - 1e-9, "never under the stop");
                }
            }
        }
        assertTrue(touched > 200 && off > 20, "the scene has both: " + touched + " touching, " + off + " off");
    }

    @Test
    void theCollectorSceneChargesCrossesAndGives() {
        final List<RailStory.Frame> frames = PowerTrackPonderBeats.Collector.frames();
        final int base = PowerTrackPonderBeats.base();
        final int carRate = RailPower.carRate(base, RailPower.MAX_COLLECTORS_PER_CAR);

        for (int t = PowerTrackPonderBeats.Collector.CHARGE; t < PowerTrackPonderBeats.Collector.GLIDE_2; t++) {
            assertEquals(carRate, frames.get(t).carFlow(), "charging at tick " + t);
            assertEquals(1, frames.get(t).feeders()[0].lamp(), "A's lantern green while it supplies with charge");

            assertEquals(carRate, frames.get(t).feeders()[0].railFlow(), "A's needle at the car's rate");
        }

        boolean inGap = false;
        int arcsAtGap = 0;
        int arcsRollingOn = 0;
        int firstLanding = -1;
        for (RailStory.Frame f : frames) {
            for (RailStory.Shoe s : f.shoes()) {
                if (s.touching() && firstLanding < 0) {
                    firstLanding = f.tick();
                    assertFalse(s.arc(), "the first shoe to land on A meets no load: no arc");
                }
                if (!s.touching() && f.tick() > PowerTrackPonderBeats.Collector.GLIDE_2 && f.tick() < PowerTrackPonderBeats.Collector.GIVE) {
                    inGap = true;
                }
                if (s.arc()) {
                    assertTrue(s.touching(), "an arc strikes as the shoe lands");
                    if (f.tick() > PowerTrackPonderBeats.Collector.GLIDE_2) {
                        arcsAtGap++;
                    } else {
                        arcsRollingOn++;
                    }
                }
            }
        }
        assertTrue(firstLanding > PowerTrackPonderBeats.Collector.GLIDE_1, "the shoes meet the rail on the first glide");
        assertTrue(inGap, "a shoe hangs in the gap");
        assertEquals(2, arcsAtGap, "each shoe arcs once as it lands on the collecting section under load");
        assertTrue(arcsRollingOn <= 1, "at most the second shoe's landing on A arcs");

        final RailStory.Frame last = frames.get(frames.size() - 1);
        final int floor = (int) Math.ceil(PowerTrackPonderBeats.BANK_FE * (PowerTrackPonderBeats.Collector.FLOOR_B / 100.0));
        assertEquals(floor, last.carStored(), "the car keeps B's floor");
        assertEquals(0, last.carFlow());
        assertEquals(-1, last.feeders()[1].lamp(), "B still collects with room: amber");
        assertEquals(0, last.feeders()[1].railFlow(), "B's needle rests");
        boolean gave = false;
        for (RailStory.Frame f : frames) {
            if (f.carFlow() < 0) {
                gave = true;

                assertEquals(f.carFlow(), f.feeders()[1].railFlow(), "what the car gives is what B takes");
                assertEquals(SectionView.COLLECTING, f.sections()[f.carSection()].state());
            }
        }
        assertTrue(gave, "the car gives on B");
        conserved(frames, 2);
    }

    @Test
    void theFeederSceneSuppliesCollectsAndCarries() {
        final List<RailStory.Frame> frames = PowerTrackPonderBeats.Feeder.frames();
        final int held = PowerTrackPonderBeats.Feeder.floorHeld();
        final RailStory.FeederFrame atHold = frames.get(held).feeders()[0];
        assertEquals(PowerTrackPonderBeats.Feeder.FLOOR_A_SUPPLY, 100 * atHold.stationStored() / PowerTrackPonderBeats.BANK_FE,
                1.0, "A holds at its floor");
        assertTrue(held < PowerTrackPonderBeats.Feeder.GLIDE_1, "it holds before the car leaves for B");
        assertEquals(0, frames.get(held + 3).feeders()[0].lamp(), "the lantern goes dark once the buffer is spent");
        assertEquals(0, frames.get(held + 3).feeders()[0].railFlow(), "and the needle rests");
        for (int t = held + 3; t < PowerTrackPonderBeats.Feeder.GLIDE_1; t++) {
            assertEquals(0, frames.get(t).carFlow(), "nothing moves while A holds, tick " + t);
        }

        final int floorB = (int) Math.ceil(PowerTrackPonderBeats.BANK_FE * (PowerTrackPonderBeats.Feeder.FLOOR_B / 100.0));
        assertEquals(floorB, frames.get(PowerTrackPonderBeats.Feeder.AUTO_FLOOR_CUE).carStored(), "the car at B's floor before Auto");

        final RailStory.Frame end = frames.get(frames.size() - 1);
        final double aPct = 100.0 * end.feeders()[0].stationStored() / PowerTrackPonderBeats.BANK_FE;
        final double bPct = 100.0 * end.feeders()[1].stationStored() / PowerTrackPonderBeats.BANK_FE;
        assertTrue(aPct > PowerTrackPonderBeats.Feeder.FLOOR_AUTO + 5, "A ends above its line: " + aPct);
        assertTrue(bPct < PowerTrackPonderBeats.Feeder.FLOOR_AUTO + 5, "B ends below its line plus the hysteresis: " + bPct);
        assertEquals(1, end.feeders()[0].lamp(), "A green at the end: it still has it");
        assertEquals(-1, end.feeders()[1].lamp(), "B amber at the end: still short");
        assertTrue(end.feeders()[0].autoSupplying(), "A never flipped to collecting");
        assertFalse(end.feeders()[1].autoSupplying(), "B never flipped to supplying");
        assertEquals(floorB, end.carStored(), "the car ends at the floor again");
        int carried = 0;
        for (int t = PowerTrackPonderBeats.Feeder.AUTO_B; t < frames.size(); t++) {
            if (frames.get(t).carFlow() > 0) {
                assertEquals(0, frames.get(t).carSection(), "it charges on A");
                carried += frames.get(t).carFlow();
            }
        }
        assertTrue(carried > 0, "Auto carried charge from A to B");
        conserved(frames, 2);
    }

    @Test
    void theLayingSceneLightsWhenWired() {
        final List<RailStory.Frame> frames = PowerTrackPonderBeats.Laying.story(PowerTrackPonderBeats.Laying.stage().layout())
                .play(PowerTrackPonderBeats.Laying.LENGTH);
        for (int t = 0; t < PowerTrackPonderBeats.Laying.WIRED; t++) {
            assertEquals(0, frames.get(t).feeders()[0].lamp(), "dark before it is wired, tick " + t);
        }
        assertEquals(1, frames.get(PowerTrackPonderBeats.Laying.WIRED).feeders()[0].lamp(), "green the tick its circuit has charge");
        assertEquals(1, frames.get(PowerTrackPonderBeats.Laying.LENGTH - 1).feeders()[0].lamp());
        assertEquals(0, frames.get(PowerTrackPonderBeats.Laying.LENGTH - 1).feeders()[0].railFlow(), "no train: the needle rests at zero");
        assertEquals(SectionView.LIVE, frames.get(PowerTrackPonderBeats.Laying.LENGTH - 1).feeders()[0].view().state());
        assertEquals(PowerTrackPonderBeats.defaultDialFe(), frames.get(PowerTrackPonderBeats.Laying.LENGTH - 1).feeders()[0].dialFe());
    }

    @Test
    void theYardsCarChargesNothingInItsGap() {
        final List<RailStory.Frame> frames = PowerTrackPonderBeats.Gaps.frames();
        final SectionGraph g = PowerTrackPonderBeats.Gaps.stage().layout().sections();
        final RailStory.Frame end = frames.get(frames.size() - 1);
        for (RailStory.Shoe s : end.shoes()) {
            assertFalse(s.touching(), "a shoe in the junction's gap is on its stop");
            assertEquals(CollectorPose.STOP_DEG, s.angle(), 1e-6);
        }
        for (RailStory.Frame f : frames) {
            assertEquals(0, f.carFlow(), "nothing charges in a gap");
        }
        final double cut = PowerTrackStage.yardWestAt(PowerTrackStage.YARD_INSULATOR[0]);
        final SectionView west = RailStory.lampView(end.sections(), g, PowerTrackStage.YARD_WEST, PowerTrackStage.YARD_JUNCTION, cut, true);
        final SectionView east = RailStory.lampView(end.sections(), g, PowerTrackStage.YARD_WEST, PowerTrackStage.YARD_JUNCTION, cut, false);
        assertEquals(SectionView.LIVE, west.state(), "lamp 0 green: A supplies its side");
        assertEquals(SectionView.COLLECTING, east.state(), "lamp 1 amber: B collects on the other");
        final SectionView before = RailStory.lampView(frames.get(PowerTrackPonderBeats.Gaps.STATION_B - 1).sections(), g,
                PowerTrackStage.YARD_WEST, PowerTrackStage.YARD_JUNCTION, cut, false);
        assertEquals(SectionView.DEAD, before.state(), "lamp 1 dark before feeder B stands");
    }

    private static void conserved(List<RailStory.Frame> frames, int feeders) {
        long first = -1;
        for (RailStory.Frame f : frames) {
            long sum = f.carStored();
            for (int i = 0; i < feeders; i++) {
                sum += f.feeders()[i].buffer() + f.feeders()[i].stationStored();
            }
            if (first < 0) {
                first = sum;
            }
            assertEquals(first, sum, "FE created or lost by tick " + f.tick());
        }
    }
}
