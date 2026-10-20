package com.createstats.statsmk3.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.createstats.blockentity.StatsDisplayPanelBE;

import org.junit.jupiter.api.Test;

class SignalsCacheTest {
    @Test
    void windowAndStatisticsAreReusedUntilAStoreRevisionChanges() {
        final StatsDisplayPanelBE.DataChannel channel =
                new StatsDisplayPanelBE.DataChannel("test", "Test");
        channel.getStore().addSampleDirect(1.0f);
        channel.getStore().addSampleDirect(3.0f);
        channel.getStore().addSampleDirect(2.0f);
        final Signals.Signal signal = Signals.signal(channel, null, false, 0);

        final float[] firstWindow = Signals.window(signal, Signals.FULL_WINDOW);
        final Signals.Stats firstStats = Signals.stats(signal, Signals.FULL_WINDOW, 0L);
        assertSame(firstWindow, Signals.window(signal, Signals.FULL_WINDOW));
        assertSame(firstStats, Signals.stats(signal, Signals.FULL_WINDOW, 0L));

        channel.getStore().addSampleDirect(8.0f);
        final float[] secondWindow = Signals.window(signal, Signals.FULL_WINDOW);
        final Signals.Stats secondStats = Signals.stats(signal, Signals.FULL_WINDOW, 0L);
        assertNotSame(firstWindow, secondWindow);
        assertNotSame(firstStats, secondStats);
        assertEquals(4, secondStats.count);
        assertEquals(8.0f, secondStats.hi);
    }
}
