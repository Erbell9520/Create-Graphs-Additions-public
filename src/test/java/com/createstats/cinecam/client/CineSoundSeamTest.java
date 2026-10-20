package com.createstats.cinecam.client;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CineSoundSeamTest {
    @Test
    void vanillaStillHasTheFields() throws NoSuchFieldException {
        assertEquals(SoundEngine.class, SoundManager.class.getDeclaredField(CineSound.SOUND_ENGINE).getType());
        assertEquals(Map.class, SoundEngine.class.getDeclaredField(CineSound.PLAYING).getType());
    }

    @Test
    void theLookupFindsThem() {
        assertNotNull(CineSound.field(SoundManager.class, CineSound.SOUND_ENGINE, SoundEngine.class));
        assertNotNull(CineSound.field(SoundEngine.class, CineSound.PLAYING, Map.class));
    }
}
