package com.createstats.vccmk4.route;

import com.createstats.surveyor.core.Comfort;
import com.createstats.surveyor.core.PlantModel;
import com.createstats.surveyor.core.RouteRunner;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RouteNbtTest {
    @Test
    void aRunnersSaveComesBackAsItWent() {
        RouteRunner.Saved s = new RouteRunner.Saved(RouteRunner.State.WAITING, "cargo full", 123.25, 2, 7, 98765L, 9, true, 3, 131.5);
        assertEquals(s, RouteNbt.readSaved(RouteNbt.writeSaved(s)));
    }

    @Test
    void everyStateComesBackByName() {
        for (RouteRunner.State st : RouteRunner.State.values()) {
            RouteRunner.Saved s = new RouteRunner.Saved(st, "", 0.0, -1, -1, 0L, -1, false, 0, -1.0);
            assertEquals(st, RouteNbt.readSaved(RouteNbt.writeSaved(s)).state());
            assertEquals(st.key, RouteNbt.writeSaved(s).getString("State"), "saved as its key");
        }
    }

    @Test
    void aLearnedPlantComesBack() {
        PlantModel p = new PlantModel(0.21, 1.9, 0.45, 60, 6, 1.5);
        PlantModel q = RouteNbt.readPlant(RouteNbt.writePlant(p), new PlantModel(128));
        assertEquals(p.k(), q.k(), 1e-12);
        assertEquals(p.tau(), q.tau(), 1e-12);
        assertEquals(p.tauBrake(), q.tauBrake(), 1e-12);
        assertEquals(p.samples(), q.samples());
        assertEquals(p.brakeSamples(), q.brakeSamples());
        assertEquals(p.skidGain(), q.skidGain(), 1e-12);
        assertEquals(p.learned(), q.learned());
    }

    @Test
    void optionsComeBackWithTheirOwnComfortOrThePathways() {
        RouteRunner.Options own = new RouteRunner.Options(12.5, Comfort.BRISK, false, false);
        assertEquals(own, RouteNbt.readOptions(RouteNbt.writeOptions(own)));
        RouteRunner.Options pathways = new RouteRunner.Options(0.0, null, true, true);
        RouteRunner.Options back = RouteNbt.readOptions(RouteNbt.writeOptions(pathways));
        assertNull(back.comfortOverride(), "no comfort of its own stays none, not the first comfort");
        assertEquals(pathways, back);
    }

    @Test
    void aSaveFromBeforeRoutesReadsAsNone() {
        CompoundTag empty = new CompoundTag();
        assertSame(RouteRunner.Options.DEFAULT, RouteNbt.readOptions(empty));
        PlantModel fallback = new PlantModel(128);
        assertSame(fallback, RouteNbt.readPlant(empty, fallback), "an empty plant keeps the fresh one");
        RouteRunner.Saved s = RouteNbt.readSaved(empty);
        assertEquals(RouteRunner.State.PAUSED, s.state());
        assertEquals(-1, s.waitingAt(), "not waiting at node 0");
        assertEquals(-1, s.sendTo(), "not sent to node 0");
        assertEquals(-1.0, s.lastLap(), 0.0);
    }
}
