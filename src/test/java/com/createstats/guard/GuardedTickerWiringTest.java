package com.createstats.guard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardedTickerWiringTest {
    private static final String METHOD = "getTicker\\((?:(?!\\n    \\})[\\s\\S])*?";

    private static final Path BLOCKS = Path.of("src/main/java/com/createstats/block");

    private record Site(String file, Pattern guarded, String what) {
    }

    private static final List<Site> SITES = List.of(
            new Site("PowerDistributionUnitBlock.java", Pattern.compile(METHOD
                    + "BlockFault\\.tick\\(\"Power Distribution Unit\", (\\w+),\\s*\\(\\(PowerDistributionUnitBE\\) \\1\\)::tick\\)"),
                    "the PDU"),
            new Site("PduCabinetBlock.java", Pattern.compile(METHOD
                    + "BlockFault\\.tick\\(\"PDU Cabinet\", (\\w+), \\(\\(PduCabinetBE\\) \\1\\)::tick\\)"),
                    "the PDU Cabinet"),
            new Site("SparkCrownBlock.java", Pattern.compile(METHOD
                    + "BlockFault\\.tick\\(\"Spark Crown\", (\\w+), \\1::tick\\)"),
                    "the Spark Crown"));

    private static final Pattern BARE = Pattern.compile(METHOD + "\\)\\.tick\\(\\)");

    @Test
    void everyGuardedBlockTicksThroughTheGuard() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Site site : SITES) {
            String text = read(BLOCKS.resolve(site.file()));
            if (!site.guarded().matcher(text).find()) {
                problems.add(site.file() + ": the ticker does not run " + site.what() + "'s tick through BlockFault");
            }
            if (BARE.matcher(text).find()) {
                problems.add(site.file() + ": the ticker calls a tick() straight, outside BlockFault");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
