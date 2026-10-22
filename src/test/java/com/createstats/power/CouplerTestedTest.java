package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerTestedTest {
    @Test
    @DisplayName("the checked-in file is where the tooltip reads it, and it parses")
    void theFileIsWhereTheLoaderLooks() throws IOException {
        final Path file = Path.of("src/main/resources" + CouplerTested.RESOURCE);
        assertTrue(Files.isRegularFile(file), "the tooltip reads " + CouplerTested.RESOURCE + " and it is not there");
        final List<CouplerTested.Entry> entries =
                CouplerTested.parse(new StringReader(Files.readString(file, StandardCharsets.UTF_8)));
        for (CouplerTested.Entry e : entries) {
            assertTrue(!e.modId().isEmpty() && !e.version().isEmpty() && !e.cases().isEmpty(), e.toString());
        }
    }

    @Test
    @DisplayName("only passing entries with at least one case are listed")
    void onlyPassing() {
        final String json = """
                {"tested": [
                  {"modid": "create_new_age", "name": "Create: New Age", "version": "1.2.0", "passed": true,
                   "cases": ["bolted PULL", "bolted PUSH", "wired PULL", "wired PUSH"]},
                  {"modid": "mekanism", "name": "Mekanism", "version": "10.7.19", "passed": false,
                   "cases": ["wired PULL"]},
                  {"modid": "powah", "name": "Powah", "version": "6.2.10", "passed": true, "cases": []},
                  {"modid": "enderio", "name": "Ender IO", "version": "8.2.12-beta"}
                ]}""";
        final List<CouplerTested.Entry> entries = CouplerTested.parse(new StringReader(json));
        assertEquals(1, entries.size());
        assertEquals("create_new_age", entries.get(0).modId());
        assertEquals("bolted PULL, bolted PUSH, wired PULL, wired PUSH", entries.get(0).howTested());
    }

    @Test
    @DisplayName("an empty or malformed file lists nothing rather than guessing")
    void emptyIsEmpty() {
        assertTrue(CouplerTested.parse(new StringReader("{\"tested\": []}")).isEmpty());
        assertTrue(CouplerTested.parse(new StringReader("{}")).isEmpty());
        assertTrue(CouplerTested.parse(new StringReader("[]")).isEmpty());
    }
}
