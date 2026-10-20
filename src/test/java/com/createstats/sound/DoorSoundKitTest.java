package com.createstats.sound;

import com.createstats.block.DoorOpening;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorSoundKitTest {
    private static final Path ASSETS =
            Paths.get("src", "main", "resources", "assets", "create_stats");

    private static JsonObject read(Path path) throws IOException {
        assertTrue(Files.exists(path), "missing " + path);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    @DisplayName("every opening maps to a kit, and every kit names only real clips")
    void everyOpeningHasAKit() {
        for (final DoorOpening style : DoorOpening.values()) {
            final DoorSound.Kit kit = DoorSound.kit(style);
            assertNotNull(kit, style + " has no sound kit");
            assertNotNull(kit.depart(), style + " has nothing to play when it starts moving");
            for (final DoorClip clip : kit.clips()) {
                assertNotNull(clip, style + " names a null clip");
            }
        }
    }

    @Test
    @DisplayName("every clip has an ogg file on disk")
    void everyClipHasAnAsset() {
        for (final DoorClip clip : DoorClip.values()) {
            final Path ogg = ASSETS.resolve("sounds").resolve(clip.path() + ".ogg");
            assertTrue(Files.exists(ogg),
                    clip + " has no asset at " + ogg + "; run gen_powered_door_sounds.py");
        }
    }

    @Test
    @DisplayName("every clip has a sounds.json entry pointing at its own file")
    void everyClipIsDeclared() throws IOException {
        final JsonObject sounds = read(ASSETS.resolve("sounds.json"));
        for (final DoorClip clip : DoorClip.values()) {
            assertTrue(sounds.has(clip.soundId()),
                    clip + " has no sounds.json entry under " + clip.soundId());
            final JsonObject entry = sounds.getAsJsonObject(clip.soundId());
            assertTrue(entry.has("sounds"), clip.soundId() + " declares no sounds");
            final String named = entry.getAsJsonArray("sounds").get(0)
                    .getAsJsonObject().get("name").getAsString();
            assertTrue(named.equals("create_stats:" + clip.path()),
                    clip.soundId() + " points at " + named + " rather than its own file");
        }
    }

    @Test
    @DisplayName("every clip carries a subtitle, and the lang file has a line for it")
    void everyClipHasASubtitle() throws IOException {
        final JsonObject sounds = read(ASSETS.resolve("sounds.json"));
        final JsonObject lang = read(ASSETS.resolve("lang").resolve("en_us.json"));
        for (final DoorClip clip : DoorClip.values()) {
            final JsonObject entry = sounds.getAsJsonObject(clip.soundId());
            assertTrue(entry.has("subtitle"),
                    clip.soundId() + " has no subtitle, so it is silent to the subtitle track");
            final String key = entry.get("subtitle").getAsString();
            assertTrue(key.equals(clip.subtitleKey()),
                    clip.soundId() + " uses subtitle key " + key + ", expected "
                            + clip.subtitleKey());
            assertTrue(lang.has(key), "en_us.json has no line for " + key);
            assertTrue(!lang.get(key).getAsString().isBlank(), key + " is blank");
        }
    }

    @Test
    @DisplayName("every clip is actually reachable from some opening or from the door's own controls")
    void nothingIsOrphaned() {
        final java.util.EnumSet<DoorClip> reached = java.util.EnumSet.noneOf(DoorClip.class);
        for (final DoorOpening style : DoorOpening.values()) {
            reached.addAll(DoorSound.kit(style).clips());
        }

        reached.add(DoorClip.REFUSE);
        reached.add(DoorClip.STALL);
        reached.add(DoorClip.CONFIG);
        for (final DoorClip clip : DoorClip.values()) {
            assertTrue(reached.contains(clip), clip + " is registered and shipped but never played");
        }
    }

    @Test
    @DisplayName("loop windows and cue points sit inside the travel, in the order they happen")
    void cuePointsAreOrdered() {
        for (final DoorOpening style : DoorOpening.values()) {
            final DoorSound.Kit kit = DoorSound.kit(style);
            if (kit.loop() != null) {
                assertTrue(kit.loopFrom() >= 0.0f && kit.loopFrom() < kit.loopTo(),
                        style + " has a loop window that starts at or after it ends");
                assertTrue(kit.loopTo() <= 1.0f, style + " loops past the end of its travel");
            }
            if (kit.midway() != null) {
                assertTrue(kit.midwayAt() > 0.0f && kit.midwayAt() < kit.arriveAt(),
                        style + " clacks at " + kit.midwayAt() + ", which is not before it arrives");
            }
            assertTrue(kit.arriveAt() > 0.5f && kit.arriveAt() <= 1.0f,
                    style + " arrives at " + kit.arriveAt() + ", which is not near the end");
        }
    }

    @Test
    @DisplayName("a heavier door is pitched lower and louder than a lighter one")
    void materialColouringIsMonotonic() {
        final float lightest = 2.5f;
        final float heaviest = 5.0f;

        assertTrue(DoorSound.pitchForHardness(heaviest) < DoorSound.pitchForHardness(lightest),
                "the heaviest door on the line should be pitched below the lightest");
        assertTrue(DoorSound.volumeForHardness(heaviest) > DoorSound.volumeForHardness(lightest),
                "the heaviest door on the line should be louder than the lightest");

        float previousPitch = Float.MAX_VALUE;
        float previousGain = -1.0f;
        for (int i = 0; i <= 50; i++) {
            final float hardness = lightest + (heaviest - lightest) * i / 50.0f;
            final float pitch = DoorSound.pitchForHardness(hardness);
            final float gain = DoorSound.volumeForHardness(hardness);
            assertTrue(pitch <= previousPitch + 1.0e-5f,
                    "pitch rose with hardness at " + hardness);
            assertTrue(gain >= previousGain - 1.0e-5f,
                    "gain fell with hardness at " + hardness);
            assertTrue(pitch >= 0.5f && pitch <= 2.0f, "pitch out of range at " + hardness);
            assertTrue(gain > 0.0f && gain <= 1.0f, "gain out of range at " + hardness);
            previousPitch = pitch;
            previousGain = gain;
        }

        for (final float wild : new float[]{0.0f, 1.0f, 12.0f, 100.0f}) {
            assertTrue(DoorSound.pitchForHardness(wild) >= 0.5f
                            && DoorSound.pitchForHardness(wild) <= 2.0f,
                    "a hardness of " + wild + " produced an unusable pitch");
            assertTrue(DoorSound.volumeForHardness(wild) > 0.0f
                            && DoorSound.volumeForHardness(wild) <= 1.0f,
                    "a hardness of " + wild + " produced an unusable gain");
        }
    }

    @Test
    @DisplayName("the substance layer sits under the mechanism, not over it")
    void substanceLayerIsQuietAndLow() {
        assertTrue(DoorSound.SUBSTANCE_GAIN > 0.0f && DoorSound.SUBSTANCE_GAIN < 0.5f,
                "the material layer should be present but well under the synthesised impact");
        assertTrue(DoorSound.SUBSTANCE_PITCH > 0.0f && DoorSound.SUBSTANCE_PITCH < 1.0f,
                "the material layer should sit below the kit's own pitch, so it reads as body");
    }
}
