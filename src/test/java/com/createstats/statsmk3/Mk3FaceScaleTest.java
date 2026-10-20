package com.createstats.statsmk3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.createstats.statsmk3.layout.Mk3Layout;
import com.createstats.statsmk3.layout.Tile;
import com.createstats.statsmk3.paint.Lod;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

class Mk3FaceScaleTest {
    @Test
    void aPanelSavedBeforeTheSettingExistedTakesTheNewDefault() {
        final Mk3Settings before = new Mk3Settings();
        final CompoundTag tag = new CompoundTag();
        before.save(tag);

        tag.getCompound("Mk3").remove("FaceScale");
        assertFalse(tag.getCompound("Mk3").contains("FaceScale"));

        final Mk3Settings loaded = new Mk3Settings();
        loaded.load(tag);

        assertEquals(Mk3Settings.DEFAULT_FACE_SCALE, loaded.faceScale, 1.0e-5f,
                "an old save must come back at the new default, not at the size it drew at before");
        assertEquals(0.5f, Mk3Settings.DEFAULT_FACE_SCALE, 1.0e-5f,
                "the default is half the original face size");
    }

    @Test
    void aScaleSurvivesARoundTrip() {
        for (final float scale : Mk3Settings.FACE_SCALES) {
            final Mk3Settings source = new Mk3Settings();
            source.faceScale = scale;
            final CompoundTag tag = new CompoundTag();
            source.save(tag);

            final Mk3Settings loaded = new Mk3Settings();
            loaded.load(tag);
            assertEquals(scale, loaded.faceScale, 1.0e-5f);
            assertEquals(Mk3Settings.faceScaleIndex(scale), Mk3Settings.faceScaleIndex(loaded.faceScale));
        }
    }

    @Test
    void anAbsurdScaleIsClamped() {
        for (final float bad : new float[]{0.0f, -4.0f, 1000.0f, Float.NaN}) {
            final CompoundTag tag = new CompoundTag();
            new Mk3Settings().save(tag);
            tag.getCompound("Mk3").putFloat("FaceScale", bad);

            final Mk3Settings loaded = new Mk3Settings();
            loaded.load(tag);
            assertTrue(loaded.faceScale >= Mk3Settings.MIN_FACE_SCALE
                            && loaded.faceScale <= Mk3Settings.MAX_FACE_SCALE,
                    "face scale " + bad + " loaded as " + loaded.faceScale);
        }
    }

    @Test
    void theDetailRangesFollowTheScale() {
        boolean sawADifference = false;
        for (int metres = 1; metres <= 60; metres++) {
            final double dsq = metres * (double) metres;
            final boolean fullAtFullSize = Lod.forView(dsq, 1, 1, 1.0f).showsLabels();
            final boolean fullAtThird = Lod.forView(dsq, 1, 1, 0.3f).showsLabels();
            assertFalse(fullAtThird && !fullAtFullSize,
                    "a 30% face kept its labels at " + metres + "m where a full size one had lost them");
            sawADifference |= fullAtFullSize && !fullAtThird;
        }
        assertTrue(sawADifference, "the scale made no difference to the level of detail at any range");
    }

    @Test
    void placingATileWhereItAlreadyIsIsNotAChange() {
        final Mk3Layout layout = Mk3Layout.onGrid(8, 8);
        final Tile tile = layout.add(new Tile(1, 1, 2, 2));
        layout.presetId = "quad";

        assertFalse(layout.place(tile, 1, 1, 2, 2), "a no-op place reported a change");
        assertEquals("quad", layout.presetId, "a no-op place cleared the preset");

        assertTrue(layout.place(tile, 2, 1, 2, 2), "a real move reported no change");
        assertEquals("", layout.presetId, "a real move left the preset selected");
    }
}
