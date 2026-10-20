package com.createstats.aerial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.createstats.aerial.AerialAlerts.Alert;
import com.createstats.aerial.AerialAlerts.Code;
import com.createstats.aerial.AerialAlerts.Level;
import com.createstats.aerial.AerialFixtures.Scene;
import com.createstats.aerial.AerialSnapshot.Event;
import com.createstats.aerial.AerialSnapshot.Helm;
import com.createstats.aerial.AerialSnapshot.Voyage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialMindTest {
    @Test
    void everySectionRoundTripsFromBothFixtures() throws IOException {
        for (final AerialSnapshot s : new AerialSnapshot[]{AerialFixtures.full(Scene.CRUISE),
                AerialFixtures.full(Scene.MOORED), AerialRealistic.scene(Scene.CRUISE), AerialRealistic.scene(Scene.MOORED)}) {
            final byte[] bytes = AerialCodec.encode(s);
            final AerialSnapshot back = AerialCodec.decode(bytes, null);
            assertArrayEquals(bytes, AerialCodec.encode(back), "every section survives whole");
            assertEquals(s.helm(), back.helm() == null ? null : withFloats(s.helm(), back.helm()));
            assertEquals(s.parts().parts().size(), back.parts().parts().size());
            assertEquals(s.voyage().events().size(), back.voyage().events().size());
            assertEquals(s.mass().load(), back.mass().load(), 1.0e-3);
            assertEquals(s.balloons().get(0).lit(), back.balloons().get(0).lit());
            assertEquals(s.silhouette().hasEnvelope(), back.silhouette().hasEnvelope());
            assertArrayEquals(s.silhouette().envSide(), back.silhouette().envSide());
        }
    }

    private static Helm withFloats(Helm expected, Helm actual) {
        assertEquals(expected.mode(), actual.mode());
        assertEquals(expected.intent(), actual.intent());
        assertEquals(expected.notes(), actual.notes());
        assertEquals(expected.rings(), actual.rings());
        assertEquals((float) expected.order(), (float) actual.order());
        assertEquals((float) expected.heldAltitude(), (float) actual.heldAltitude());
        assertEquals(expected.guard().verdict(), actual.guard().verdict());
        return expected;
    }

    @Test
    void aShipSavedBeforeVersionTwoKeepsItsRecorder() throws IOException {
        final AerialRecorder rec = AerialFixtures.flown();
        final AerialSnapshot carrier = new AerialSnapshot(10L, "", null, 0, null, null, null, null, null, null, null,
                null, null, null, null, null, null, rec.trail(), rec.history(), rec.envelope(), null);
        final byte[] v1 = AerialCodec.encode(carrier, AerialCodec.V1_READABLE);
        v1[0] = (byte) AerialCodec.V1;
        final AerialRecorder back = AerialRecorder.load(v1);
        assertEquals(rec.trail().fixes().size(), back.trail().fixes().size(), "the trail survives the upgrade");
        assertEquals(rec.envelope().topSpeed(), back.envelope().topSpeed(), 1.0e-4, "and what it learned");
        assertFalse(back.voyage().aloft(), "a blob with no voyage starts its story at the next take-off");

        final byte[] mass = AerialCodec.encode(AerialFixtures.cruise(), AerialCodec.Section.MASS.bit());
        mass[0] = (byte) AerialCodec.V1;
        assertThrows(IOException.class, () -> AerialCodec.decode(mass, null));
    }

    @Test
    void theRecorderSavesItsVoyage() {
        final AerialSnapshot s = AerialRealistic.scene(Scene.CRUISE);
        final AerialRecorder back = AerialRecorder.load(saved(s));
        final Voyage v = back.voyage();
        assertEquals(s.voyage().takeoff(), v.takeoff(), "saved in the air, still in the air");
        assertEquals(s.voyage().events().size(), v.events().size());
        assertEquals(s.voyage().events().get(0).text(), v.events().get(0).text());
        assertEquals(s.voyage().trace().length, v.trace().length);
        assertEquals(s.voyage().tracePeriod(), v.tracePeriod());
        assertEquals(s.voyage().distance(), v.distance(), 0.01);
    }

    private static byte[] saved(AerialSnapshot s) {
        final AerialSnapshot carrier = new AerialSnapshot(s.gameTime(), "", null, 0, null, null, null, null, null, null,
                null, null, null, null, null, null, null, s.trail(), s.history(), s.envelope(), null, null, null, s.voyage());
        return AerialCodec.encode(carrier, AerialCodec.Section.HEADER.bit() | AerialCodec.Section.TRAIL.bit()
                | AerialCodec.Section.HISTORY.bit() | AerialCodec.Section.ENVELOPE.bit() | AerialCodec.Section.VOYAGE.bit());
    }

    @Test
    void theHelmIsFastAndTheStoryIsSlow() {
        assertTrue(AerialCodec.Section.HELM.fast(), "the telegraph and the height held change by the second");
        assertFalse(AerialCodec.Section.PARTS.fast());
        assertFalse(AerialCodec.Section.VOYAGE.fast());
        assertFalse(AerialCodec.Section.TRAFFIC.fast(), "read once a second, so sent when it changes");
        int fastMask = 0;
        for (final AerialCodec.Section sec : AerialCodec.Section.values()) {
            if (sec.fast()) {
                fastMask |= sec.bit();
            }
        }
        final byte[] fast = AerialCodec.encode(AerialRealistic.scene(Scene.CRUISE), fastMask);
        assertTrue(fast.length < 2_500, "the owner's nine-propeller ship: fast packet " + fast.length + " bytes");
    }

    @Test
    void theRecorderTellsTheFlightFromTheSnapshotsAlone() {
        final Voyage v = AerialRealistic.scene(Scene.CRUISE).voyage();
        final List<String> said = new ArrayList<>();
        for (final Event e : v.events()) {
            said.add(e.text());
        }
        assertTrue(v.aloft());
        assertTrue(said.contains("Weighed off: 223 kpg aboard"), said.toString());
        assertTrue(said.contains("Took off"), said.toString());
        assertTrue(said.contains("SLOW AHEAD"), said.toString());
        assertTrue(said.contains("HALF AHEAD"), said.toString());
        assertTrue(said.contains("Alex left the helm: going to the target"), said.toString());
        assertTrue(said.contains("Alex took the helm"), said.toString());
        assertTrue(said.stream().anyMatch(t -> t.startsWith("Terrain guard: climbing to 104")), said.toString());

        assertTrue(said.indexOf("Weighed off: 223 kpg aboard") < said.indexOf("Took off"));
        for (int i = 1; i < v.events().size(); i++) {
            assertTrue(v.events().get(i).tick() >= v.events().get(i - 1).tick());
        }
        assertTrue(v.highest() > 100.0, "the guard's climb over the hill is the flight's highest point");
        assertTrue(v.distance() > 300.0, "metres over the ground: " + v.distance());
    }

    @Test
    void aLandingClosesTheFlightAndKeepsItsTotals() {
        final Voyage v = AerialRealistic.scene(Scene.MOORED).voyage();
        assertFalse(v.aloft(), "on the pad");
        assertNotNull(v.last());
        assertEquals(Event.LANDED, v.events().get(v.events().size() - 1).kind());
        assertEquals(4, v.last().orders(), "slow, half, slow again, and stop for the landing");
        assertTrue(v.last().seconds() > 200.0);
        assertTrue(v.trace().length > 0, "the last flight's trace stays for the hangar face");
    }

    @Test
    void theTraceKeepsTheWholeFlightByHalvingItsDetail() {
        final AerialRecorder rec = new AerialRecorder();
        final AerialSnapshot s = AerialRealistic.scene(Scene.CRUISE);
        final int samples = AerialSnapshot.Voyage.MAX_TRACE * 3;
        for (int i = 0; i < samples * AerialRecorder.TRACE_TICKS / AerialRecorder.SAMPLE_TICKS; i++) {
            final long t = 5000L + i * AerialRecorder.SAMPLE_TICKS;
            final V3 at = s.body().position().plus(new V3(i * 0.1, Math.sin(i * 0.01) * 5.0, 0.0));
            rec.observe(new AerialSnapshot(t, s.name(), s.shipId(), 0, new AerialSnapshot.Body(at, s.body().orientation(),
                    s.body().nose(), new V3(2.0, 0.0, 0.0), V3.ZERO), s.air(), s.mass(), s.forces(), s.propellers(),
                    s.balloons(), s.engine(), s.target(), s.autopilot(), null, List.of(), s.terrain(), null, null, null, null,
                    s.silhouette(), s.helm(), s.parts(), null));
        }
        final Voyage v = rec.voyage();
        assertTrue(v.trace().length <= AerialSnapshot.Voyage.MAX_TRACE);
        assertTrue(v.trace().length > AerialSnapshot.Voyage.MAX_TRACE / 2, "never less than half full once it has filled");
        assertEquals(AerialRecorder.TRACE_TICKS * 4, v.tracePeriod(), "three times full: halved twice");
    }

    @Test
    void theOrdersAreSaidAsTheConsoleSaysThem() {
        assertEquals("HALF AHEAD", HelmWords.order(0.5));
        assertEquals("SLOW ASTERN", HelmWords.order(-0.25));
        assertEquals("STOP", HelmWords.order(0.0));
        assertEquals("FULL AHEAD", HelmWords.order(1.0f), "exact after a float's trip over the wire");
        assertEquals("AHEAD 62%", HelmWords.order(0.62), "between notches, the lever being swept");
        assertEquals(-1, HelmWords.notch(0.62));
        assertEquals(5, HelmWords.notch(0.5));
    }

    @Test
    void theCeilingSaysWhatDecidesIt() {
        final Helm h = AerialRealistic.scene(Scene.CRUISE).helm();
        assertEquals("the balloons' lift", HelmWords.ceilingReason(h));
        final Helm capped = new Helm("HELM", "ANCHOR", "", 0L, "", 0, 0, 0, false, 120, 120, 0, Double.NaN, 5, 150, 190,
                150, 320, 0.5, false, false, 0, 0, V3.NAN, null, "", false, List.of(), 0, 0, 0);
        assertEquals("your altitude cap", HelmWords.ceilingReason(capped));
        final Helm top = new Helm("HELM", "ANCHOR", "", 0L, "", 0, 0, 0, false, 120, 120, 0, Double.NaN, 5, 272, 400,
                Double.NaN, 320, 0.5, false, false, 0, 0, V3.NAN, null, "", false, List.of(), 0, 0, 0);
        assertEquals("the top of the world", HelmWords.ceilingReason(top));
    }

    @Test
    void aWeighedLoadIsCarriedLikeTheShipsOwnMass() {
        final AirframeReadout r = AirframeMath.read(AerialRealistic.scene(Scene.CRUISE));
        assertEquals(AerialRealistic.MASS, r.mass, 1.0e-9, "the ship's own mass is Sable's");
        assertEquals(AerialRealistic.LOAD, r.load, 1.0e-9);
        assertEquals(AerialRealistic.MASS + AerialRealistic.LOAD, r.carried, 1.0e-9);
        assertEquals(1.0, r.support, 0.03, "held level: carried, not a quarter to spare");

        final AerialSnapshot s = AerialRealistic.scene(Scene.CRUISE);
        final AerialSnapshot unweighed = new AerialSnapshot(s.gameTime(), s.name(), s.shipId(), s.commands(), s.body(),
                s.air(), new AerialSnapshot.Mass(s.mass().mass(), s.mass().inertia(), s.mass().blocks()), s.forces(),
                s.propellers(), s.balloons(), s.engine(), s.target(), s.autopilot(), s.docking(), s.traffic(), s.terrain(),
                s.route(), s.trail(), s.history(), s.envelope(), s.silhouette());
        assertTrue(AirframeMath.read(unweighed).support > 1.25);
    }

    @Test
    void noDragNoTrend() {
        final AirframeReadout r = AirframeMath.read(AerialRealistic.scene(Scene.CRUISE));
        assertTrue(Double.isNaN(r.forwardAccel), "all push and no resistance is not where the speed is going");
        assertTrue(Double.isNaN(r.dragShare), "and no drag is unknown drag, not zero drag");
        assertTrue(Double.isNaN(r.topSpeed));
        assertTrue(Double.isFinite(AirframeMath.read(AerialFixtures.cruise()).forwardAccel), "with drag it is there");
    }

    @Test
    void shipsParkedSideBySideAreNotInConflict() {
        final AerialSnapshot s = AerialRealistic.scene(Scene.MOORED);
        final V3 beside = s.body().position().plus(new V3(20.0, 0.0, 0.0));
        final AerialSnapshot parked = new AerialSnapshot(s.gameTime(), s.name(), s.shipId(), s.commands(), s.body(),
                s.air(), s.mass(), s.forces(), s.propellers(), s.balloons(), s.engine(), s.target(), s.autopilot(),
                null, List.of(new AerialSnapshot.Traffic("Neighbour", new UUID(9L, 9L), beside, V3.ZERO, 14.0)),
                s.terrain(), s.route(), s.trail(), s.history(), s.envelope(), s.silhouette());
        assertFalse(AirframeMath.read(parked).traffic.get(0).conflict(), "neither is going anywhere");
        final AerialSnapshot closing = new AerialSnapshot(s.gameTime(), s.name(), s.shipId(), s.commands(), s.body(),
                s.air(), s.mass(), s.forces(), s.propellers(), s.balloons(), s.engine(), s.target(), s.autopilot(),
                null, List.of(new AerialSnapshot.Traffic("Neighbour", new UUID(9L, 9L), beside, new V3(-2.0, 0.0, 0.0),
                14.0)), s.terrain(), s.route(), s.trail(), s.history(), s.envelope(), s.silhouette());
        assertTrue(AirframeMath.read(closing).traffic.get(0).conflict(), "one coming at the other is");
    }

    @Test
    void theAerialsOwnWarningLeadsTheCautions() {
        final AirframeReadout r = AirframeMath.read(AerialRealistic.scene(Scene.MOORED));
        final List<Alert> alerts = AerialAlerts.evaluate(r, Set.of(), 0.0);
        assertEquals(Code.HELM, alerts.get(0).code(), "the banked burners, the line the goggles show");
        assertEquals(Level.CAUTION, alerts.get(0).level());
        assertTrue(alerts.get(0).text().startsWith("MOORED WITH THE BURNERS BANKED  "), alerts.get(0).text());
        assertEquals("CEILING Y 190  higher", AerialAlerts.noteText("Ceiling y 190: higher"));
        assertEquals("HELD DOWN  The load is heavy", AerialAlerts.noteText("HELD DOWN: THE LOAD IS HEAVY"),
                "the banner's capitals read as a headline and a sentence");
    }

    @Test
    void theGuardAtWorkIsInformationForAShipOnItsOwnAndAWarningAtTheHelm() {
        final Helm unattended = helm("GO_TO", "CLIMB", "TERRAIN AHEAD: RIDGE AT 90 IN 110 b. CLIMBING TO CLEAR IT BY 4.", true,
                List.of("Terrain ahead: climbing to y 104 to clear the ground at y 90, 110 blocks on."));
        final List<Alert> a = AerialAlerts.evaluate(withHelm(unattended), Set.of(), 0.0);
        assertEquals(Level.ADVISORY, level(a, Code.GUARD));
        assertNull(level(a, Code.HELM), "the guard doing its job is not something wrong");
        final Helm atHelm = helm("HELM", "CLIMB", "TERRAIN AHEAD: RIDGE AT 90 IN 110 b, ABOVE THE HEIGHT HELD. CLIMB OR TURN.",
                true, List.of("Terrain ahead: the ground at y 90, 110 blocks on, is above the height held. Climb or turn."));
        final List<Alert> b = AerialAlerts.evaluate(withHelm(atHelm), Set.of(), 0.0);
        assertEquals(Level.CAUTION, level(b, Code.HELM), "the pilot is warned and never overruled");
        assertNull(level(b, Code.GUARD));
    }

    private static Helm helm(String mode, String verdict, String intent, boolean alert, List<String> notes) {
        return new Helm(mode, "GO_TO", "HELM".equals(mode) ? "Alex" : "", 0L, "STANDARD", 0.5, 0.5, 2, false, 96, 104,
                16, Double.NaN, 5.1, 190, 190, Double.NaN, 320, 0.6, false, false, 0, 0, V3.NAN,
                new AerialSnapshot.Guard(verdict, 104, Double.NaN, 90, 110, 90, 110, Double.POSITIVE_INFINITY), intent,
                alert, notes, 2285, 1100, 12000);
    }

    private static AirframeReadout withHelm(Helm h) {
        final AerialSnapshot s = AerialRealistic.scene(Scene.CRUISE);
        return AirframeMath.read(s.withMind(h, s.parts()));
    }

    private static Level level(List<Alert> alerts, Code code) {
        for (final Alert a : alerts) {
            if (a.code() == code) {
                return a.level();
            }
        }
        return null;
    }

    @Test
    void theHelmReachesTheChannelsBesideTheReadings() {
        final AirframeReadout r = AirframeMath.read(AerialRealistic.scene(Scene.CRUISE));
        final Map<String, Float> c = new HashMap<>();
        AerialChannels.publish(r, AerialAlerts.evaluate(r, Set.of(), 0.0), (id, label, value, type) -> c.put(id, value));
        assertEquals(1.0f, c.get("aero_mode"), "1 is the helm");
        assertEquals(25.0f, c.get("aero_order"), 1.0e-3f, "slow ahead");
        assertEquals(96.0f, c.get("aero_held_altitude"), 1.0e-3f);
        assertEquals(223.0f, c.get("aero_load"), 1.0e-3f);
        assertNotNull(c.get("aero_gas"));
        assertNotNull(c.get("aero_altitude"), "the old ids are all still there");
    }

    @Test
    void aBoardLeadsWithWhatTheGogglesSayThenTheOrderAndTheHeight() {
        final AirframeReadout r = AirframeMath.read(AerialRealistic.scene(Scene.CRUISE));
        final List<String> lines = AerialText.lines(r, AerialAlerts.evaluate(r, Set.of(), 0.0), 4);
        assertEquals("HOLDING 96, HEADING 016", lines.get(1));
        assertEquals("SLOW AHEAD  HOLD 96  HDG 016", lines.get(2));
    }

    @Test
    void aClippedTextIsTheSameAfterEveryCrossing() throws IOException {
        final String text = "A".repeat(63) + " and the rest past the cap";
        final Event once = new Event(100L, Event.ORDER, 0, text, 96.0f);
        assertEquals("A".repeat(63), once.text(), "cut at the cap, and the space the cut ended on stripped");
        assertEquals(once.text(), AerialSnapshot.clip(once.text(), 64), "clipping a clipped string changes nothing");
        final AerialSnapshot s = AerialFixtures.full(Scene.CRUISE);
        final Voyage v = s.voyage();
        final List<Event> events = new ArrayList<>(v.events());
        events.add(once);
        final AerialSnapshot with = s.withRecorded(s.trail(), s.history(), s.envelope(), new Voyage(v.takeoff(), v.seconds(),
                v.distance(), v.highest(), v.tracePeriod(), v.traceLast(), v.trace(), events, v.last()));
        final byte[] first = AerialCodec.encode(with);
        final byte[] second = AerialCodec.encode(AerialCodec.decode(first, null));
        assertArrayEquals(first, second, "a snapshot encodes the same after crossing the wire once");
    }
}
