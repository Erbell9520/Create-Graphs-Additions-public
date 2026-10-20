package com.createstats.renderer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaSpeakerLensTest {
    private static final Path MODELS =
            Path.of("src/main/resources/assets/create_stats/models/block");

    private static final double EPS = 0.02;

    private static final double[] LENS_SPEAKER = {12.00, 10.80, 6.00, 13.00, 10.95, 8.00};
    private static final double[] LENS_MEGAPHONE = {7.20, 11.40, 7.20, 8.80, 11.60, 8.80};
    private static final double[] VOICE_SPEAKER = {4.20, 10.62, 4.20, 11.80, 10.70, 11.80};
    private static final double[] VOICE_MEGAPHONE = {4.30, 11.75, 4.30, 11.70, 11.85, 11.70};

    @Test
    void theCabinetsLensSitsOnTheBezelBarItsSeatIsCutInto() throws IOException {
        final double[] bar = element("pa_speaker", "bezel east");
        assertEquals(bar[4], LENS_SPEAKER[1], EPS,
                "the lens must start exactly at the bezel's front face, or it floats or sinks");
        assertTrue(LENS_SPEAKER[4] > bar[4] + 1.0e-6,
                "and end above it, or it is coplanar with the bezel and will z-fight");
        assertWithinFootprint(LENS_SPEAKER, bar, "cabinet lens");
    }

    @Test
    void theHornsPilotSitsOnTheMouthPlateAtTheBottomOfTheThroat() throws IOException {
        final double[] plate = element("pa_megaphone", "mouth plate");
        assertEquals(plate[4], LENS_MEGAPHONE[1], EPS, "the pilot must start at the throat's floor");
        assertTrue(LENS_MEGAPHONE[4] > plate[4] + 1.0e-6, "and stand proud of it");
        assertWithinFootprint(LENS_MEGAPHONE, plate, "horn pilot");
    }

    @Test
    void theHornsPilotIsInsideTheRimsOpening() throws IOException {
        final double[] north = element("pa_megaphone", "rim north");
        final double[] west = element("pa_megaphone", "rim west");

        final double innerZ0 = north[5];
        final double innerX0 = west[3];
        assertTrue(LENS_MEGAPHONE[0] >= innerX0 - EPS && LENS_MEGAPHONE[2] >= innerZ0 - EPS,
                "the pilot must not be tucked under the rim, or nothing can see it");
        assertTrue(LENS_MEGAPHONE[3] <= 16.0 - innerX0 + EPS
                        && LENS_MEGAPHONE[5] <= 16.0 - innerZ0 + EPS,
                "and must not overhang the other side of the opening either");
    }

    @Test
    void everyGlowIsRecessedRatherThanFloatingOffTheBlock() throws IOException {
        final double[] cap = element("pa_speaker", "dust cap");
        final double[] bezel = element("pa_speaker", "bezel east");
        assertTrue(VOICE_SPEAKER[1] >= cap[4] - EPS,
                "the cabinet's glow must clear the dust cap, which would occlude the middle of it");
        assertTrue(VOICE_SPEAKER[4] <= bezel[4] + EPS,
                "and stay inside the bezel recess");

        final double[] rim = element("pa_megaphone", "rim north");
        assertTrue(VOICE_MEGAPHONE[1] >= LENS_MEGAPHONE[4] - EPS,
                "the horn's glow must sit above its own pilot, not through it");
        assertTrue(VOICE_MEGAPHONE[4] < rim[4] - 1.0,
                "and stay at least a pixel down inside the rim, or the throat loses its depth");
    }

    @Test
    void nothingTheRendererDrawsEscapesItsOwnBlock() {
        for (double[] box : new double[][] {LENS_SPEAKER, LENS_MEGAPHONE, VOICE_SPEAKER,
                VOICE_MEGAPHONE}) {
            for (int i = 0; i < 3; i++) {
                assertTrue(box[i] >= 0.0 && box[i + 3] <= 16.0,
                        "a drawn box left the block: " + java.util.Arrays.toString(box));
                assertTrue(box[i + 3] > box[i],
                        "a drawn box is inside out: " + java.util.Arrays.toString(box));
            }
        }
    }

    private static void assertWithinFootprint(double[] box, double[] host, String what) {
        assertTrue(box[0] >= host[0] - EPS && box[3] <= host[3] + EPS,
                what + " overhangs its seat in x");
        assertTrue(box[2] >= host[2] - EPS && box[5] <= host[5] + EPS,
                what + " overhangs its seat in z");
    }

    private static double[] element(String model, String comment) throws IOException {
        final JsonObject root = JsonParser.parseString(
                Files.readString(MODELS.resolve(model + ".json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        for (var raw : root.getAsJsonArray("elements")) {
            final JsonObject el = raw.getAsJsonObject();
            if (el.has("__comment") && comment.equals(el.get("__comment").getAsString())) {
                final JsonArray from = el.getAsJsonArray("from");
                final JsonArray to = el.getAsJsonArray("to");
                return new double[] {from.get(0).getAsDouble(), from.get(1).getAsDouble(),
                        from.get(2).getAsDouble(), to.get(0).getAsDouble(), to.get(1).getAsDouble(),
                        to.get(2).getAsDouble()};
            }
        }
        throw new AssertionError("no element named '" + comment + "' in " + model
                + ". If it was renamed in gen_pa_speakers.py, rename it here too.");
    }
}
