package com.createstats.client.ponder.powertrack;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerTrackOverlayTest {
    private static final Path KIT = Path.of("src/main/java/com/createstats/client/ponder/powertrack");
    private static final Pattern ON_BASE = Pattern.compile("\\bclass\\s+(\\w+)\\s+extends\\s+PowerTrackOverlay\\b");
    private static final Pattern PONDER_FADE = Pattern.compile(
            "\\bAnimatedOverlayElement(Base)?\\b|\\bPonderOverlayElement\\b|\\.setFade\\(|\\bforceApplyFade\\(");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.list(KIT)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void everyOverlayHoldsPondersFadeAtOneAtEveryPartialTick() throws Exception {
        final List<String> found = new ArrayList<>();
        for (Path p : sources()) {
            final String outer = p.getFileName().toString().replace(".java", "");
            final Matcher m = ON_BASE.matcher(read(p));
            while (m.find()) {
                found.add(outer.equals(m.group(1)) ? outer : outer + "$" + m.group(1));
            }
        }

        assertTrue(found.size() >= 2, "found only " + found + " on PowerTrackOverlay: the tag and the slow-motion note, so the "
                + "scan has stopped seeing them");
        for (String name : found) {
            final Class<?> type = Class.forName("com.createstats.client.ponder.powertrack." + name);
            final Constructor<?> make = type.getDeclaredConstructor();
            make.setAccessible(true);
            final PowerTrackOverlay overlay = (PowerTrackOverlay) make.newInstance();
            for (float pt : new float[] {0f, 0.25f, 0.5f, 0.999f, 1f}) {
                assertEquals(1f, overlay.getFade(pt), 1.0e-6f, name + "'s fade at partial tick " + pt
                        + ": anything but 1 strobes once a scene tick (KI-130)");
            }
        }
    }

    @Test
    void noClassReachesPondersOverlayFadeButTheBase() throws IOException {
        final List<Path> files = sources();
        assertFalse(files.isEmpty(), "no sources under " + KIT + ": the scan checks nothing");
        boolean sawBase = false;
        for (Path p : files) {
            final String text = read(p);
            if (p.getFileName().toString().equals("PowerTrackOverlay.java")) {
                sawBase = true;
                int set = 0;
                for (int at = text.indexOf("setFade(1f);"); at >= 0; at = text.indexOf("setFade(1f);", at + 1)) {
                    set++;
                }
                assertEquals(2, set, "PowerTrackOverlay must set Ponder's fade to 1 exactly twice (KI-130)");
                continue;
            }
            final Matcher m = PONDER_FADE.matcher(text);
            if (m.find()) {
                throw new AssertionError(p.getFileName() + " reaches Ponder's overlay fade directly (\"" + m.group()
                        + "\"): extend PowerTrackOverlay and fade by its own level (KI-130)");
            }
        }
        assertTrue(sawBase, "PowerTrackOverlay.java is missing");
    }
}
