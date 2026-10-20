package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FePassThroughRateTest {
    private static final int BASE = 8_192;

    private static final Path NETWORK = Path.of("src/main/java/com/createstats/power/FeWireNetwork.java");

    @Test
    @DisplayName("two spans in series carry what the thinner one carries")
    void theThinnerSpanBinds() {
        assertEquals(BASE, FeWireNetwork.passThroughRate(WireTier.COPPER, WireTier.HV));
        assertEquals(BASE, FeWireNetwork.passThroughRate(WireTier.HV, WireTier.COPPER));
        assertEquals(BASE * WireTier.HV.multiplier(), FeWireNetwork.passThroughRate(WireTier.HV, WireTier.HV));
        assertEquals(BASE * WireTier.INSULATED.multiplier(),
                FeWireNetwork.passThroughRate(WireTier.SHIELDED, WireTier.INSULATED));
        assertEquals(BASE, FeWireNetwork.passThroughRate(null, null), "an unknown grade is copper, as tierOf's default");
    }

    private static String code(String text) {
        return text.replace("\r\n", "\n").replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String body(String source, String signature) {
        final int at = source.indexOf(signature);
        assertTrue(at >= 0, "not found, so this test guards nothing: " + signature);
        final int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(at, i + 1);
            }
        }
        throw new AssertionError("unbalanced braces after " + signature);
    }

    private static String branch(String walk, String test) {
        final int at = walk.indexOf(test);
        assertTrue(at >= 0, "no branch " + test);
        final int next = walk.indexOf("if (linkBE instanceof", at + test.length());
        return walk.substring(at, next < 0 ? walk.length() : next);
    }

    @Test
    @DisplayName("both pass-through branches rate the edge they make, and both edge builders read the rate")
    void theRateReachesBothMechanisms() throws IOException {
        final String net = code(Files.readString(NETWORK, StandardCharsets.UTF_8));
        final String walk = body(net, "private static void walkTerminalLinks(");
        for (String test : new String[] {"linkBE instanceof com.createstats.blockentity.DiagnosticJunctionBE junction",
                "linkBE instanceof com.createstats.blockentity.ContactorBE contactor"}) {
            final String b = branch(walk, test);
            assertTrue(b.contains("walk.ratePassThrough(") && b.contains("passThroughRate("),
                    "the branch " + test + " puts an edge in the tree and leaves it unrated");
        }

        assertTrue(body(net, "private static TreeArrays treeArrays(").contains("passRate.getOrDefault(keys[i]"),
                "the shared edge builder builds its edge rates without the pass-through edges' (MECH-2 and MECH-4 blind to them)");
        assertTrue(net.contains("treeArrays(found, attachments, walk.parentOf, walk.passRate, parallelSinks)"),
                "the shared edge builder is not fed the walk's pass rates");
        for (String builder : new String[] {"private static FeConduitThroat conduitThroatOf(",
                "private static FePathBudget pathBudgetOf("}) {
            assertTrue(body(net, builder).contains("tree.edgeRate()"),
                    builder + " builds without the shared edge rates");
        }
        assertTrue(net.contains("conduitThroatOf(found, attachments, flowOwners, tree)"));
        assertTrue(net.contains("pathBudgetOf(found, attachments, flowOwners, tree)"));
        final String record = body(net, "void ratePassThrough(");
        assertTrue(record.contains("passRate.put(child.asLong(), rate)"), "the rate is keyed by the edge's child");
    }
}
