package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalPowerCouplerGuardTest {
    private static final Path SOURCE =
            Path.of("src/main/java/com/createstats/power/FeCapabilities.java");

    @Test
    @DisplayName("no block but the Coupler offers its energy handler to a New Age connector")
    void everyEnergyRegistrationIsGuarded() throws IOException {
        final List<String> lines = Files.readAllLines(SOURCE, StandardCharsets.UTF_8);
        final List<String> unguarded = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            final boolean byType = lines.get(i)
                    .contains("registerBlockEntity(Capabilities.EnergyStorage.BLOCK");
            final boolean byBlock = lines.get(i)
                    .contains("registerBlock(Capabilities.EnergyStorage.BLOCK");
            if (!byType && !byBlock) {
                continue;
            }

            final StringBuilder window = new StringBuilder();
            for (int j = i; j < lines.size(); j++) {
                window.append(lines.get(j));
                if (lines.get(j).stripTrailing().endsWith(");")) {
                    break;
                }
            }
            final String text = window.toString();
            if (text.contains("UNIVERSAL_POWER_COUPLER")) {
                continue;
            }
            if (!text.contains(byBlock ? "guardBlock(" : "guard(")) {
                unguarded.add((i + 1) + ": " + lines.get(i).trim());
            }
        }
        assertTrue(unguarded.isEmpty(),
                "unguarded energy registrations, each one a place a New Age connector can destroy FE:\n"
                        + String.join("\n", unguarded));
    }
}
