package com.createstats.surveyor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class UnseenWiringTest {
    private static final Path ROOT = Path.of("src/main/java/com/createstats");
    private static String source(String file) throws Exception { return Files.readString(ROOT.resolve(file)).replace("\r\n", "\n"); }

    private static boolean statement(String source, String before, String read) {
        int at = source.indexOf(read);
        if (at < 0) return false;
        int end = source.indexOf(';', at);
        String expected = before + read;
        int start = source.lastIndexOf(before, at);
        return start >= 0 && source.substring(start, end + 1).startsWith(expected);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) return "";
        start = source.indexOf('{', start);
        int depth = 1;
        for (int end = start + 1; end < source.length(); end++) {
            if (source.charAt(end) == '{') depth++;
            if (source.charAt(end) == '}' && --depth == 0) return source.substring(start + 1, end);
        }
        return "";
    }

    private static String firstStatement(String source, String prefix) {
        int start = source.indexOf(prefix);
        int end = start < 0 ? -1 : source.indexOf(';', start);
        return end < 0 ? "" : source.substring(start, end + 1);
    }

    private static void mutation(String real, String guardedLine, Predicate<String> check) {
        assertTrue(check.test(real), "real guard is missing");
        assertTrue(real.contains(guardedLine), "mutation anchor is missing");
        assertFalse(check.test(real.replace(guardedLine, "")), "removing the guard still passes");

        assertFalse(check.test(real.replace(guardedLine, "") + "\n" + guardedLine + "\nlevel.getBlockState(other);"),
                "adjacent evidence falsely passes");
    }

    @Test void sideColumnAndEveryCachedDockReadAreGuardedAndMutationsFail() throws Exception {
        String sensors = source("vccmk4/route/RouteSensors.java");
        String guard = "                    if (!level.isLoaded(pos)) continue;\n";
        mutation(sensors, guard, s -> statement(s, guard, "                    BlockState state = level.getBlockState(pos);"));
        String dock = source("vccmk4/route/DockApproach.java");
        for (String expression : new String[]{
                "ours == null || !level.isLoaded(ours) ? null : level.getBlockEntity(ours)",
                "level.isLoaded(ours) ? level.getBlockEntity(ours) : null",
                "level.isLoaded(bay) ? level.getBlockEntity(bay) : null",
                "bay == null || !level.isLoaded(bay) ? null : level.getBlockEntity(bay)"}) {
            assertTrue(dock.contains(expression));
            String guardPart = expression.contains("||") ? "|| !level.isLoaded(" + (expression.startsWith("ours") ? "ours" : "bay") + ") "
                    : "level.isLoaded(" + (expression.contains("ours") ? "ours" : "bay") + ") ? ";
            assertFalse(dock.replace(guardPart, "").contains(expression));
        }
        String neighbour = "            if (!level.isLoaded(neighbour)) continue;\n";
        mutation(dock, neighbour, s -> statement(s, neighbour, "            BlockEntity be = level.getBlockEntity(neighbour);"));
    }

    @Test void fullStateIsWrittenBeforeSableRestoresAndTheOldScalarHandoffIsGone() throws Exception {
        String unseen = source("surveyor/UnseenRoutes.java");
        String begin = "        console.put(\"Route\", returned);\n        setDirty();\n";
        mutation(unseen, begin, s -> statement(s, begin, "        String result = RouteParking.restore("));
        assertTrue(unseen.contains("v.ghostRoute = v.ghost.save();"));
        assertTrue(unseen.contains("c.put(\"GhostRoute\", v.ghost == null ? v.ghostRoute.copy() : v.ghost.save());"));
        assertFalse(unseen.contains("takeHandBack"));
        assertFalse(source("vccmk4/route/Vcc4Route.java").contains("takeHandBack"));
    }

    @Test void frozenLevelsAndSyntheticLearningAreBoundedAtTheirEntryPoints() throws Exception {
        String unseen = source("surveyor/UnseenRoutes.java");
        String freeze = " || !level.tickRateManager().runsNormally()";
        mutation(unseen, freeze, s -> s.contains("if (!(event.getLevel() instanceof ServerLevel level)" + freeze + ") return;"));
        String runner = source("surveyor/core/RouteRunner.java");
        mutation(runner, "if (measured) ", s -> s.contains("if (measured) learn(in);"));
        String route = source("vccmk4/route/Vcc4Route.java");
        assertTrue(route.contains("command = host.present() ? runner.tick(s) : runner.tickUnseen(s);"));
        assertTrue(route.contains("if (host.present()) dock.tick("));
        assertTrue(route.contains("if (!host.present()) return;\n        if (sableHeld"));
        assertTrue(route.contains("if (host.present() && driving && !held"));
    }

    @Test void incompleteRestorationStopsUnseenAuthorityAndPausesTheMatchingLoadedConsole() throws Exception {
        String unseen = source("surveyor/UnseenRoutes.java");
        String gate = "            if (!intact) {\n";
        mutation(unseen, gate, s -> {
            int begin = s.indexOf(gate);
            int end = begin < 0 ? -1 : s.indexOf("            }", begin);
            return end >= 0 && s.substring(begin, end).contains("failedReturn(level, v, at, now,")
                    && s.substring(begin, end).contains("return;");
        });
        String stop = "        v.ghost.route().pause(level, why);\n";
        mutation(unseen, stop, s -> statement(methodBody(s, "void failedReturn("), stop, "        v.ghostRoute = v.ghost.save();"));
        String parking = source("vccmk4/route/RouteParking.java");
        mutation(parking, "getChunk(cx, cz, false)", s -> firstStatement(methodBody(s, "void pauseLoadedConsole("), "var chunk = ")
                .equals("var chunk = level.getChunkSource().getChunk(cx, cz, false);"));
        mutation(parking, " && be.consoleId().equals(console)",
                s -> firstStatement(methodBody(s, "void pauseLoadedConsole("), "if (chunk.getBlockEntity(pos)")
                        .startsWith("if (chunk.getBlockEntity(pos) instanceof com.createstats.vccmk4.Vcc4BE be && be.consoleId().equals(console)) {"));
    }
}
