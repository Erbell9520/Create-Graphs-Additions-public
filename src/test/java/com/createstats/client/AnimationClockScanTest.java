package com.createstats.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationClockScanTest {
    private static final Path JAVA = Path.of("src/main/java/com/createstats");

    private static final String PARTIAL =
            "(?:partial(?:Tick|Ticks)?|pt|ctx\\.partialTick\\(\\)|AnimationTickHolder\\.getPartialTicks\\(\\))(?![\\w(])";
    private static final Pattern RAW_CLOCK = Pattern.compile("getGameTime\\(\\)\\s*\\+\\s*" + PARTIAL);
    private static final Pattern NAMED_SUM = Pattern.compile("([A-Za-z_]\\w*)(\\(\\))?\\s*\\+\\s*" + PARTIAL);
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    private static final Set<String> ALLOWED = Set.of("CoffeeMachineBE.java:elapsed");

    @Test
    void noAnimationAddsAPartialTickToTheRawWorldClock() throws IOException {
        final List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(JAVA)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                for (int line : scan(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8))) {
                    found.add(JAVA.relativize(file).toString().replace('\\', '/') + ":" + line);
                }
            }
        }
        assertTrue(found.isEmpty(), "partial tick added to the raw world clock, which is float arithmetic and steps on "
                + "an old world: use AnimationClock.time, or subtract the stamp before adding the partial tick. At "
                + found);
    }

    @Test
    void theScanCatchesBothShapesAndPassesTheirFixes() {
        assertEquals(Set.of(1), scan("A.java", "double t = level.getGameTime() + partial;"));
        assertEquals(Set.of(2), scan("A.java", "void f(long gameTime, float partialTick) {\n"
                + "    float s = (gameTime + partialTick) % 80f; }"));
        assertEquals(Set.of(1), scan("A.java", "long gameTime() { return 0L; } double s = gameTime() + partialTick;"));
        assertEquals(Set.of(), scan("A.java", "double t = (double) level.getGameTime() + partial;"));
        assertEquals(Set.of(), scan("A.java", "double t = level.getGameTime() + (double) partial;"));
        assertEquals(Set.of(), scan("A.java", "float e = level.getGameTime() - doorStart + partial;"));
        assertEquals(Set.of(), scan("A.java", "long delta = now - at; float e = delta + partialTick;"));
        assertEquals(Set.of(), scan("A.java", "// level.getGameTime() + partial\n/* gameTime + pt */ long gameTime;"));
        assertEquals(Set.of(1), scan("A.java", "long elapsed = brewElapsed(); float e = elapsed + partialTick;"));
        assertEquals(Set.of(), scan("CoffeeMachineBE.java", "long elapsed = brewElapsed(); float e = elapsed + partialTick;"));
    }

    static Set<Integer> scan(String fileName, String source) {
        String code = source.replace("\r\n", "\n");
        code = BLOCK_COMMENT.matcher(code).replaceAll(m -> Matcher.quoteReplacement(m.group().replaceAll("[^\\n]", " ")));
        code = LINE_COMMENT.matcher(code).replaceAll("");
        final Set<Integer> lines = new TreeSet<>();
        final Matcher raw = RAW_CLOCK.matcher(code);
        while (raw.find()) {
            if (!castToDouble(code, chainStart(code, raw.start()))) {
                lines.add(lineOf(code, raw.start()));
            }
        }
        final Matcher sum = NAMED_SUM.matcher(code);
        while (sum.find()) {
            final String name = sum.group(1);
            final boolean call = sum.group(2) != null;
            if (ALLOWED.contains(fileName + ":" + name) || !worldTick(code, name, call)) {
                continue;
            }
            final String before = code.substring(0, chainStart(code, sum.start(1))).stripTrailing();
            if (!before.endsWith("-") && !before.endsWith("(double)")) {
                lines.add(lineOf(code, sum.start()));
            }
        }
        return lines;
    }

    private static boolean worldTick(String code, String name, boolean call) {
        final Matcher decl = Pattern.compile("\\blong\\s+" + Pattern.quote(name)
                + (call ? "\\s*\\(" : "\\b(?!\\s*\\()\\s*(?:=([^;]*))?")).matcher(code);
        boolean declared = false;
        while (decl.find()) {
            declared = true;
            if (!call && decl.group(1) != null && decl.group(1).contains("-")) {
                return false;
            }
        }
        return declared;
    }

    private static int chainStart(String code, int i) {
        while (i > 0) {
            final char c = code.charAt(i - 1);
            final boolean emptyCall = c == '(' && i < code.length() && code.charAt(i) == ')';
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == ')' || emptyCall)) {
                break;
            }
            i--;
        }
        return i;
    }

    private static boolean castToDouble(String code, int start) {
        return code.substring(0, start).stripTrailing().endsWith("(double)");
    }

    private static int lineOf(String code, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
