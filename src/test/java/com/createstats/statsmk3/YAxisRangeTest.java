package com.createstats.statsmk3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.createstats.statsmk3.instrument.Plot;
import com.createstats.statsmk3.layout.Tile;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.ui.Rect;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

class YAxisRangeTest {
    @Test
    void invalidFixedBoundsFallBackToAuto() {
        assertFalse(YAxisRange.fixed(4.0f, 4.0f).isFixed());
        assertFalse(YAxisRange.fixed(8.0f, 2.0f).isFixed());
        assertFalse(YAxisRange.fixed(Float.NaN, 2.0f).isFixed());
        assertTrue(YAxisRange.fixed(-2.0f, 2.0f).isFixed());
    }

    @Test
    void seededBoundsRoundOutward() {
        final YAxisRange range = YAxisRange.rounded(13.2f, 87.1f);

        assertTrue(range.isFixed());
        assertTrue(range.min() <= 13.2f);
        assertTrue(range.max() >= 87.1f);
        assertEquals(0.0f, range.min(), 1.0e-5f);
        assertEquals(100.0f, range.max(), 1.0e-5f);
    }

    @Test
    void tileAndPageRangesRoundTripIndependently() {
        final Tile tile = new Tile(1, 2, 3, 4);
        tile.setYAxis(YAxisRange.fixed(-10.0f, 40.0f));
        final Tile loadedTile = Tile.load(tile.save());
        assertEquals(YAxisRange.fixed(-10.0f, 40.0f), loadedTile.yAxis());

        final Mk3Settings settings = new Mk3Settings();
        settings.setLiveYRange("energy", YAxisRange.fixed(0.0f, 200.0f));
        settings.setAlertYRange("energy", YAxisRange.fixed(25.0f, 75.0f));
        final CompoundTag tag = new CompoundTag();
        settings.save(tag);

        final Mk3Settings loaded = new Mk3Settings();
        loaded.load(tag);
        assertEquals(YAxisRange.fixed(0.0f, 200.0f), loaded.liveYRange("energy"));
        assertEquals(YAxisRange.fixed(25.0f, 75.0f), loaded.alertYRange("energy"));
        assertFalse(loaded.liveYRange("missing").isFixed());
    }

    @Test
    void oldSettingsAndTilesRemainAuto() {
        assertFalse(Tile.load(new Tile(0, 0, 1, 1).save()).yAxis().isFixed());

        final CompoundTag tag = new CompoundTag();
        new Mk3Settings().save(tag);
        final Mk3Settings loaded = new Mk3Settings();
        loaded.load(tag);
        assertFalse(loaded.liveYRange("energy").isFixed());
        assertFalse(loaded.alertYRange("energy").isFixed());
    }

    @Test
    void fixedPlotClipsAndReportsBothEdges() {
        final ChannelView channel = new ChannelView("test");
        channel.history = new float[]{-5.0f, 5.0f, 15.0f};
        channel.historyCount = channel.history.length;
        final Rect area = new Rect(10.0f, 20.0f, 100.0f, 50.0f);

        final Plot.Points points = Plot.map(channel, area, 20, 0.1f,
                YAxisRange.fixed(0.0f, 10.0f), new Plot.Points());

        assertTrue(points.clippedLow);
        assertTrue(points.clippedHigh);
        assertEquals(area.bottom(), points.xy[1], 1.0e-5f);
        assertEquals(area.y, points.xy[5], 1.0e-5f);
    }
}
