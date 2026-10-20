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

class FeForecastWiringTest {
    private static final Path SOURCES = Path.of("src/main/java/com/createstats");
    private static final Path PDU = SOURCES.resolve("blockentity/PowerDistributionUnitBE.java");
    private static final Path[] BOXES = {
            SOURCES.resolve("blockentity/PowerMeterBE.java"),
            SOURCES.resolve("blockentity/PowerLimiterBE.java"),
            SOURCES.resolve("blockentity/MainBreakerBE.java")};

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

    private static String declaration(String code) {
        final Matcher m = Pattern.compile("public class \\w+[^{]*\\{").matcher(code);
        assertTrue(m.find(), "no class declaration");
        return m.group();
    }

    @Test
    @DisplayName("KI-63: a meter, a limiter and a breaker each relay what feeds them")
    void theBoxesRelay() throws IOException {
        for (Path box : BOXES) {
            final String code = code(box);
            final String name = box.getFileName().toString();
            assertTrue(declaration(code).contains("FeForecastingSource"),
                    name + " is no longer a forecasting source: a roof behind it is priced and never forecast");
            final String in = method(code, "public int potentialFePerTickIn");
            assertTrue(in.contains("FeForecastRelay.through("), name + " no longer relays the drop through its price");
            assertTrue(in.contains("forecastBehind()"), name + " no longer asks what feeds it");
            assertTrue(method(code, "private com.createstats.power.FeForecastRelay.Upstream forecastBehind")
                    .contains("FeForecastWalk.behind("), name + " no longer walks its input side");
            assertTrue(method(code, "public int relayedWeatherShareFePerTick")
                    .contains("FeForecastRelay.weatherShareThrough("), name + " no longer relays the roof's share");
            assertTrue(method(code, "public boolean relays").contains("return true"),
                    name + " stopped saying it relays: a board's leg pricing would ask it what it makes");
        }
    }

    @Test
    @DisplayName("KI-88: a board's back boss relays its own supply's drop to the board below")
    void theBackBossRelays() throws IOException {
        final String code = code(PDU);
        assertTrue(declaration(code).contains("FeForecastingSource"), "the PDU is no longer a forecasting source");
        assertTrue(method(code, "public int potentialFePerTickIn").contains("FeForecastRelay.export("),
                "the back boss no longer relays the drop to its export");
        assertTrue(method(code, "public boolean relays").contains("return true"),
                "the PDU stopped saying it relays");
    }

    @Test
    @DisplayName("KI-63: the survey carries a relay's roof share, and the legs are forecast too")
    void theSurveyTakesRelaysAndLegs() throws IOException {
        final String code = code(PDU);
        assertTrue(method(code, "private static void noteForecast").contains(".relayedWeatherShareFePerTick()"),
                "noteForecast drops a relay's roof share: rain no longer discounts a roof behind a meter");
        final String read = method(code, "private void readUpstream");
        assertTrue(read.contains("surveyLegForecasts()"), "the legs' forecasting sources are no longer surveyed");
        assertTrue(Pattern.compile("supplyMemory\\.observe\\([^;]*learnedGeneration").matcher(read).find(),
                "the day model no longer learns the forecast leg sources");
        final String serve = method(code, "private FeGridSolver.BranchResult serveActiveAuto");
        assertTrue(Pattern.compile("supplyDrop\\s*=\\s*expectedSupplyDropFePerTick\\(\\)").matcher(serve).find(),
                "the admit budget no longer gives up the legs' drop");
    }

    @Test
    @DisplayName("KI-64: each peer is sent its own share of the night, never the board's whole figure")
    void peersGetTheirShare() throws IOException {
        final String talk = method(code(PDU), "private void talkToBranchBoards");
        assertTrue(talk.contains("peerHoldShares"), "talkToBranchBoards no longer reads the split");
        assertFalse(Pattern.compile("acceptHoldTarget\\(\\s*(?:worldPosition\\.asLong\\(\\)\\s*,\\s*)?"
                + "outlook\\.shortfallFe\\(\\)").matcher(talk).find(),
                "every peer is sent the whole shortfall again (KI-64)");

        assertTrue(Pattern.compile("acceptHoldTarget\\(\\s*worldPosition\\.asLong\\(\\)\\s*,\\s*share\\s*,")
                .matcher(talk).find(), "the peer's share, named by this board, is no longer what is sent");
        assertTrue(method(code(PDU), "private void splitNightHold").contains("FeSupplyOutlook.splitHold("),
                "the split is no longer the pinned one");
    }

    @Test
    @DisplayName("KI-87: a board's own bare banks hold its own share of the night")
    void ownBanksHoldTheirShare() throws IOException {
        final String own = method(code(PDU), "private com.createstats.power.FeBoardDeal.Answer ownBankAnswer");
        assertTrue(own.contains("ownBankBand("), "a board's own banks are assessed without its own hold");

        assertFalse(Pattern.compile("Band\\.DEFAULT\\s*,").matcher(own).find(),
                "a board's own banks are back on the fixed default band");
    }

    @Test
    @DisplayName("KI-62: a board reads the Weather Panel's countdown run down and aged, never raw")
    void theCountdownIsAged() throws IOException {
        final String weather = method(code(PDU), "private com.createstats.power.FeSupplyOutlook.Weather currentWeather");
        assertTrue(weather.contains(".ticksToNextChangeFor("), "the board no longer ages the countdown");
        assertFalse(weather.contains(".ticksToNextChange()"), "the board reads a dark panel's frozen countdown again");
    }
}
