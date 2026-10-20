package com.createstats.power;

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

class FeCapabilityCoverageTest {
    private static final Path BLOCK_ENTITIES = Path.of("src/main/java/com/createstats/blockentity");
    private static final Path CAPABILITIES =
            Path.of("src/main/java/com/createstats/power/FeCapabilities.java");
    private static final Path REGISTRY =
            Path.of("src/main/java/com/createstats/registry/ModBlockEntities.java");

    @Test
    void everyGridMemberRegistersAnEnergyCapability() throws IOException {
        final String capabilities = read(CAPABILITIES);
        final String registry = read(REGISTRY);

        final List<String> unregistered = new ArrayList<>();
        for (final String member : gridMembers()) {
            final String constant = constantFor(registry, member);
            if (constant == null) {
                continue;
            }
            if (!capabilities.contains("ModBlockEntities." + constant + ".get()")
                    && !capabilities.contains("com.createstats.blockentity." + member)) {
                unregistered.add(member + " (ModBlockEntities." + constant + ")");
            }
        }

        assertTrue(unregistered.isEmpty(),
                "These block entities are FeGridMembers but register no EnergyStorage capability in "
                        + "FeCapabilities, so cable will not connect to them, a cord will not plug "
                        + "into them and a bore will not land on them: " + unregistered);
    }

    private static List<String> gridMembers() throws IOException {
        final List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(BLOCK_ENTITIES)) {
            for (final Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                final String source = read(file);

                final Matcher m = Pattern
                        .compile("\\bimplements\\b([^{]*)\\{", Pattern.DOTALL)
                        .matcher(source);
                if (m.find() && m.group(1).contains("FeGridMember")) {
                    final String name = file.getFileName().toString();
                    names.add(name.substring(0, name.length() - ".java".length()));
                }
            }
        }
        assertTrue(names.size() > 15, "found suspiciously few grid members: " + names);
        return names;
    }

    private static String constantFor(String registry, String beClass) {
        final Matcher m = Pattern
                .compile("BlockEntityEntry<\\s*com\\.createstats\\.blockentity\\."
                        + Pattern.quote(beClass) + "\\s*>\\s+([A-Z0-9_]+)")
                .matcher(registry);
        return m.find() ? m.group(1) : null;
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), "missing source file: " + path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
