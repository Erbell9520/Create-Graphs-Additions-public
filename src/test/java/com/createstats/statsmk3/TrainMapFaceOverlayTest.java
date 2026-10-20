package com.createstats.statsmk3;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.train.smart.ClientRailNetwork;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainMapFaceOverlayTest {
    @Test
    void aWaitForCycleReachesTheBlockFace() {
        final CountingSurface locked = renderMapFace(faceDeadlock(true));
        final CountingSurface calm = renderMapFace(faceDeadlock(false));

        assertEquals(1, TrainIntel.stalls(faceDeadlock(true)).size(),
                "the fixture has to deadlock");
        assertTrue(TrainIntel.stalls(faceDeadlock(false)).isEmpty(), "and the control must not");

        assertTrue(locked.triangles > calm.triangles + 12,
                "the face draws the wait-for graph a plain held train does not ("
                        + locked.triangles + " vs " + calm.triangles + ")");
    }

    @Test
    void drawsOnAFaceForEyeballing() throws IOException {
        final File out = new File("fixture_preview");
        if (!out.exists() && !out.mkdirs()) {
            throw new IOException("could not create " + out);
        }
        ClientRailNetwork.accept(RailFixtures.deadlock(), true);
        final PanelModel model = new PanelModel();
        model.faceMode = Mk3Settings.FaceMode.TRAIN_MAP;
        model.mapFocus = Mk3Settings.MapFocus.NETWORK;
        model.title = "Railway";
        PreviewImages.write(RailFixtures.render(model, Lod.FULL, 4, 3, 0.5f), "png",
                new File(out, "mk3_face_train_deadlock.png"));
    }

    private static CountingSurface renderMapFace(RailSnapshot snap) {
        ClientRailNetwork.accept(snap, true);
        final PanelModel model = new PanelModel();
        model.faceMode = Mk3Settings.FaceMode.TRAIN_MAP;
        model.mapFocus = Mk3Settings.MapFocus.NETWORK;
        model.title = "Railway";
        final CountingSurface surface = new CountingSurface();
        PanelPainter.paint(surface, RailFixtures.face(4, 3, 0.5f), model, Lod.FULL);
        return surface;
    }

    private static RailSnapshot faceDeadlock(boolean cyclic) {
        final RailSnapshot snap = new RailSnapshot();
        snap.serverTick = 7000L;
        snap.topologyChecksum = 63;

        final RailSnapshot.Station hub = RailFixtures.station("Kreuz", 0, 0);
        final RailSnapshot.Station n = RailFixtures.station("Nord", 0, -140);
        final RailSnapshot.Station e = RailFixtures.station("Ost", 140, 0);
        final RailSnapshot.Station w = RailFixtures.station("West", -140, 0);
        snap.stations().addAll(List.of(hub, n, e, w));
        snap.links().add(RailFixtures.link(hub, n, 140.0, new float[]{0, 0, 0, -140}));
        snap.links().add(RailFixtures.link(hub, e, 140.0, new float[]{0, 0, 140, 0}));
        snap.links().add(RailFixtures.link(hub, w, 140.0, new float[]{0, 0, -140, 0}));

        final RailSnapshot.TrainState a = RailFixtures.train("Kohlezug", 0.0f, -12, -34);
        final RailSnapshot.TrainState b = RailFixtures.train("Erzzug", 0.0f, 34, 10);
        final RailSnapshot.TrainState c = RailFixtures.train("Holzzug", 0.0f, -34, 8);
        for (final RailSnapshot.TrainState t : List.of(a, b, c)) {
            t.waitingForSignal = true;
            t.ticksWaitingForSignal = 1100;
            t.stops.addAll(List.of("Kreuz", "Nord", "Ost", "West"));
            t.currentEntry = 1;
            snap.trains().add(t);
        }
        if (cyclic) {
            a.blockedByTrain = b.id;
            b.blockedByTrain = c.id;
            c.blockedByTrain = a.id;
        }
        return snap;
    }
}
