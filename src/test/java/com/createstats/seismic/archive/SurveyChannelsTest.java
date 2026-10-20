package com.createstats.seismic.archive;

import java.util.List;
import java.util.Map;

import com.createstats.seismic.SeismicWave;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyChannelsTest {
    private static final String IRON = "c:ores/iron";
    private static final double RX = SurveyFixtures.PANEL_X + 0.5;
    private static final double RZ = SurveyFixtures.PANEL_Z + 0.5;

    @Test
    @DisplayName("the nearest block along the ground, and every block known, as the scene has them")
    void figuresMatchTheScene() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final ArchiveBlow b = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, 1_000L);
        final List<Long> rigs = List.of(b.rigKey());
        final long late = 1_000L + 40_000L;
        final Map<String, SurveyChannels.Figures> f = SurveyChannels.compute(a.snapshot(rigs, Long.MIN_VALUE), RX, RZ,
                late);
        final SurveyChannels.Figures iron = f.get(IRON);
        assertEquals(b.cellCount(), iron.blocks(), "every block known, once");
        final SurveyScene s = SceneBuilder.build(a, rigs, rigs, Long.MIN_VALUE, SurveyFixtures.PANEL_X,
                SurveyFixtures.PANEL_Y, SurveyFixtures.PANEL_Z, SceneBuilder.DEFAULT_BUDGET, late, 1L);

        final SurveyFinds.Find near = SurveyFinds.nearest(s, IRON, -1, RX, RZ);
        assertTrue(iron.nearest() <= near.horiz() + 1.0e-9, iron.nearest() + " against " + near.horiz());
        assertEquals(0L, iron.heard(), "long after: nothing heard in the last five seconds");
        assertEquals(0L, iron.fresh(), "and nothing in the last ten minutes");
    }

    @Test
    @DisplayName("a block counts once its echo is back at the plate; the five second count rises and falls")
    void heardRisesAndFalls() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final long at = 1_000L;
        final ArchiveBlow b = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, at);
        final List<ArchiveBlow> snap = a.snapshot(List.of(b.rigKey()), Long.MIN_VALUE);
        assertTrue(SurveyChannels.compute(snap, RX, RZ, at).get(IRON).blocks() < b.cellCount() / 4,
                "at the landing almost nothing is heard yet");
        final int listen = SeismicWave.listenTicks(48);
        final SurveyChannels.Figures during = SurveyChannels.compute(snap, RX, RZ, at + listen).get(IRON);
        assertEquals(b.cellCount(), during.blocks(), "by the end of listening, everything");
        assertTrue(during.heard() > 0, "the alarm count is up while echoes arrive");
        final SurveyChannels.Figures after = SurveyChannels.compute(snap, RX, RZ,
                at + listen + SurveyChannels.HEARD_TICKS + 1).get(IRON);
        assertEquals(0L, after.heard(), "and down five seconds after the last echo");
        assertEquals(b.cellCount(), after.fresh(), "while the ten minute count still holds them");
        assertTrue(SurveyChannels.hot(snap, at + listen) && !SurveyChannels.hot(snap, at + listen + 200L));
        assertTrue(SurveyChannels.warm(snap, at + listen + 200L));
    }

    @Test
    @DisplayName("a snapshot is a copy: the archive moving on does not move it")
    void aSnapshotStaysPut() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final ArchiveBlow first = g.strike(a, SiteGround.WEST_RIG, IRON, 48, 1_000L);
        final List<ArchiveBlow> snap = a.snapshot(List.of(first.rigKey()), Long.MIN_VALUE);
        final int before = snap.get(0).cellCount();
        g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, 2_000L);
        assertTrue(first.cellCount() < before, "the newer blow took over ground in the archive");
        assertEquals(before, snap.get(0).cellCount(), "the copy still reads the ground as it was copied");
    }
}
