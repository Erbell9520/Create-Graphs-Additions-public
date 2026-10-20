package com.createstats.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SupportLogTailTest {
    @TempDir
    Path dir;

    private Path write(List<String> lines) throws Exception {
        Path log = this.dir.resolve("latest.log");
        Files.write(log, lines, StandardCharsets.UTF_8);
        return log;
    }

    @Test
    void wholeSmallFileIsSearched() throws Exception {
        Path log = write(List.of(
            "[main/INFO] [create_stats/]: loaded",
            "[main/WARN] [create_stats/]: first warning",
            "[main/ERROR] [othermod/]: not ours",
            "[main/ERROR] [create_stats/]: second error"));
        SupportLogTail.Result result = SupportLogTail.read(log, 1 << 20, 60, "create_stats");
        assertTrue(result.notes().isEmpty(), "a window covering the file adds no note");
        assertEquals(List.of("[main/WARN] [create_stats/]: first warning",
            "[main/ERROR] [create_stats/]: second error"), result.matches());
    }

    @Test
    void onlyTheWindowIsReadAndItsFirstFragmentDropped() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("[main/ERROR] [create_stats/]: OLD, outside the window");
        for (int i = 0; i < 200; i++) {
            lines.add("[main/INFO] [filler/]: line " + i + " padding padding padding");
        }
        lines.add("[main/ERROR] [create_stats/]: NEW, inside the window");
        Path log = write(lines);

        SupportLogTail.Result result = SupportLogTail.read(log, 300, 60, "create_stats");
        assertEquals(List.of("[main/ERROR] [create_stats/]: NEW, inside the window"), result.matches());
        assertTrue(result.notes().get(0).startsWith("(searched the last"));
    }

    @Test
    void partialFirstLineIsNotMatched() throws Exception {
        String target = "[main/ERROR] [create_stats/]: cut in half by the window";
        Path log = write(List.of(target, "[main/INFO] [x/]: tail"));
        long size = Files.size(log);

        SupportLogTail.Result result = SupportLogTail.read(log, size - 5, 60, "create_stats", "half");
        assertTrue(result.matches().isEmpty());
    }

    @Test
    void limitKeepsTheNewestAndSaysHowManyWereCut() throws Exception {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            lines.add("[main/WARN] [create_stats/]: warning " + i);
        }
        SupportLogTail.Result result = SupportLogTail.read(write(lines), 1 << 20, 3, "create_stats");
        assertEquals(List.of("[main/WARN] [create_stats/]: warning 7", "[main/WARN] [create_stats/]: warning 8",
            "[main/WARN] [create_stats/]: warning 9"), result.matches());
        assertEquals(List.of("... 7 earlier entries omitted ..."), result.notes());
    }

    @Test
    void crlfLogsSplitCleanly() throws Exception {
        Path log = this.dir.resolve("crlf.log");
        Files.writeString(log, "[main/WARN] [create_stats/]: a\r\n[main/WARN] [create_stats/]: b\r\n");
        assertEquals(List.of("[main/WARN] [create_stats/]: a", "[main/WARN] [create_stats/]: b"),
            SupportLogTail.read(log, 1 << 20, 60, "create_stats").matches());
    }
}
