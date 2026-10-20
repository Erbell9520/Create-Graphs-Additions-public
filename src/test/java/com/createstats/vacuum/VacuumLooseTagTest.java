package com.createstats.vacuum;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VacuumLooseTagTest {
    @Test
    void theTagFileIsWhereTheLoaderLooks() throws IOException {
        final var id = SuctionEngine.VACUUM_LOOSE.location();
        final Path file = Path.of("src/main/resources/data", id.getNamespace(), "tags/block", id.getPath() + ".json");
        assertTrue(Files.isRegularFile(file), "no tag file at " + file);
        final String json = Files.readString(file);

        assertTrue(json.contains("\"minecraft:short_grass\""), "short grass is the debris the user asked for");
        assertTrue(json.contains("\"minecraft:snow\""), "snow layers");

        assertTrue(!json.contains("\"minecraft:sand\"") && !json.contains("\"minecraft:gravel\""),
                "sand and gravel are lifted because they fall, not because they are tagged");
    }
}
