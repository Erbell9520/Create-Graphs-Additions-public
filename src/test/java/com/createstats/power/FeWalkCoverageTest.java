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

class FeWalkCoverageTest {
    private static final Path BLOCK_ENTITIES = Path.of("src/main/java/com/createstats/blockentity");
    private static final Path NETWORK = Path.of("src/main/java/com/createstats/power/FeWireNetwork.java");

    private static List<String> gridMembers() throws IOException {
        final List<String> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(BLOCK_ENTITIES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                final String name = file.getFileName().toString().replace(".java", "");
                final String source = Files.readString(file, StandardCharsets.UTF_8);
                final Matcher declaration = Pattern
                        .compile("public\\s+(?:abstract\\s+)?class\\s+" + name + "\\b[^{]*\\{",
                                Pattern.DOTALL)
                        .matcher(source);
                if (declaration.find() && declaration.group().contains("FeGridMember")) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("every FE grid member is matched by a branch of the flood, or it silently never charges")
    void everyGridMemberIsWalked() throws IOException {
        final String network = code(Files.readString(NETWORK, StandardCharsets.UTF_8));
        final List<String> missing = new ArrayList<>();
        for (final String member : gridMembers()) {
            if (!walked(network, member)) {
                missing.add(member);
            }
        }
        assertTrue(missing.isEmpty(),
                "these are FeGridMembers that no branch of FeWireNetwork's walk matches, so they "
                        + "join no grid and never charge, while a cable still draws an arm to them: "
                        + missing);
    }

    @Test
    @DisplayName("the flood hands the network to members through the interface, not a second chain")
    void theNetworkIsAssignedThroughTheInterface() throws IOException {
        final String network = Files.readString(NETWORK, StandardCharsets.UTF_8);
        assertTrue(network.contains("node.adoptNetwork(network)"),
                "FeWireNetwork.build must assign the finished grid by calling adoptNetwork on every "
                        + "member through FeGridMember. Without that call nothing holds the network "
                        + "it was just given.");
        final int typed = network.split(Pattern.quote("adoptNetwork("), -1).length - 1;
        assertTrue(typed == 1,
                "FeWireNetwork calls adoptNetwork " + typed + " times, which means the per type "
                        + "chain is back. A block left out of it is a member of the grid that never "
                        + "learns which grid it is a member of, and every symptom of that looks like "
                        + "something other than wiring. Assign it once, over FeGridMember.");
    }

    static boolean walked(String network, String member) {
        return Pattern.compile("node instanceof (?:com\\.createstats\\.blockentity\\.)?" + Pattern.quote(member) + "\\b")
                .matcher(network).find();
    }

    @Test
    @DisplayName("a branch for a longer type name is not a branch for a member whose name it begins (KI-138)")
    void aLongerNameIsNotABranchForAShorterOne() {
        final String longer = "} else if (node instanceof com.createstats.blockentity.TrackFeederBEx feeder) {";
        assertTrue(walked(longer, "TrackFeederBEx"), "the full name must still match");
        assertTrue(!walked(longer, "TrackFeederBE"),
                "a branch for a longer type satisfied the check for a member whose name it begins: KI-138");
        assertTrue(walked("} else if (node instanceof TrackFeederBE feeder) {", "TrackFeederBE"),
                "the short spelling, an imported type, must match too");
        assertTrue(!walked(code("// node instanceof TrackFeederBE feeder\n"), "TrackFeederBE"),
                "a branch surviving only in a comment is not a branch");
    }

    @Test
    @DisplayName("the walk still has members to check, so a rename cannot quietly empty this test")
    void theScanFindsSomething() throws IOException {
        assertTrue(gridMembers().size() > 15,
                "only " + gridMembers().size() + " grid members were found, which means the scan "
                        + "stopped matching rather than the mod losing two dozen machines");
    }

    @Test
    @DisplayName("a roof of Solar Panels is one circuit: the panel branch joins panel to panel")
    void aRoofOfSolarPanelsIsOneCircuit() throws IOException {
        final String network = code(Files.readString(NETWORK, StandardCharsets.UTF_8));
        final int branch = network.indexOf("node instanceof com.createstats.blockentity.SolarPanelBE");
        assertTrue(branch >= 0,
                "no walk branch matches SolarPanelBE, so this test now guards nothing. See "
                        + "everyGridMemberIsWalked, which is the check that should have failed first.");
        final int nextBranch = network.indexOf("node instanceof", branch + 1);
        final String body = network.substring(branch,
                nextBranch < 0 ? network.length() : nextBranch);
        assertTrue(body.contains("spreadAcrossPanels("),
                "the Solar Panel's walk branch does not call spreadAcrossPanels, so it reaches "
                        + "wiring and never the panel beside it. A roof is then one grid per panel: "
                        + "every panel a cable physically touches generates, the rest sit at a full "
                        + "buffer, and every readout on every one of them still reports a circuit, "
                        + "because each of them truly has one. KI-48.");

        final String helper = methodBody(network,
                "private static void spreadAcrossPanels(Level level, BlockPos at, Walk walk)");
        assertTrue(helper.contains("instanceof com.createstats.blockentity.SolarPanelBE"),
                "spreadAcrossPanels does not test its neighbour for a panel, so the branch calls a "
                        + "join that joins nothing: " + helper);
        assertTrue(helper.contains("walk.enqueue("),
                "spreadAcrossPanels never enqueues, so the neighbouring panel is found and then "
                        + "dropped. The flood only grows through enqueue: " + helper);
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
