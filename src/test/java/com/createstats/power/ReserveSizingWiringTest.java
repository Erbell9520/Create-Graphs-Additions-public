package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReserveSizingWiringTest {
    private static final Path SOURCES = Path.of("src/main/java/com/createstats");
    private static final Path CONTROLLER = SOURCES.resolve("blockentity/BatteryControllerBE.java");
    private static final Path PDU = SOURCES.resolve("blockentity/PowerDistributionUnitBE.java");
    private static final Path PROJECTION = SOURCES.resolve("power/ReserveProjection.java");
    private static final Path SPECIMEN = SOURCES.resolve("book/specimen/BatteryPlates.java");

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

    private static int count(String code, String regex) {
        final Matcher m = Pattern.compile(regex).matcher(code);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    @Test
    @DisplayName("KI-198: the controller's stance, droop and floor meter read the sized band and the want")
    void theControllerAssessesOnTheSizedBand() throws IOException {
        final String tick = method(code(CONTROLLER), "public void tick");
        final String assess = statement(tick, "reserveState = com\\.createstats\\.power\\.FeReserveBand\\.assess\\(");
        assertTrue(assess.contains("effectiveBand()"), "the band is no longer the sized one with its hold: " + assess);
        assertTrue(assess.endsWith("reserveState.stance(), criticalWant))"),
                "the assessment no longer states the Critical want that meters the floor: " + assess);
        final int want = tick.indexOf("bandWant.update(criticalWant, now)");
        assertTrue(want >= 0, "the band's want is no longer updated from the tick's Critical want");
        assertTrue(want < tick.indexOf("FeReserveBand.assess("), "the want is updated after the band was assessed");
    }

    @Test
    @DisplayName("KI-198: the refill claim and the forecast's claim are judged on the sized band, never the hold")
    void theClaimIsJudgedOnTheSizedBand() throws IOException {
        final String code = code(CONTROLLER);
        final String claim = statement(method(code, "public void tick"), "chargeClaim = Math\\.min\\(claimable,");
        assertTrue(claim.contains(", sizedBand(), "), "the refill claim is not judged on the sized band: " + claim);
        final String outlook = statement(code, "outlook = com\\.createstats\\.power\\.ReserveProjection\\.project\\(");
        assertTrue(outlook.contains("supplyForecast(), sizedBand(),"),
                "the forecast's claim band is not the sized one: " + outlook);
    }

    @Test
    @DisplayName("KI-198: the band is sized from the controller's own settings, and only they read the server's")
    void nothingElseReadsThePlainPercents() throws IOException {
        final String code = code(CONTROLLER);

        assertEquals(2, count(code, "(?<![A-Za-z.])band\\(\\)(?!\\s*\\{)"),
                "band() is read somewhere other than bandSettings()");
        assertEquals(2, count(method(code, "public com.createstats.power.FeBandSettings bandSettings"),
                "(?<![A-Za-z.])band\\(\\)"), "bandSettings() no longer falls back to the server's band");
        assertTrue(method(code, "private com.createstats.power.FeReserveBand.Band sizedBand")
                .contains("bandSettings().band(reserveCapacity, bandWant.want(), reserveDischargeRate(), rideTicks())"),
                "sizedBand() does not size from the controller's settings");
        final String effective = method(code, "private com.createstats.power.FeReserveBand.Band effectiveBand");
        assertTrue(effective.contains("final com.createstats.power.FeReserveBand.Band sized = sizedBand();"),
                "the hold no longer raises the sized band's floor");
        assertTrue(statement(code, "tag\\.putInt\\(\"CfgFloor\",").contains("sizedBand().floorPct()"),
                "the screen's floor before the hold is not the sized one");
    }

    @Test
    @DisplayName("KI-191, KI-198: a board's own and leg banks are judged on the configured band, sized, with the want")
    void aBoardsBanksAreSizedOnTheBoardsCritical() throws IOException {
        final String code = code(PDU);
        final String sized = method(code, "private com.createstats.power.FeReserveBand.Band sizedBankBand");
        assertTrue(sized.contains("FeReserveBand.sized(BatteryControllerBE.band(), BatteryControllerBE.bandMinutes(),"),
                "a board's banks are not sized on the controller's configured band and minutes");
        assertTrue(method(code, "private com.createstats.power.FeReserveBand.Band ownBankBand")
                .contains("= sizedBankBand(capacity, fullRate);"), "the night hold no longer raises the sized floor");

        final String own = method(code, "private com.createstats.power.FeBoardDeal.Answer ownBankAnswer");
        final String ownAssess = statement(own, "final com\\.createstats\\.power\\.FeReserveReport own =");
        assertTrue(ownAssess.contains("ownBankBand(capacity, fullRate), BatteryControllerBE.rideTicks(), ownBankStance,"
                + " bankWant.want())"), "the board's own banks are off the sized band, the ride or the want: " + ownAssess);
        final String legs = method(code, "private com.createstats.power.FeBoardDeal.Answer legBankAnswer");
        final String legAssess = statement(legs, "final com\\.createstats\\.power\\.FeReserveReport legs =");
        assertTrue(legAssess.contains("sizedBankBand(branchTankCapacity, fullRate), BatteryControllerBE.rideTicks(),"
                + " legBankStance, bankWant.want())"), "the leg banks are off the sized band, the ride or the want: "
                + legAssess);
        for (String body : new String[]{own, legs}) {
            assertFalse(body.contains("Band.DEFAULT"), "a board's bank is judged on the default band again");
            assertFalse(body.contains("DEFAULT_RIDE_TICKS"), "a board's bank rides the default horizon again (KI-191)");
        }

        final String serve = method(code, "private FeGridSolver.BranchResult serveActiveAuto");
        final int update = serve.indexOf("bankWant.update(currentRequest.critical(), now)");
        assertTrue(update >= 0, "the banks' want is no longer the board's requested Critical");
        assertTrue(update < serve.indexOf("legBankAnswer(") && update < serve.indexOf("ownBankAnswer("),
                "the banks' want is updated after an answer used it");
    }

    @Test
    @DisplayName("KI-199: the forecast meters the floor with the same want the tick does")
    void theForecastStatesTheWant() throws IOException {
        final String assess = statement(code(PROJECTION), "FeReserveReport report = FeReserveBand\\.assess\\(");
        assertTrue(assess.endsWith("s.band(), s.rideTicks(), stance, critical))"),
                "the forecast's assessment no longer states the Critical want: " + assess);
    }

    @Test
    @DisplayName("KI-198: the manual's controller specimen runs the sized band")
    void theManualsSpecimenIsSized() throws IOException {
        final String example = method(code(SPECIMEN), "private static BatteryView example");
        assertTrue(statement(example, "final FeReserveBand\\.Band band =").contains("FeReserveBand.sized("),
                "the manual's specimen judges its banks on the plain percents");
        assertTrue(statement(example, "final FeReserveReport report =").endsWith("ride, null, criticalWant))"),
                "the manual's specimen does not meter the floor with its want");
    }
}
