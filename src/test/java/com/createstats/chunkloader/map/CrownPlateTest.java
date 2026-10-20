package com.createstats.chunkloader.map;

import com.createstats.chunkloader.core.CrownBoard;
import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.createstats.chunkloader.map.CrownMapFixtures.LAW;
import static com.createstats.chunkloader.map.CrownMapFixtures.LEGENDS;
import static com.createstats.chunkloader.map.CrownMapFixtures.board;
import static com.createstats.chunkloader.map.CrownMapFixtures.fed;
import static com.createstats.chunkloader.map.CrownMapFixtures.state;
import static com.createstats.chunkloader.map.CrownMapFixtures.studs12;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownPlateTest {
    private static List<CrownPlate.Field> wide(CrownState s, CrownBoard b) {
        return CrownPlate.layout(s, b, LAW, 853, LEGENDS);
    }

    private static List<CrownPlate.Field> narrow(CrownState s, CrownBoard b) {
        return CrownPlate.layout(s, b, LAW, 640, LEGENDS);
    }

    private static void reads(List<CrownPlate.Field> f, String... texts) {
        for (int i = 0; i < 4; i++) {
            assertEquals(CrownPlate.LEGENDS[i], f.get(i).legend());
            assertEquals(texts[i], f.get(i).text(), f.get(i).legend());
        }
    }

    private static void inks(List<CrownPlate.Field> f, CrownPlate.Ink... inks) {
        for (int i = 0; i < 4; i++) {
            assertEquals(inks[i], f.get(i).ink(), f.get(i).legend());
        }
    }

    private static String g(long v) {
        return CrownPlate.group(v);
    }

    @Test
    @DisplayName("mid, 853: the unit goes before the bar is touched, the rest is v11's own text, all green")
    void mid() {
        final List<CrownPlate.Field> f = wide(fed(12, 31), studs12());
        reads(f, "12/25 YOURS 31/50", "21,898 NEXT +3,213", "FED  RIDE-THROUGH 5.0 s", "PDU 1 + 2  GENERATION OK");
        inks(f, CrownPlate.Ink.GREEN, CrownPlate.Ink.GREEN, CrownPlate.Ink.GREEN, CrownPlate.Ink.GREEN);
        assertEquals(21_898 / 131_072.0, f.get(1).bar(), 1e-9, "the draw against what the studs carry");
        assertFalse(f.get(0).hasBar());
        assertTrue(f.get(1).hasBar());

        assertTrue(f.get(1).segs().get(0).figure());
        assertFalse(f.get(1).segs().get(1).figure());
    }

    @Test
    @DisplayName("mid, 640: the short wording keeps every legend (v11 small)")
    void midNarrow() {
        reads(narrow(fed(12, 31), studs12()), "12/25 YOURS 31/50", "21,898 +3,213", "FED  RIDE 5.0 s", "PDU 1+2 GEN OK");
    }

    @Test
    @DisplayName("empty: idle while it charges toward the first chunk's ride-through; the feed stays green")
    void empty() {
        final CrownState s = state(0, 1, CrownHold.Phase.DARK, 35_840, 512, 20, CrownState.Reason.NO_POWER);
        final List<CrownPlate.Field> f = wide(s, studs12());
        reads(f, "0/25 YOURS 20/50", "0 FE/t FIRST 1,024", "CHARGING 1.8 OF 5.0 s", "PDU 1 + 2  GENERATION OK");
        inks(f, CrownPlate.Ink.IDLE, CrownPlate.Ink.IDLE, CrownPlate.Ink.IDLE, CrownPlate.Ink.GREEN);
        assertEquals(0.0, f.get(1).bar());
        reads(narrow(s, studs12()), "0/25 YOURS 20/50", "0 FIRST 1,024", "CHARGING 1.8 s", "PDU 1+2 GEN OK");
    }

    @Test
    @DisplayName("dark: red only here, the always-on readings kept, WAITING on POWER and what it needs on FEED")
    void dark() {
        final CrownState s = state(0, 16, CrownHold.Phase.DARK, 0, 0, 35, CrownState.Reason.NO_POWER);
        final List<CrownPlate.Field> f = wide(s, studs12());
        reads(f, "0/25 YOURS 35/50", "0 FE/t NEXT +" + g(LAW.nextPrice(16)), "DARK  16 WAITING",
                "SHORT " + g(LAW.totalDraw(16)) + " FE/t");
        inks(f, CrownPlate.Ink.RED, CrownPlate.Ink.RED, CrownPlate.Ink.RED, CrownPlate.Ink.RED);
        reads(narrow(s, studs12()), "0/25 YOURS 35/50", "0 +" + g(LAW.nextPrice(16)), "DARK  16 WAITING",
                "SHORT " + g(LAW.totalDraw(16)));
    }

    @Test
    @DisplayName("riding: amber on POWER and FEED, the seconds left by the crown's own policy (INV-25), the open stud named")
    void riding() {
        final int draw = LAW.totalDraw(16);
        final CrownState s = state(16, 16, CrownHold.Phase.RIDING, 388_080, draw - 9_240, 35, CrownState.Reason.NONE);
        final CrownBoard b = board(0, 1, true, false, 131_072, 0, -1);
        final List<CrownPlate.Field> f = wide(s, b);
        reads(f, "16/25 YOURS 35/50", g(draw) + " NEXT +" + g(LAW.nextPrice(16)), "RIDING 2.1 s LEFT",
                "STUD 2 OPEN  SHORT 9,240");
        inks(f, CrownPlate.Ink.GREEN, CrownPlate.Ink.GREEN, CrownPlate.Ink.AMBER, CrownPlate.Ink.AMBER);
        assertEquals(2.1, CrownPlate.rideSeconds(s), 1e-9, "stored over draw less intake, the store paying the gap");
        reads(narrow(s, b), "16/25 YOURS 35/50", g(draw) + " +" + g(LAW.nextPrice(16)), "RIDE 2.1 s", "SHORT 9,240");
    }

    @Test
    @DisplayName("NEXT reads FULL at either cap: 25 at the station or 50 for the player")
    void fullAtEitherCap() {
        assertEquals("100,707 FE/t FULL", wide(fed(25, 44), studs12()).get(1).text());
        assertEquals("21,898 FE/t FULL", wide(fed(12, 50), studs12()).get(1).text());
        assertEquals("100,707 FULL", narrow(fed(25, 44), studs12()).get(1).text());
    }

    @Test
    @DisplayName("carry: the studs' figure replaces GENERATION OK when the next chunk will not fit them (v11 carry)")
    void carry() {
        final CrownBoard b = board(2, 1, true, true, 98_304, 0, -1);
        final List<CrownPlate.Field> f = wide(fed(24, 43), b);
        assertEquals("PDU 3 + 2  CARRY 98,304", f.get(3).text(), "stud A's PDU stud first, as the mock writes it");
        assertEquals(LAW.totalDraw(24) / 98_304.0, f.get(1).bar(), 1e-9);
        assertEquals("PDU 3+2 98,304", narrow(fed(24, 43), b).get(3).text());
    }

    @Test
    @DisplayName("banks paying part of the board's load: amber, and how much (proposed)")
    void banks() {
        final CrownPlate.Field feed = wide(fed(12, 31), board(0, 1, true, true, 131_072, 1_873, -1)).get(3);
        assertEquals("PDU 1 + 2  BANKS 1,873", feed.text());
        assertEquals(CrownPlate.Ink.AMBER, feed.ink());
    }

    @Test
    @DisplayName("off a board: the crown's own studs, what arrives through them, and their grades under the bar (proposed)")
    void offABoard() {
        final List<CrownPlate.Field> f = wide(fed(12, 31), CrownBoard.NONE);
        assertEquals("STUDS A + B  IN 21,898", f.get(3).text());
        assertEquals(21_898 / 131_072.0, f.get(1).bar(), 1e-9, "two HV Lines' grades");
    }

    @Test
    @DisplayName("a crown that may not hold says why on POWER, in red")
    void blocked() {
        final List<CrownPlate.Field> f = wide(state(0, 3, CrownHold.Phase.DARK, 0, 0, 22,
                CrownState.Reason.REGION_TAKEN), studs12());
        assertEquals("REGION TAKEN", f.get(2).text());
        assertEquals(CrownPlate.Ink.RED, f.get(2).ink());
        assertEquals("OFF AT BOARD", wide(state(0, 3, CrownHold.Phase.DARK, 0, 0, 22,
                CrownState.Reason.SWITCHED_OFF), studs12()).get(2).text());
    }

    @Test
    @DisplayName("the degrade order: as written, then without the unit (the space kept), then compact figures")
    void degrade() {
        final List<CrownPlate.Seg> segs = List.of(new CrownPlate.Seg("100,707", true), new CrownPlate.Seg(" FE/t ", false),
                new CrownPlate.Seg("NEXT +", false), new CrownPlate.Seg("2,345", true));
        assertEquals(segs, CrownPlate.fit(segs, 200), "room enough: untouched");
        assertEquals("100,707 NEXT +2,345", text(CrownPlate.fit(segs, 120)));
        assertEquals("101k NEXT +2,345", text(CrownPlate.fit(segs, 60)), "the Power Meter's compact, three figures");
    }

    @Test
    @DisplayName("windows sit where v11 cuts them, and the DRAW bar is a fixed 36 (24 narrow) at the window's right")
    void windows() {
        final CrownPlate.Window w = CrownPlate.window(853, 1, 24, true, false);
        assertEquals(218, w.legendX());
        assertEquals(247, w.x0());
        assertEquals(420, w.x1());
        assertEquals(251, w.textX());
        assertEquals(416 - 36 - 4 - 251, w.room());
        assertEquals(380, w.barX());
        assertEquals(36, w.barWidth());
        assertEquals(24, CrownPlate.window(640, 1, 24, true, true).barWidth());
        assertTrue(CrownPlate.narrow(640));
        assertFalse(CrownPlate.narrow(853));
    }

    @Test
    @DisplayName("ride-through: fed is what it would ride, charging never reads past the target")
    void seconds() {
        assertEquals(5.0, CrownPlate.rideSeconds(fed(12, 31)), 1e-9);
        final CrownState full = state(0, 1, CrownHold.Phase.DARK, 10_000_000, 512, 20, CrownState.Reason.NO_POWER);
        assertEquals(5.0, CrownPlate.chargeSeconds(full, LAW), 1e-9);
        assertEquals("1.8", CrownPlate.seconds(1.75), "rounded half up, as the mock's %.1f");
    }

    private static String text(List<CrownPlate.Seg> segs) {
        final StringBuilder b = new StringBuilder();
        segs.forEach(s -> b.append(s.text()));
        return b.toString();
    }
}
