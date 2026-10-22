package com.createstats.flight.bridge;

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

class SableContainersGuardTest {
    private static final Path MAIN = Path.of("src/main/java");
    private static final Path HELPER = MAIN.resolve("com/createstats/flight/bridge/SableContainers.java");
    private static final Path WAKE = MAIN.resolve("com/createstats/flight/bridge/SableWake.java");

    private static final Pattern CLASS_NAME = Pattern.compile("\"dev\\.ryanhcode\\.sable\\.api\\.sublevel\\.(Server)?SubLevelContainer\"");

    private static final Pattern MEMBER_NAME = Pattern.compile(
            "\"(getContainer|getAllSubLevels|getAllTickets|addForceLoadTicket|removeForceLoadTicket|getHoldingChunkMap|physicsSystem)\"");

    private static final Pattern SEARCH = Pattern.compile(
            "\\.(getMethods|getMethod|getDeclaredMethods|getDeclaredMethod)\\(|ReflectionAccessors\\.(method|findMethodAccepting)\\(");

    @Test
    void onlyTheHelperNamesTheContainer() throws IOException {
        List<String> problems = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> s = Files.walk(MAIN)) {
            files = s.filter(p -> p.toString().endsWith(".java")).toList();
        }
        for (Path p : files) {
            if (p.equals(HELPER) || p.equals(WAKE)) continue;
            String src = code(p);
            Matcher c = CLASS_NAME.matcher(src);
            if (c.find()) problems.add(MAIN.relativize(p) + ": names " + c.group() + "; reach it through SableContainers");
            Matcher m = MEMBER_NAME.matcher(src);
            while (m.find()) problems.add(MAIN.relativize(p) + ": looks up the container's " + m.group() + " itself");
        }
        assertTrue(problems.isEmpty(), "Sable's container searched outside SableContainers:\n  " + String.join("\n  ", problems));
    }

    @Test
    void theHelperOnlyTakesHandles() throws IOException {
        String src = code(HELPER);
        List<String> problems = new ArrayList<>();
        Matcher m = SEARCH.matcher(src);
        while (m.find()) problems.add("searches a class: " + m.group());
        for (String member : List.of(
                "ForeignHandles.findStatic(container, \"getContainer\", container, Level.class)",
                "ForeignHandles.findVirtual(container, \"getAllSubLevels\", List.class)",
                "ForeignHandles.findVirtual(container, \"getSubLevel\", subLevel, UUID.class)",
                "ForeignHandles.findVirtual(container, \"inBounds\", boolean.class, BlockPos.class)",
                "ForeignHandles.findVirtual(container, \"getPlot\", plot, ChunkPos.class)",
                "ForeignHandles.findVirtual(serverContainer, \"getAllTickets\", Map.class)",
                "ticket(serverContainer, \"addForceLoadTicket\", serverSubLevel, ticketType)",
                "ticket(serverContainer, \"removeForceLoadTicket\", serverSubLevel, ticketType)",
                "ForeignHandles.findVirtual(serverContainer, name, boolean.class, serverSubLevel, ticketType, Object.class)",
                "ForeignHandles.findVirtual(serverContainer, \"getHoldingChunkMap\", holdingMap)")) {
            if (!src.contains(member)) problems.add("missing its exact handle: " + member);
        }
        assertTrue(problems.isEmpty(), "SableContainers:\n  " + String.join("\n  ", problems));
    }

    private static String code(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }
}
