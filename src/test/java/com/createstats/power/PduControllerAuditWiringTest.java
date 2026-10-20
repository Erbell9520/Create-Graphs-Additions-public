package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduControllerAuditWiringTest {
    private static final Path SOURCES = Path.of("src/main/java/com/createstats");
    private static final Path PDU = SOURCES.resolve("blockentity/PowerDistributionUnitBE.java");
    private static final Path CONTROLLER = SOURCES.resolve("blockentity/BatteryControllerBE.java");
    private static final Path PROJECTION = SOURCES.resolve("power/ReserveProjection.java");

    private static String code(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static String method(String code, String declaration) {
        final Matcher m = Pattern.compile(Pattern.quote(declaration) + "\\s*\\([^)]*\\)\\s*\\{").matcher(code);
        assertTrue(m.find(), "no method " + declaration);
        int depth = 0;
        final int open = m.end() - 1;
        for (int i = open; i < code.length(); i++) {
            final char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return code.substring(open + 1, i);
            }
        }
        throw new IllegalStateException("unbalanced braces");
    }

    private static String statement(String body, String start) {
        final Matcher m = Pattern.compile(start).matcher(body);
        assertTrue(m.find(), "no statement starting " + start);
        final int end = body.indexOf(';', m.start());
        assertTrue(end > 0, "unterminated statement " + start);
        return body.substring(m.start(), end).replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("KI-185: an unlinked board budgets with PduOwnBudget, admit included")
    void theBoardBudgetsOnItsOwnBufferThroughThePureRule() throws IOException {
        final String serve = method(code(PDU), "private FeGridSolver.BranchResult serveActiveAuto");
        assertTrue(statement(serve, "final com\\.createstats\\.power\\.PduOwnBudget\\.Budgets ownBudget\\s*=")
                .contains("PduOwnBudget.of("), "serveActiveAuto no longer works its own budgets out in PduOwnBudget");
        final String admit = statement(serve, "final int admitBudget\\s*=");
        assertTrue(admit.endsWith(": ownBudget.admit()"), "the unlinked admit budget is not PduOwnBudget's: " + admit);
        assertFalse(admit.contains("ceiling - standing"), "the whole wire is back in the admit budget: " + admit);
        assertTrue(statement(serve, "final int runBudget\\s*=").endsWith(": ownBudget.run()"),
                "the unlinked run budget is not PduOwnBudget's");
        assertTrue(statement(serve, "final int criticalBudget\\s*=").endsWith(": ownBudget.critical()"),
                "the unlinked Critical budget is not PduOwnBudget's");
        assertTrue(statement(serve, "final long restoreSupply\\s*=").endsWith(": ownBudget.restoreSupply()"),
                "the unlinked restore margin is no longer measured against PduOwnBudget's supply");
    }

    @Test
    @DisplayName("KI-187: the controller answers its boards in their outputs' tiers")
    void theControllerAnswersInTheOrderItFeeds() throws IOException {
        final String answer = method(code(CONTROLLER), "private void answerBoards");
        final String split = statement(answer, "final List<com\\.createstats\\.power\\.FeBoardDeal\\.Answer> answers\\s*=");
        assertTrue(split.contains("requests, caps, held, tiers, now + BOARD_ANSWER_GRACE_TICKS)"),
                "answerBoards no longer hands the split each board's tier: " + split);
        assertTrue(statement(answer, "tiers\\[j\\]\\s*=\\s*Math\\.min\\(").contains("plan.get(i).priority().ordinal()"),
                "a board's tier is no longer its output's priority");
        final String served = method(code(PROJECTION), "static int[] boardServed");
        assertTrue(statement(served, "final List<FeBoardDeal\\.Answer> answers\\s*=")
                        .contains("requests, caps, heldOut, tiers, 1L)"),
                "the controller's forecast no longer answers boards in tier order, as the controller does");
    }
}
