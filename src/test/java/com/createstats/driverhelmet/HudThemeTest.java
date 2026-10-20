package com.createstats.driverhelmet;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudThemeTest {
    @Test
    void theOrderIsSavedOnHelmetsSoItNeverChanges() {
        assertEquals(0, HudTheme.TELEMETRY.ordinal());
        assertEquals(1, HudTheme.DARK.ordinal());
        assertEquals(2, HudTheme.RACING.ordinal());
        assertEquals(3, HudTheme.CREATE.ordinal());
        assertEquals(4, HudTheme.PUNK.ordinal());
    }

    @Test
    void onlyPunkGlows() {
        for (HudTheme t : HudTheme.values()) {
            assertEquals(t == HudTheme.PUNK, t.glows(), t.id);
        }
    }

    @Test
    void telemetryIsExactlyTheLookTheOwnerCalledPerfect() {
        final HudTheme t = HudTheme.TELEMETRY;
        assertEquals(0xFFB23A, t.primary);
        assertEquals(0x7FA4FF, t.cool);
        assertEquals(0x62FF8C, t.ok);
        assertEquals(0xFF5245, t.bad);
        assertEquals(0xF2E6CF, t.text);
        assertEquals(0x14161B, t.plate);
        assertEquals(0.82, t.plateAlpha, 1e-9);
        assertEquals(0x62FF8C, t.shiftLow);
        assertEquals(0xFFB23A, t.shiftMid);
        assertEquals(0x7FA4FF, t.shiftHigh);
    }

    @Test
    void theKeyCyclesThroughEveryThemeAndABadOrdinalIsTheFirst() {
        final Set<HudTheme> seen = new HashSet<>();
        int t = 0;
        for (int i = 0; i < HudTheme.values().length; i++) {
            seen.add(HudTheme.of(t));
            t = HudTheme.next(t).ordinal();
        }
        assertEquals(HudTheme.values().length, seen.size());
        assertEquals(0, t, "back to the start after a full cycle");
        assertEquals(HudTheme.TELEMETRY, HudTheme.of(-1));
        assertEquals(HudTheme.TELEMETRY, HudTheme.of(99));
    }

    @Test
    void everyThemeIsItsOwnPalette() {
        final Set<String> palettes = new HashSet<>();
        for (HudTheme t : HudTheme.values()) {
            palettes.add(t.plate + "/" + t.primary + "/" + t.stripe);
        }
        assertEquals(HudTheme.values().length, palettes.size());
    }

    @Test
    void aHelmetSavedBeforeThemesAndTheOdometerLoadsWithTheirDefaults() {
        final JsonObject old = new JsonObject();
        old.addProperty("open", true);
        old.addProperty("stroke", 1234L);
        old.addProperty("night_vision", true);
        final HelmetVisor v = HelmetVisor.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        assertTrue(v.open());
        assertEquals(1234L, v.strokeTick());
        assertTrue(v.nightVision());
        assertEquals(0, v.theme());
        assertEquals(0.0, v.odometer(), 0.0);
        final HelmetVisor round = HelmetVisor.CODEC.parse(JsonOps.INSTANCE,
                HelmetVisor.CODEC.encodeStart(JsonOps.INSTANCE, v.nextTheme().withOdometer(5000.0)).getOrThrow())
                .getOrThrow();
        assertEquals(1, round.theme());
        assertEquals(5000.0, round.odometer(), 0.0);
    }
}
