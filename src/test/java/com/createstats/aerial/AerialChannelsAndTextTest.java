package com.createstats.aerial;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.createstats.data.MetricType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialChannelsAndTextTest {
    private record Sample(String label, float value, MetricType type) {
    }

    private static Map<String, Sample> publish(AerialSnapshot s) {
        final AirframeReadout r = AirframeMath.read(s);
        final Map<String, Sample> out = new LinkedHashMap<>();
        AerialChannels.publish(r, AerialAlerts.evaluate(r, Set.of(), 0.0),
                (id, label, value, type) -> out.put(id, new Sample(label, value, type)));
        return out;
    }

    @Test
    void theHeadlineReadingsBecomeChannels() {
        final Map<String, Sample> c = publish(AerialFixtures.cruise());
        assertEquals(142.0f, c.get("aero_altitude").value(), 1.0e-3f);
        assertEquals(107.0f, c.get("aero_support").value(), 1.0e-3f);
        assertEquals(MetricType.FILL_LEVEL, c.get("aero_support").type());
        assertEquals(7.0f, c.get("aero_lift_reserve").value(), 1.0e-3f);
        assertEquals(72.0f, c.get("aero_heading").value(), 1.0e-3f);
        assertEquals(MetricType.ANGLE_DEGREES, c.get("aero_heading").type());
        assertTrue(c.containsKey("aero_wind"));
        assertTrue(c.containsKey("aero_target_distance"));
        assertTrue(c.containsKey("aero_cross_track"));
        for (final String id : c.keySet()) {
            assertTrue(id.startsWith("aero_"), id);
            assertTrue(Float.isFinite(c.get(id).value()), id + " is published as a number");
        }
    }

    @Test
    void anUnknownReadingIsNoChannelRatherThanAZero() {
        final AerialSnapshot bare = new AerialSnapshot(1L, "Bare", null, 0, null,
                new AerialSnapshot.Air(false, V3.ZERO, Double.NaN, Double.NaN, 63.0, new V3(0.0, -11.0, 0.0)),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        final Map<String, Sample> c = publish(bare);
        assertFalse(c.containsKey("aero_agl"), "no terrain, no height above it");
        assertFalse(c.containsKey("aero_support"), "no forces, no support");
        assertFalse(c.containsKey("aero_wind"), "no air data, no wind");
        assertFalse(c.containsKey("aero_ceiling"));
        assertTrue(c.containsKey("aero_altitude"));
        assertEquals(0.0f, c.get("aero_alerts").value(), 0.0f, "an advisory is not an alarm");
    }

    @Test
    void theAlertCountIsWhatARedstoneAlarmWatches() {
        final Map<String, Sample> c = publish(AerialFixtures.sinking());
        assertTrue(c.get("aero_alerts").value() >= 3.0f);
        assertEquals(MetricType.ITEM_COUNT, c.get("aero_alerts").type());
    }

    @Test
    void boardLinesLeadWithTheShipThenTheWarning() {
        final AirframeReadout r = AirframeMath.read(AerialFixtures.sinking());
        final List<String> lines = AerialText.lines(r, AerialAlerts.evaluate(r, Set.of(), 0.0), 8);
        assertTrue(lines.get(0).startsWith("Kestrel  ALT 104"), lines.get(0));
        assertTrue(lines.get(1).startsWith("SINK RATE") || lines.get(1).startsWith("TERRAIN"), lines.get(1));
        assertTrue(lines.get(2).startsWith("IAS "), lines.get(2));
        for (final String line : lines) {
            for (final char ch : line.toCharArray()) {
                assertTrue(ch >= 32 && ch < 127, "a display board prints ASCII: " + line);
            }
        }
        assertEquals(3, AerialText.lines(r, List.of(), 3).size());
        final AirframeReadout calm = AirframeMath.read(AerialFixtures.cruise());
        final List<String> calmLines = AerialText.lines(calm, List.of(), 8);
        assertTrue(calmLines.get(1).startsWith("IAS "), "no warning line when there is none");
        assertTrue(calmLines.stream().anyMatch(l -> l.startsWith("NAV Harbor Tower")));
        assertTrue(calmLines.stream().anyMatch(l -> l.startsWith("LIFT +7%")));
    }
}
