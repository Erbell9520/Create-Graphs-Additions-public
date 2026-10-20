package com.createstats.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GirderMountCoverageTest {
    private static final Path BLOCKS = Path.of("src/main/java/com/createstats/block");
    private static final Path MIXIN = Path.of("src/main/java/com/createstats/mixin/GirderBlockMixin.java");
    private static final Path MIXIN_CONFIG = Path.of("src/main/resources/create_stats.mixins.json");

    private static final List<String[]> FIXTURES = List.of(
            new String[] {"CableBlock", "public static boolean canAttachTo("},
            new String[] {"IndoorCableBlock", "private static boolean supports("},
            new String[] {"SwitchBlock", "public boolean canSurvive("},
            new String[] {"WallPlateBlock", "public boolean canSurvive("},
            new String[] {"DiagnosticJunctionBlock", "public boolean canSurvive("},

            new String[] {"LampBlock", null});

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String classHeader(String source, String name) {
        final int start = source.indexOf("public class " + name + " ");
        assertTrue(start >= 0, name + " has no public class declaration; this guard has lost its anchor");
        final int open = source.indexOf('{', start);
        assertTrue(open > start, "could not find the body of " + name);
        return source.substring(start, open);
    }

    private static String methodBody(String source, String declaration) {
        final int start = source.indexOf(declaration);
        assertTrue(start >= 0, declaration + " is gone; this guard has lost its anchor");
        final int open = source.indexOf('{', start);
        assertTrue(open > start, "could not find the body of " + declaration);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        throw new AssertionError(declaration + " has no closing brace; the file is malformed");
    }

    @Test
    @DisplayName("every fixture tells the girder which way it is bolted, and asks the girder rule to stay")
    void everyFixtureIsWired() throws IOException {
        for (String[] fixture : FIXTURES) {
            final String name = fixture[0];
            final String source = read(BLOCKS.resolve(name + ".java"));
            assertTrue(classHeader(source, name).contains("GirderMounting.Fixture"),
                    name + " does not implement GirderMounting.Fixture, so a girder never reaches it"
                            + " and it floats off the beam");
            assertTrue(methodBody(source, "public boolean isBackedBy(").contains("towardSupport"),
                    name + ".isBackedBy does not read the direction it is asked about");
            if (fixture[1] != null) {
                final String check = methodBody(source, fixture[1]);
                assertTrue(check.contains("GirderMounting.holds("),
                        name + " decides its support without GirderMounting.holds, so it refuses a girder");
                assertTrue(!check.contains("isFaceSturdy("),
                        name + " still asks isFaceSturdy directly, which no girder face ever passes");
            }
        }
    }

    @Test
    @DisplayName("any block that accepts a girder also tells the girder, and the other way round")
    void acceptingAndReachingAgree() throws IOException {
        final List<String> wrong = new ArrayList<>();
        try (Stream<Path> files = Files.list(BLOCKS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                final String name = file.getFileName().toString().replace(".java", "");
                if (name.equals("GirderMounting")) {
                    continue;
                }
                final String source = read(file);
                if (!source.contains("public class " + name + " ")) {
                    continue;
                }
                final boolean accepts = source.contains("GirderMounting.holds(");
                final boolean reaches = classHeader(source, name).contains("GirderMounting.Fixture");
                if (accepts && !reaches) {
                    wrong.add(name + " can be placed on a girder but never tells it, so it floats");
                }
                if (reaches && source.contains("isFaceSturdy(")) {
                    wrong.add(name + " is a girder fixture that still asks isFaceSturdy directly");
                }
            }
        }
        assertEquals(List.of(), wrong);
    }

    @Test
    @DisplayName("a surface cable run is routed along a plain girder, not around it")
    void cableRouterSeesGirders() throws IOException {
        final String body = methodBody(read(BLOCKS.resolve("CableBlock.java")), "public boolean isSolid(");
        assertTrue(body.contains("GirderMounting.Kind.GIRDER"),
                "CableBlock.viewOf does not count a girder as solid, so a dragged run refuses one");
    }

    @Test
    @DisplayName("the girder mixin is registered and hooks both of Create's connection helpers")
    void mixinIsApplied() throws IOException {
        final JsonObject config = JsonParser.parseString(read(MIXIN_CONFIG)).getAsJsonObject();
        boolean listed = false;
        for (JsonElement entry : config.getAsJsonArray("mixins")) {
            listed |= entry.getAsString().equals("GirderBlockMixin");
        }
        assertTrue(listed, "GirderBlockMixin is not in the common mixins list, so it is never applied");

        final String mixin = read(MIXIN);
        assertTrue(mixin.contains("@Mixin(value = GirderBlock.class"), "the mixin no longer targets GirderBlock");

        final String side = methodBody(mixin, "private static void createStats$reachSideFixture(");
        assertTrue(mixin.contains("method = \"updateState\""), "no hook on GirderBlock.updateState");
        assertTrue(side.contains("GirderMounting.fixtureOn(") && side.contains("GirderBlock.X")
                        && side.contains("GirderBlock.Z") && side.contains("cir.setReturnValue("),
                "the side hook no longer grows a stub toward a fixture");

        final String vertical = methodBody(mixin, "private static void createStats$reachVerticalFixture(");
        assertTrue(mixin.contains("method = \"updateVerticalProperty\""),
                "no hook on GirderBlock.updateVerticalProperty");
        assertTrue(vertical.contains("GirderMounting.fixtureOn(")
                        && vertical.contains("setValue(updateProperty, true)"),
                "the vertical hook no longer grows a segment toward a fixture");
    }
}
