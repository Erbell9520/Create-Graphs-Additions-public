package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeSourcePricingCoverageTest {
    private static final Path BLOCK_ENTITIES = Path.of("src/main/java/com/createstats/blockentity");
    private static final Path BOARD =
            Path.of("src/main/java/com/createstats/blockentity/PowerDistributionUnitBE.java");

    private static final Pattern GIVE_ONLY = Pattern.compile(
            "public boolean canReceive\\(\\)\\s*\\{\\s*return\\s+false\\s*;\\s*\\}");

    @Test
    @DisplayName("every block entity with a give-only energy handler can be priced by a board")
    void everyGiveOnlyBlockEntityIsAnFeSupply() throws IOException {
        final List<String> found = new ArrayList<>();
        final List<String> unpriced = new ArrayList<>();
        try (Stream<Path> files = Files.list(BLOCK_ENTITIES)) {
            for (final Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                final String source = Files.readString(file, StandardCharsets.UTF_8);
                if (!GIVE_ONLY.matcher(source).find()) {
                    continue;
                }
                final String name = file.getFileName().toString().replace(".java", "");
                found.add(name);

                final Matcher declaration = Pattern
                        .compile("public\\s+(?:abstract\\s+)?class\\s+" + name + "\\b[^{]*\\{",
                                Pattern.DOTALL)
                        .matcher(source);
                assertTrue(declaration.find(), name + ": could not read its class declaration");
                if (!code(declaration.group()).contains("FeSupply")) {
                    unpriced.add(name);
                }
            }
        }

        assertTrue(found.size() >= 5,
                "only " + found.size() + " give-only handlers found (" + found + "); the scan has "
                        + "stopped working rather than the mod losing its generators");
        assertTrue(found.contains("KineticDynamoBE") && found.contains("BiofuelBurnerBE"),
                "the scan missed a known generator: " + found);
        assertTrue(unpriced.isEmpty(),
                "these block entities hand FE to a circuit and do not implement FeSupply, so a Power "
                        + "Distribution Unit fed by one prices it at zero, budgets Active Auto "
                        + "against its own buffer and sheds machines that are being fed: " + unpriced);
    }

    @Test
    @DisplayName("the board prices supply through the interface, not through a list of classes")
    void theBoardPricesThroughTheInterface() throws IOException {
        final String body = methodBody(Files.readString(BOARD, StandardCharsets.UTF_8),
                "int ownGenerationOf(");
        assertTrue(body.contains("FeSupply"),
                "ownGenerationOf must ask FeSupply. A chain of instanceof over concrete block classes "
                        + "is the defect: every generator added afterwards prices at zero until "
                        + "somebody remembers to extend it, and nothing fails when they do not.\n"
                        + body);

        assertTrue(body.contains("potentialFePerTickFor("),
                "ownGenerationOf must read the figure off FeSupply.potentialFePerTickFor(this board)\n" + body);
    }

    @Test
    @DisplayName("the survey's generator case still reaches the pricing method")
    void theGeneratorCaseIsStillPriced() throws IOException {
        final String body = methodBody(Files.readString(BOARD, StandardCharsets.UTF_8),
                "void surveyMember(");
        assertTrue(body.contains("ownGenerationOf("),
                "surveyMember's GENERATOR case must price the node through ownGenerationOf, or every "
                        + "source of ours on a feed contributes nothing to knownGeneration however "
                        + "well ownGenerationOf itself is written\n" + body);
    }

    private static String code(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String methodBody(String source, String signature) {
        final int at = source.indexOf(signature);
        assertTrue(at >= 0, "method not found, so this test now guards nothing: " + signature);
        final int open = source.indexOf('{', at);
        assertTrue(open >= 0, "no body after " + signature);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open + 1, i);
            }
        }
        throw new IllegalStateException("unbalanced braces after " + signature);
    }
}
