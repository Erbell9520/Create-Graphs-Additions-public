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

class FeTerminalWalkCoverageTest {
    private static final Path BLOCK_ENTITIES = Path.of("src/main/java/com/createstats/blockentity");
    private static final Path NETWORK = Path.of("src/main/java/com/createstats/power/FeWireNetwork.java");

    private static final List<String> DRIVEN_FROM_OUTSIDE = List.of(
            "PowerDistributionUnitBE",
            "BatteryControllerBE");

    private static List<String> terminals() throws IOException {
        final List<String> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(BLOCK_ENTITIES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                final String name = file.getFileName().toString().replace(".java", "");
                final String source = Files.readString(file, StandardCharsets.UTF_8);

                final Matcher declaration = Pattern
                        .compile("public\\s+(?:abstract\\s+)?class\\s+" + name + "\\b[^{]*\\{",
                                Pattern.DOTALL)
                        .matcher(source);
                if (declaration.find() && declaration.group().contains("FeWireTerminal")) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("every wire terminal is named in the grid walk")
    void everyTerminalIsWalked() throws IOException {
        final String network = Files.readString(NETWORK, StandardCharsets.UTF_8);
        final List<String> found = terminals();

        assertTrue(found.size() >= 8,
                "only " + found.size() + " wire terminals found; the source scan has stopped working");
        for (String terminal : found) {
            if (DRIVEN_FROM_OUTSIDE.contains(terminal)) {
                continue;
            }
            assertTrue(network.contains(terminal),
                    terminal + " implements FeWireTerminal but is never named in FeWireNetwork, so a "
                            + "wire onto it would be drawn, saved and carry nothing. Give it a branch "
                            + "in the walk, or add it to DRIVEN_FROM_OUTSIDE and say why.");
        }
    }

    @Test
    @DisplayName("the Coupler joins the grid as an attachment, never as a member")
    void couplerIsAnAttachmentNotAMember() throws IOException {
        final String coupler = Files.readString(
                BLOCK_ENTITIES.resolve("UniversalPowerCouplerBE.java"), StandardCharsets.UTF_8);
        final Matcher declaration = Pattern
                .compile("public\\s+class\\s+UniversalPowerCouplerBE\\b[^{]*\\{", Pattern.DOTALL)
                .matcher(coupler);
        assertTrue(declaration.find(), "UniversalPowerCouplerBE's declaration could not be read");

        assertTrue(!declaration.group().contains("FeGridMember"),
                "the Coupler must not be an FeGridMember: its buffer belongs to the seam, and a grid "
                        + "that counted it would plan against energy already on its way out of the mod");

        final String network = Files.readString(NETWORK, StandardCharsets.UTF_8);
        final int branch = network.indexOf("UniversalPowerCouplerBE coupler");
        assertTrue(branch >= 0, "the walk has no Universal Power Coupler branch");

        final int chainEnd = network.indexOf("// A machine already served by a connector", branch);
        assertTrue(chainEnd > branch, "the walk's shape changed; this test can no longer find its end");
        final String body = network.substring(branch, chainEnd);
        assertTrue(body.contains("new FeAttachment("),
                "the Coupler branch must join the grid as an FeAttachment on its grid handle");
        assertTrue(!body.contains("walkTerminalLinks(coupler"),
                "the Coupler branch must not walk its own links: one of them is the foreign span, and "
                        + "following it would put the other mod's connector on this grid as a second, "
                        + "unmetered copy of a crossing the Coupler already works on its own tick");
    }
}
