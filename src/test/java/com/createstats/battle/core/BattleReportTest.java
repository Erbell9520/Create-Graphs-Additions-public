package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleReportTest {
    static BattleReport sample() {
        return new BattleReport("ship:1f2e", false, "Airship Skyhook", true, true, 123456L, -1, 120000L, 1841, 42, 5, 213, 9,
                true, 3, BattleReport.BOW_THRUST, 65, BattleReport.OPT_BELL | BattleReport.OPT_CHAT, 1,
                ComparatorScale.MODE_UNDER_FIRE,
                List.of(new BattleReport.SystemLine(1500, 1533), new BattleReport.SystemLine(200, 206),
                        new BattleReport.SystemLine(1, 2), new BattleReport.SystemLine(30, 30),
                        new BattleReport.SystemLine(3, 4), new BattleReport.SystemLine(12, 12),
                        new BattleReport.SystemLine(19, 20)),
                List.of(new BattleReport.HitLine(123400L, "S:2:0:1", 20, ""),
                        new BattleReport.HitLine(123380L, "P:2", 1, "block.aeronautics.andesite_propeller")),
                123400L, "M:Magazine",
                List.of(new BattleReport.SectionLine(99L, "Engine room", 1000, "S:0:1:0"),
                        new BattleReport.SectionLine(-7L, "Magazine", 875, "S:1:1:0")),
                List.of("minecraft:spruce_planks", "create:andesite_casing"),
                7, 31, new byte[]{1, 2, 3, (byte) 0xFF}, "Builder", 12, 100000L, true, true,
                new BattleReport.TargetLine(1, "Kestrel", 55, "0f8e2a4c-1b2d-4e5f-8a9b-0c1d2e3f4a5b", 212.5f, 45.25f, -3.5f,
                        12.75f, 90.5f, 8.25f, "Alex", 120000L, -1L),
                List.of(new BattleReport.GunLine(1, "Big Bertha", GunState.READY.ordinal(), GunState.MODE_FIRE, 0, 1, 45.3f,
                                12.1f, 0, 0f, 0f, "", GunArc.FREE, net.minecraft.core.BlockPos.asLong(20_480_050, 71, -20_480_008)),

                        new BattleReport.GunLine(3, "G3", GunState.BLOCKED.ordinal(), GunState.MODE_TRACK, 1, 240, -90.5f, 3.2f,
                                1, 1.5f, 2.5f, "S:1:0:1", new GunArc(120, -120, -10, 30), BattleReport.GunLine.NO_MOUNT)),
                3, 123000L, new SalvoResult.Builder().fired("Big Bertha").notReady("G3", GunState.BLOCKED, -1).build().encode());
    }

    @Test
    void everyFieldSurvivesTheWire() throws Exception {
        BattleReport a = sample();
        BattleReport b = BattleReport.decode(a.encode());
        assertNotNull(b);
        for (RecordComponent c : BattleReport.class.getRecordComponents()) {
            Object va = c.getAccessor().invoke(a);
            Object vb = c.getAccessor().invoke(b);
            if (va instanceof byte[] ba) {
                assertArrayEquals(ba, (byte[]) vb, c.getName());
            } else {
                assertEquals(va, vb, c.getName() + " was lost on the wire");
            }
        }
    }

    @Test
    void theSampleSetsEveryFieldAwayFromItsDefault() throws Exception {
        BattleReport s = sample();
        BattleReport e = BattleReport.empty("", true, false);
        for (RecordComponent c : BattleReport.class.getRecordComponents()) {
            Object vs = c.getAccessor().invoke(s);
            Object ve = c.getAccessor().invoke(e);
            if (vs instanceof byte[] bs) {
                assertTrue(bs.length != ((byte[]) ve).length, c.getName());
            } else {
                assertTrue(!vs.equals(ve), c.getName() + " is the same in the sample and the empty report");
            }
        }
    }

    @Test
    void aReportStaysSmall() {
        assertTrue(sample().encode().length < 2048, "the update tag budget is 2 KB (4.7)");
    }

    @Test
    void derivedReadings() {
        BattleReport s = sample();
        assertEquals(977, s.integrityPermille());
        assertEquals(1000, BattleReport.empty("k", true, true).integrityPermille());
        assertEquals(56L, s.hitAge(123456L));
        assertEquals(HeaderStatus.Kind.FIRE, s.status(123456L).kind());
        assertNull(BattleReport.decode(new byte[]{99}), "a newer format is refused, not misread");
        assertNull(BattleReport.decode(new byte[0]));
    }
}
