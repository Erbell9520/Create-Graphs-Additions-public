package com.createstats.client.ponder.seismic;

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

class SeismicOverlayTest {
    private static final Path KIT = Path.of("src/main/java/com/createstats/client/ponder/seismic");
    private static final Pattern ON_BASE = Pattern.compile("\\bclass\\s+(\\w+)\\s+extends\\s+SeismicOverlay\\b");
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

        assertTrue(found.size() >= 7, "found only " + found + " on SeismicOverlay: the tag, the hub, the recorder, the "
                + "slow-motion note, the reading, the vein labels and the rings' figures, so the scan has stopped seeing them");
        for (String name : found) {
            final Class<?> type = Class.forName("com.createstats.client.ponder.seismic." + name);
            final Constructor<?> make = type.getDeclaredConstructor();
            make.setAccessible(true);
            final SeismicOverlay overlay = (SeismicOverlay) make.newInstance();
            for (float pt : new float[] {0f, 0.25f, 0.5f, 0.999f, 1f}) {
                assertEquals(1f, overlay.getFade(pt), 1.0e-6f, name + "'s fade at partial tick " + pt
                        + ": anything but 1 strobes once a scene tick (KI-130)");
            }
        }
    }

    @Test
    void theReadingKeepsItsLineClearOfLabelsWhileItShows() throws IOException {
        final int start = 100;
        final int ticks = 120;
        assertFalse(SeismicOverlays.readingUp(start - 1, start, ticks), "before the reading");
        assertTrue(SeismicOverlays.readingUp(start, start, ticks), "from its first tick, as it fades in");

        final int gone = start + ticks - SeismicOverlays.READING_OUT + SeismicOverlay.FADE;
        assertTrue(SeismicOverlays.readingUp(gone - 1, start, ticks), "while it fades out");
        assertFalse(SeismicOverlays.readingUp(gone, start, ticks), "once it is gone");
        final int[] strip = SeismicOverlays.Reading.rect(240, 426, 240);
        assertEquals(426 / 2 - 120 - 3, strip[0]);
        assertEquals(240 - 68 - 2, strip[1], "the action bar's height, vanilla's height - 68, and its veil");
        assertEquals(426 / 2 + 120 + 3, strip[2]);
        assertEquals(240 - 68 + 10, strip[3]);
        assertFalse(VeinView.clearOf(strip, 213f, 20f, 165f, 184f), "a label on the line");
        assertTrue(VeinView.clearOf(strip, 213f, 20f, 140f, strip[1]), "a label just over it");
        assertTrue(VeinView.clearOf(strip, strip[0] - 21f, 20f, 165f, 184f), "a label beside it");
        assertTrue(VeinView.clearOf(null, 213f, 20f, 165f, 184f), "nothing kept free");
        final String scenes = read(KIT.resolve("SeismicScenes.java")).replaceAll("\\s+", " ");
        assertTrue(scenes.contains("VeinView.fixed(stand.eye(0)), VeinView.never(), "
                        + "SeismicOverlays.readingClear(line, readFrom, readFor), c.now, length - c.now)"),
                "the survey's veins must keep the reading's strip clear, with the reading's own line and times");
        assertTrue(scenes.contains("c.until(scene, readFrom); scene.addInstruction(SeismicOverlays.reading(line, c.now, readFor));"),
                "the reading must show the line and the times its strip is kept for");
    }

    @Test
    void noClassReachesPondersOverlayFadeButTheBase() throws IOException {
        final List<Path> files = sources();
        assertFalse(files.isEmpty(), "no sources under " + KIT + ": the scan checks nothing");
        boolean sawBase = false;
        for (Path p : files) {
            final String text = read(p);
            if (p.getFileName().toString().equals("SeismicOverlay.java")) {
                sawBase = true;
                int set = 0;
                for (int at = text.indexOf("setFade(1f);"); at >= 0; at = text.indexOf("setFade(1f);", at + 1)) {
                    set++;
                }
                assertEquals(2, set, "SeismicOverlay must set Ponder's fade to 1 exactly twice (KI-130)");
                continue;
            }
            final Matcher m = PONDER_FADE.matcher(text);
            if (m.find()) {
                throw new AssertionError(p.getFileName() + " reaches Ponder's overlay fade directly (\"" + m.group()
                        + "\"): extend SeismicOverlay and fade by its own level (KI-130)");
            }
        }
        assertTrue(sawBase, "SeismicOverlay.java is missing");
    }
}
