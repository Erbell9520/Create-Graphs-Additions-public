package com.createstats.client.ponder.powertrack;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerTrackPonderLookTest {
    private static final Path FEEDER = Path.of("src/main/java/com/createstats/blockentity/TrackFeederBE.java");

    @Test
    void theNeedleMirrorsTheFeedersClientTick() throws IOException {
        final String src = Files.readString(FEEDER, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue(src.contains("needleVelocity += (target - needle) * " + f(FeederLook.PULL) + ";"), "the spring's pull moved");
        assertTrue(src.contains("needleVelocity *= " + f(FeederLook.DAMPING) + ";"), "the damping moved");
        assertTrue(src.contains("NEEDLE_STOP = " + f(FeederLook.STOP) + ";"), "the stop pins moved");
        assertTrue(src.contains("needleVelocity *= " + f(FeederLook.BOUNCE) + ";"), "the bounce moved");
    }

    private static String f(float v) {
        return String.format(Locale.ROOT, "%sf", v);
    }

    @Test
    void aFullReadingStrikesThePinAndSettles() {
        final FeederLook look = new FeederLook();
        float peak = 0f;
        for (int t = 0; t < 60; t++) {
            look.tick(1f);
            peak = Math.max(peak, look.needle);
        }
        assertEquals(FeederLook.STOP, peak, 1e-6, "the overshoot strikes the pin");
        assertEquals(1f, look.needle, 0.01f, "and settles at full scale");
        for (int t = 0; t < 60; t++) {
            look.tick(0f);
        }
        assertEquals(0f, look.needle, 0.01f, "a zero reading rests");
        assertEquals(look.needle, look.needle(1f), 1e-6f);
    }
}
