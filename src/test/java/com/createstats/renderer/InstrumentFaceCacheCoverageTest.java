package com.createstats.renderer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstrumentFaceCacheCoverageTest {
    private static final Path RENDERER = Path.of("src/main/java/com/createstats/renderer");

    private static final String DRAWING_MARKER = "// ---------- Drawing ----------";

    private static final String COMPOSITION_MARKER = "// ---------- Composition ----------";

    private static final String SIGNATURE_METHOD = "static int signatureOf(";

    private static final Pattern FACE_RECORD =
            Pattern.compile("\\brecord Face\\s*\\(([^)]*)\\)", Pattern.DOTALL);

    private static final Pattern COMPONENT =
            Pattern.compile("(\\w+(?:\\.\\w+)*(?:\\[])?)\\s+(\\w+)\\s*(?:,|$)");

    private static final Pattern DERIVED = Pattern.compile(
            "^        (?:boolean|float|int|long|String)\\s+(\\w+)\\(\\)\\s*\\{", Pattern.MULTILINE);

    @Test
    void pduFaceSignatureCoversEveryValueItDraws() throws IOException {
        assertSignatureCoversDrawing("PowerDistributionUnitTextureManager.java", DRAWING_MARKER);
    }

    @Test
    void batteryControllerFaceSignatureCoversEveryValueItDraws() throws IOException {
        assertSignatureCoversDrawing("BatteryControllerTextureManager.java", DRAWING_MARKER);
    }

    @Test
    void capacitorBankFaceSignatureCoversEveryValueItDraws() throws IOException {
        assertSignatureCoversDrawing("CapacitorBankTextureManager.java", COMPOSITION_MARKER);
    }

    @Test
    void convertedRenderersDoNotLayOutTextPerFrame() throws IOException {
        assertConverted("PowerDistributionUnitRenderer.java", "PowerDistributionUnitTextureManager");
        assertConverted("BatteryControllerRenderer.java", "BatteryControllerTextureManager");
        assertConverted("CapacitorBankRenderer.java", "CapacitorBankTextureManager");
    }

    private static void assertSignatureCoversDrawing(String file, String marker) throws IOException {
        final Path path = RENDERER.resolve(file);
        assertTrue(Files.exists(path), file + " is missing");
        final String source = Files.readString(path, StandardCharsets.UTF_8);

        final int split = source.indexOf(marker);
        assertTrue(split > 0, file + " has no '" + marker + "' section marker, so this test cannot"
                + " tell its signature from its drawing. Restore the marker rather than deleting"
                + " the check.");

        final Set<String> names = faceNames(source, file);
        final Map<String, Set<String>> derivedFrom = derivedDependencies(source, names);
        final Set<String> folded =
                namesUsedIn(sectionFrom(source, SIGNATURE_METHOD, marker, file), names);
        final Set<String> drawn = namesUsedIn(source.substring(split), names);

        assertFalse(folded.isEmpty(), file + "'s signatureOf folds in no part of its Face at all");
        assertFalse(drawn.isEmpty(), file + "'s drawing reads no part of its Face at all, which"
                + " means this test is looking at the wrong section of the file");

        for (String name : drawn) {
            assertTrue(covered(name, folded, derivedFrom, new LinkedHashSet<>()),
                    file + " draws face." + name + "() but signatureOf never folds it in, and does"
                            + " not fold everything it is derived from either, so that value will"
                            + " freeze on the glass while the rest of the face keeps updating.");
        }

        if (mentions(bodyOf(source.substring(split)), "blinkPhase")) {
            assertTrue(
                    mentions(bodyOf(sectionFrom(source, SIGNATURE_METHOD, marker, file)),
                            "blinkPhase"),
                    file + " draws with the blink phase but signatureOf never folds it in, so a"
                            + " blinking lamp stops blinking.");
        }
    }

    private static boolean mentions(String section, String token) {
        return Pattern.compile("\\b\\Q" + token + "\\E\\b").matcher(section).find();
    }

    private static String bodyOf(String section) {
        final int brace = section.indexOf('{');
        return brace < 0 ? section : section.substring(brace + 1);
    }

    private static boolean covered(String name, Set<String> folded,
                                   Map<String, Set<String>> derivedFrom, Set<String> visiting) {
        if (folded.contains(name)) {
            return true;
        }
        final Set<String> sources = derivedFrom.get(name);

        if (sources == null || sources.isEmpty() || !visiting.add(name)) {
            return false;
        }
        for (String source : sources) {
            if (!covered(source, folded, derivedFrom, visiting)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Set<String>> derivedDependencies(String source, Set<String> names) {
        final Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        final Matcher derived = DERIVED.matcher(source);
        while (derived.find()) {
            final String name = derived.group(1);
            final int end = source.indexOf("\n        }", derived.end());
            if (end < 0) {
                continue;
            }
            final String body = source.substring(derived.end(), end);
            final Set<String> used = new LinkedHashSet<>();
            for (String candidate : names) {
                if (!candidate.equals(name)
                        && Pattern.compile("\\b\\Q" + candidate + "\\E\\b").matcher(body).find()) {
                    used.add(candidate);
                }
            }
            dependencies.put(name, used);
        }
        return dependencies;
    }

    private static Set<String> faceNames(String source, String file) {
        final Matcher record = FACE_RECORD.matcher(source);
        assertTrue(record.find(), file + " has no 'record Face(...)'. The face is supposed to be a"
                + " pure function of one record; if that changed, this test has to change with it"
                + " rather than be deleted.");
        final Set<String> names = new LinkedHashSet<>();
        final Matcher component = COMPONENT.matcher(record.group(1).replaceAll("\\s+", " ").trim());
        while (component.find()) {
            names.add(component.group(2));
        }
        assertFalse(names.isEmpty(), file + "'s Face record parsed to no components");
        final Matcher derived = DERIVED.matcher(source);
        while (derived.find()) {
            names.add(derived.group(1));
        }
        return names;
    }

    private static String sectionFrom(String source, String from, String to, String file) {
        final int start = source.indexOf(from);
        assertTrue(start > 0, file + " has no '" + from + "'");
        final int end = source.indexOf(to, start);
        assertTrue(end > start, file + " has no '" + to + "' after '" + from + "'");
        return source.substring(start, end);
    }

    private static Set<String> namesUsedIn(String section, Set<String> names) {
        final Set<String> used = new LinkedHashSet<>();
        for (String name : names) {
            if (Pattern.compile("\\bface\\.\\Q" + name + "\\E\\s*\\(").matcher(section).find()) {
                used.add(name);
            }
        }
        return used;
    }

    private static void assertConverted(String file, String manager) throws IOException {
        final Path path = RENDERER.resolve(file);
        assertTrue(Files.exists(path), file + " is missing");

        final String source = stripComments(Files.readString(path, StandardCharsets.UTF_8));

        assertTrue(source.contains(manager + ".getOrUpdateTexture"),
                file + " no longer draws its face from " + manager + ". If the face was moved"
                        + " somewhere else, move this check with it rather than dropping it.");
        assertFalse(source.contains("drawInBatch"),
                file + " lays text out with the font again. That is a full text layout per visible"
                        + " instance per frame, which is what moving this face to a cached texture"
                        + " removed, and nothing at runtime will tell you it came back.");
        assertFalse(source.contains("String.format"),
                file + " formats a string inside the render path again. It runs every frame for a"
                        + " figure that changes at most once a tick; it belongs in the texture"
                        + " manager's draw, behind the signature.");
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
