package com.createstats.power;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduAutoTierIdsTest {
    private static final Path PDU =
            Path.of("src/main/java/com/createstats/blockentity/PowerDistributionUnitBE.java");

    private static final Path LANG =
            Path.of("src/main/resources/assets/create_stats/lang/en_us.json");

    private static final Pattern TIER_KEY =
            Pattern.compile("Map\\.entry\\(\"([a-z0-9_]+)\",\\s*FePriority\\.");

    private static final Pattern SUFFIX_RULE =
            Pattern.compile("path\\.endsWith\\(\"([a-z0-9_]+)\"\\)");

    private static final Pattern REGISTERED =
            Pattern.compile("\"block\\.create_stats\\.([a-z0-9_]+)\"");

    @Test
    void everyAutoTierKeyNamesARealBlock() throws IOException {
        final Set<String> registered = matches(read(LANG), REGISTERED);
        final String pdu = read(PDU);
        final List<String> dead = new ArrayList<>();
        for (String key : matches(pdu, TIER_KEY)) {
            if (!registered.contains(key)) {
                dead.add(key);
            }
        }
        assertTrue(dead.isEmpty(),
                "auto-tier keys that match no registered block, so auto assign silently skips them: " + dead);
    }

    @Test
    void everySuffixRuleMatchesAFamilyThatExists() throws IOException {
        final Set<String> registered = matches(read(LANG), REGISTERED);
        final Set<String> suffixes = matches(read(PDU), SUFFIX_RULE);
        assertFalse(suffixes.isEmpty(), "the suffix rules disappeared; the families they cover have not");
        for (String suffix : suffixes) {
            assertTrue(registered.stream().anyMatch(id -> id.endsWith(suffix)),
                    "no registered block ends with \"" + suffix + "\", so that rule tiers nothing");
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Set<String> matches(String source, Pattern pattern) {
        final Set<String> out = new LinkedHashSet<>();
        final Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
        return out;
    }
}
