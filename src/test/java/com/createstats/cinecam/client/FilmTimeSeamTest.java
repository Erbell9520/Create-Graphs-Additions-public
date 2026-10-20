package com.createstats.cinecam.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FilmTimeSeamTest {
    @Test
    void vanillaStillHasTheFields() throws NoSuchFieldException {
        assertEquals(float.class, DeltaTracker.Timer.class.getDeclaredField(FilmTime.RESIDUAL).getType());
        assertEquals(float.class, Camera.class.getDeclaredField(FilmTime.EYE_HEIGHT).getType());
        assertEquals(float.class, Camera.class.getDeclaredField(FilmTime.EYE_HEIGHT_OLD).getType());
    }

    @Test
    void theLookupTheGameUsesFindsThem() {
        for (Field f : new Field[]{FilmTime.field(DeltaTracker.Timer.class, FilmTime.RESIDUAL),
                FilmTime.field(Camera.class, FilmTime.EYE_HEIGHT), FilmTime.field(Camera.class, FilmTime.EYE_HEIGHT_OLD)}) {
            assertNotNull(f);
            assertEquals(float.class, f.getType());
        }
    }
}
