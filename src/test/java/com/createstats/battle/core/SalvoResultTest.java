package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalvoResultTest {
    private static final Map<String, String> EN = Map.ofEntries(
            Map.entry("salvo.fired", "Fired %s."),
            Map.entry("salvo.nothing", "Nothing fired: %s."),
            Map.entry("salvo.not_ready", " Not ready: %s."),
            Map.entry("salvo.none", "No gun is on Track or Fire."),
            Map.entry("salvo.item", "%s %s"),
            Map.entry("salvo.item_eta", "%s %s (about %s s)"),
            Map.entry("gun.word.empty", "empty"),
            Map.entry("gun.word.slewing", "slewing"),
            Map.entry("gun.word.loading", "loading"));

    private static final BiFunction<String, Object[], String> TR = (k, a) -> String.format(EN.getOrDefault(k, k), a);

    @Test
    void thePlansFirstExample() {
        SalvoResult r = new SalvoResult.Builder().fired("G1").fired("G3")
                .notReady("G2", GunState.EMPTY, 5).notReady("G4", GunState.SLEWING, 2).build();
        assertEquals("Fired G1, G3. Not ready: G2 empty, G4 slewing (about 2 s).", r.describe(TR));
    }

    @Test
    void thePlansNothingReadyExample() {
        SalvoResult r = new SalvoResult.Builder().notReady("G1", GunState.SLEWING, -1).notReady("G2", GunState.EMPTY, -1).build();
        assertEquals("Nothing fired: G1 slewing, G2 empty.", r.describe(TR));
    }

    @Test
    void allFiredSaysOnlyThat() {
        assertEquals("Fired G1.", new SalvoResult.Builder().fired("G1").build().describe(TR));
    }

    @Test
    void noGunAskedSaysSo() {
        assertEquals("No gun is on Track or Fire.", new SalvoResult.Builder().build().describe(TR));
    }

    @Test
    void onlyASlewingGunCarriesATime() {
        SalvoResult r = new SalvoResult.Builder().notReady("G2", GunState.LOADING, 3).build();
        assertEquals("Nothing fired: G2 loading.", r.describe(TR));
    }

    @Test
    void encodeAndDecodeRoundTrip() {
        SalvoResult r = new SalvoResult.Builder().fired("Big Bertha").fired("G3")
                .notReady("G2, left", GunState.EMPTY, -1).notReady("G4", GunState.SLEWING, 2).build();
        SalvoResult back = SalvoResult.decode(r.encode());
        assertEquals(r.describe(TR), back.describe(TR));
        assertEquals(0, SalvoResult.decode("garbage\u0001x").fired().size(), "damage decodes to empty, never throws");
        assertEquals(0, SalvoResult.decode("").notReady().size());
    }
}
