package com.createstats.sound;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundAssetFormatTest {
    private static final Path SOUNDS = Path.of("src/main/resources/assets/create_stats/sounds");
    private static final Path SOUNDS_JSON = Path.of("src/main/resources/assets/create_stats/sounds.json");
    private static final byte[] VORBIS_ID = {0x01, 'v', 'o', 'r', 'b', 'i', 's'};

    @Test
    void everyShippedSoundIsMonoVorbis() throws IOException {
        final List<String> wrong = new ArrayList<>();
        final List<Path> files = oggFiles();
        assertFalse(files.isEmpty(), "found no .ogg files under " + SOUNDS);
        for (final Path file : files) {
            final byte[] head = readHead(file, 512);
            final int at = indexOf(head, VORBIS_ID);
            if (at < 0 || at + 16 > head.length) {
                wrong.add(SOUNDS.relativize(file) + " is not Ogg Vorbis (the only format Minecraft plays)");
                continue;
            }
            final int channels = head[at + 11] & 0xFF;
            if (channels != 1) {
                wrong.add(SOUNDS.relativize(file) + " has " + channels
                    + " channels, so it would play unpositioned at full volume everywhere");
            }
        }
        assertTrue(wrong.isEmpty(), "Sounds that are not mono Vorbis:\n  " + String.join("\n  ", wrong));
    }

    @Test
    void theFlywheelSoundEventsPointAtRealFiles() throws IOException {
        final String json = Files.readString(SOUNDS_JSON, StandardCharsets.UTF_8);
        for (final String name : new String[] {"flywheel_loop", "flywheel_charged", "flywheel_overstress"}) {
            assertTrue(json.contains("\"create_stats:" + name + "\""), "sounds.json has no entry for " + name);
            assertTrue(Files.isRegularFile(SOUNDS.resolve(name + ".ogg")), name + ".ogg is missing");
        }
    }

    private static List<Path> oggFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(SOUNDS)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".ogg")).sorted().toList();
        }
    }

    private static byte[] readHead(Path file, int max) throws IOException {
        final byte[] all = Files.readAllBytes(file);
        final byte[] head = new byte[Math.min(max, all.length)];
        System.arraycopy(all, 0, head, 0, head.length);
        return head;
    }

    private static int indexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= data.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
