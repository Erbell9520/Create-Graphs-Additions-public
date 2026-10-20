package com.createstats.train.map;

import java.util.List;
import java.util.UUID;

import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitForArtTest {
    @Test
    void anArrowStopsShortOfBothTrains() {
        final float[] out = new float[6];
        final int n = WaitForArt.arrowPath(0.0f, 0.0f, 100.0f, 0.0f, 0.0f, 8.0f, out);

        assertEquals(3, n);
        assertEquals(8.0f, out[0], 0.5f, "it starts a gap out from the waiting train");
        assertEquals(92.0f, out[4], 0.5f, "and ends a gap short of the one it waits on");
        assertEquals(0.0f, out[5], 0.01f, "no bow is a straight arrow");
    }

    @Test
    void theArrowHeadSitsAtTheBlocker() {
        final float[] out = new float[6];
        WaitForArt.arrowPath(10.0f, 10.0f, 10.0f, 210.0f, 0.0f, 8.0f, out);
        final float endY = out[4 + 1];
        assertTrue(endY > 190.0f && endY < 210.0f,
                "the last point of the path is next to the blocker, got y=" + endY);
    }

    @Test
    void oppositeArrowsInATwoTrainRingBowApart() {
        final float[] ab = new float[6];
        final float[] ba = new float[6];
        WaitForArt.arrowPath(0.0f, 0.0f, 60.0f, 0.0f, 10.0f, 6.0f, ab);
        WaitForArt.arrowPath(60.0f, 0.0f, 0.0f, 0.0f, 10.0f, 6.0f, ba);

        assertTrue(ab[3] * ba[3] < 0.0f,
                "the two directions of a mutual wait must bow to opposite sides, got "
                        + ab[3] + " and " + ba[3]);
        assertTrue(Math.abs(ab[3] - ba[3]) > 8.0f, "and far enough apart to read as two arrows");
    }

    @Test
    void theCircleEnclosesEveryTrainInTheRing() {
        final float[] pts = {0.0f, 0.0f, 20.0f, 0.0f, 20.0f, 20.0f, 0.0f, 20.0f};
        final float[] out = new float[3];
        WaitForArt.enclose(pts, 4, out);

        assertEquals(10.0f, out[0], 0.01f);
        assertEquals(10.0f, out[1], 0.01f);
        for (int i = 0; i < 4; i++) {
            final float d = (float) Math.hypot(pts[i * 2] - out[0], pts[i * 2 + 1] - out[1]);
            assertTrue(d <= out[2] + 0.01f, "train " + i + " is outside the circle");
        }
    }

    @Test
    void drawingToleratesATrainItCannotLocate() {
        final RailSnapshot snap = new RailSnapshot();
        final RailSnapshot.TrainState a = held("A");
        final RailSnapshot.TrainState b = held("B");
        final RailSnapshot.TrainState c = held("C");
        a.blockedByTrain = b.id;
        b.blockedByTrain = c.id;
        c.blockedByTrain = a.id;
        snap.trains().addAll(List.of(a, b, c));

        final List<TrainIntel.Stall> stalls = TrainIntel.stalls(snap);
        assertEquals(1, stalls.size());

        final RasterSurface surface = new RasterSurface(200, 200, 0x111318);
        final Rect plot = new Rect(0, 0, 200, 200);

        WaitForArt.draw(surface, plot, stalls, id -> {
            if (id.equals(a.id)) {
                return new float[]{60.0f, 80.0f};
            }
            if (id.equals(c.id)) {
                return new float[]{140.0f, 120.0f};
            }
            return null;
        }, 0.0f, 1.0f, true);
        surface.finish();
    }

    private static RailSnapshot.TrainState held(String name) {
        final RailSnapshot.TrainState t = new RailSnapshot.TrainState();
        t.id = UUID.nameUUIDFromBytes(name.getBytes());
        t.name = name;
        t.dimension = "minecraft:overworld";
        t.waitingForSignal = true;
        t.ticksWaitingForSignal = TrainIntel.SIGNAL_HOLD_ALERT_TICKS + 300;
        return t;
    }
}
