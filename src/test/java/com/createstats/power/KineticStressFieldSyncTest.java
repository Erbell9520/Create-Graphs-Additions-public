package com.createstats.power;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KineticStressFieldSyncTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/createstats");

    private record Contract(String method, String field) {
    }

    private static final List<Contract> CONTRACTS = List.of(
            new Contract("calculateStressApplied", "lastStressApplied"),
            new Contract("calculateAddedStressCapacity", "lastCapacityProvided"));

    @Test
    void everyOverrideRecordsItsOwnAnswer() throws IOException {
        final List<String> broken = new ArrayList<>();

        for (final Path source : sources()) {
            final String body = Files.readString(source, StandardCharsets.UTF_8);
            final String name = source.getFileName().toString().replace(".java", "");

            for (final Contract contract : CONTRACTS) {
                final String signature = "float " + contract.method() + "(";
                final String methodBody = methodBody(body, signature);
                if (methodBody == null) {
                    continue;
                }
                if (!methodBody.contains(contract.field() + " =")) {
                    broken.add(name + "." + contract.method() + "() never assigns " + contract.field()
                            + ", so a restart carries the network's old total forward uncancelled");
                }
            }
        }

        assertTrue(broken.isEmpty(),
                "Kinetic stress overrides that do not record their own answer:\n  " + String.join("\n  ", broken));
    }

    private static String methodBody(String source, String signature) {
        final int at = source.indexOf(signature);
        if (at < 0) {
            return null;
        }
        final int open = source.indexOf('{', at);
        if (open < 0) {
            return null;
        }
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open + 1, i);
            }
        }
        return null;
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList();
        }
    }
}
