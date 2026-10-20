package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeBoardDealWiringTest {
    private static final Path SOURCES = Path.of("src/main/java/com/createstats");
    private static final Path PDU = SOURCES.resolve("blockentity/PowerDistributionUnitBE.java");
    private static final Path CONTROLLER = SOURCES.resolve("blockentity/BatteryControllerBE.java");

    private static String code(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static String method(String code, String declaration) {
        final Matcher m = Pattern.compile(declaration + "\\s*\\([^)]*\\)\\s*\\{").matcher(code);
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

    @Test
    @DisplayName("the controller answers every board it feeds, each tick")
    void theControllerAnswers() throws IOException {
        final String code = code(CONTROLLER);
        assertTrue(method(code, "public void tick").contains("answerBoards("),
                "BatteryControllerBE.tick() no longer calls answerBoards: every board is back on the broadcast");
        final String answer = method(code, "private void answerBoards");

        assertTrue(answer.contains(".boardRequest(worldPosition.asLong())"),
                "answerBoards no longer asks each board for this controller's share of its request");
        assertTrue(answer.contains("FeBoardDeal.split("), "answerBoards no longer splits the offer");
        assertTrue(answer.contains(".acceptBoardAnswer("), "answerBoards no longer delivers the answers");

        assertTrue(answer.contains("BatteryControllerPlan.boardCap("),
                "answerBoards no longer caps a board by its output's floor, mode and rate cap");
        assertTrue(Pattern.compile("if\\s*\\(\\s*reserveCapacity\\s*<=\\s*0\\s*\\)\\s*\\{\\s*return;")
                .matcher(answer).find(), "a controller with nothing stored answers its boards again");
    }

    @Test
    @DisplayName("KI-102: the margin a restore clears is measured against the answer's spare")
    void theMarginIsMeasuredAgainstTheSpare() throws IOException {
        final String serve = method(code(PDU), "private FeGridSolver.BranchResult serveActiveAuto");
        assertTrue(Pattern.compile("useMargin\\s*=\\s*PduAutoController\\.restoreMargin\\(").matcher(serve).find(),
                "the burst margin is no longer measured against the supply");
        assertTrue(Pattern.compile("new\\s+PduAutoController\\.Supply\\(\\s*useRun\\s*,\\s*useAdmit\\s*,\\s*useMargin")
                .matcher(serve).find(), "the Supply is no longer handed the measured margin");
        assertTrue(Pattern.compile("restoreSupply\\s*=\\s*dealt\\s*\\?\\s*\\(long\\)\\s*useAdmit\\s*\\+\\s*dealSpare")
                .matcher(serve).find(), "an answered board's supply no longer counts the reserve's spare");
    }

    @Test
    @DisplayName("KI-122: a running device is judged against the answer plus its spare, a restore against the answer")
    void theRunTestCarriesTheSpare() throws IOException {
        final String serve = method(code(PDU), "private FeGridSolver.BranchResult serveActiveAuto");

        final Matcher run = Pattern.compile("final\\s+int\\s+useRun\\s*=([^;]*);").matcher(serve);
        assertTrue(run.find(), "serveActiveAuto no longer builds a run budget");
        final String useRun = run.group(1);
        assertTrue(Pattern.compile("dealt\\s*\\?\\s*com\\.createstats\\.power\\.FeBoardDeal\\.runBudget\\(\\s*dealRun\\s*,")
                .matcher(useRun).find(), "an answered board's run budget is the bare answer again: every device "
                + "switched on sheds a running one for the whole dwell (KI-122)");
        assertTrue(useRun.contains("deal.spare()"), "the run budget no longer carries the answer's spare");
        assertTrue(!useRun.contains("dealSpare"),
                "the run budget carries the forecast-reduced spare: a running device pulsed for weather (PR-04 item 8)");
        final Matcher admit = Pattern.compile("final\\s+int\\s+useAdmit\\s*=([^;]*);").matcher(serve);
        assertTrue(admit.find(), "serveActiveAuto no longer builds an admit budget");
        assertTrue(Pattern.compile("dealt\\s*\\?\\s*Math\\.min\\(\\s*useRun\\s*,\\s*Math\\.max\\(\\s*0\\s*,\\s*dealRun\\s*-\\s*dropUncovered\\s*\\)\\s*\\)")
                .matcher(admit.group(1)).find(), "a restore is no longer judged against the answer alone");
    }

    @Test
    @DisplayName("KI-110, KI-111: the claim leaves Critical its want, and the offer spans every output")
    void theClaimAndTheOfferReadTheSharedRules() throws IOException {
        final String tick = method(code(CONTROLLER), "public void tick");
        assertTrue(tick.contains("FeChargePolicy.claimable(energy, allowanceNow, criticalWant)"),
                "the claim's bound is no longer the bus plus the band's allowance less Critical");
        assertTrue(tick.contains("BatteryControllerPlan.criticalWant(plan)"),
                "the claim's guard no longer counts every board's declared Critical");
        assertTrue(tick.contains("BatteryControllerPlan.wiredCeiling(plan)"),
                "the band is assessed against one terminal again, not every live output");

        final String projection = method(code(SOURCES.resolve("power/ReserveProjection.java")),
                "public static Result project(?=\\s*\\(Setup s, int horizon)");
        assertTrue(projection.contains("FeChargePolicy.claimable("), "the forecast's claim bound drifted from the tick's");
        assertTrue(projection.contains("s.claimBand()"),
                "the forecast claims over the held band again, which the tick never does (KI-117)");
        assertTrue(projection.contains("BatteryControllerPlan.wiredCeiling("),
                "the forecast assesses against one terminal while the tick does not");
    }

    @Test
    @DisplayName("the board budgets on its answer, a controller's or its own")
    void theBoardBudgetsOnItsAnswer() throws IOException {
        final String code = code(PDU);
        final String serve = method(code, "private FeGridSolver.BranchResult serveActiveAuto");
        assertTrue(serve.contains("currentRequest = new"), "serveActiveAuto no longer builds the request");
        assertTrue(serve.contains("controllerAnswer("), "serveActiveAuto no longer reads the controller's answer");
        assertTrue(serve.contains("ownBankAnswer("), "serveActiveAuto no longer answers its own bare banks");
        final Matcher supply = Pattern.compile("new\\s+PduAutoController\\.Supply\\(\\s*useRun\\s*,\\s*useAdmit")
                .matcher(serve);
        assertTrue(supply.find(), "the Supply handed to the controller is no longer the answered budget");
        assertTrue(method(code, "private com.createstats.power.FeBoardDeal.Answer ownBankAnswer")
                .contains("FeReserveBand.assess("), "a board's own banks are no longer assessed by the band");
    }

    @Test
    @DisplayName("a bank on the feed is not counted as generation")
    void feedStorageIsNotGeneration() throws IOException {
        final String tick = method(code(PDU), "public void tick");
        final Matcher m = Pattern.compile("observedInput\\s*=\\s*Math\\.max\\(\\s*measuredInput\\s*-\\s*feedStorageIntake\\(")
                .matcher(tick);
        assertTrue(m.find(), "the observed-input peak counts a feed bank's discharge as generation again (KI-85)");
    }
}
