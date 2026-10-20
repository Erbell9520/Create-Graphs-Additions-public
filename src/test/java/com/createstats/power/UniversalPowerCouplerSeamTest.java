package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalPowerCouplerSeamTest {
    private static final Path CAPS =
            Path.of("src/main/java/com/createstats/power/FeCapabilities.java");

    private static String couplerRegistration() throws IOException {
        final String src = Files.readString(CAPS, StandardCharsets.UTF_8);
        final int at = src.indexOf("ModBlockEntities.UNIVERSAL_POWER_COUPLER.get()");
        assertTrue(at > 0, "the Coupler has no energy registration");
        final int from = src.lastIndexOf("event.registerBlockEntity", at);
        final int end = src.indexOf("});", at);
        assertTrue(end > from, "cannot find the end of the Coupler registration");
        return src.substring(from, end + 3);
    }

    @Test
    @DisplayName("the foreign face is served without a guard in front of it")
    void foreignFaceIsExempt() throws IOException {
        final String reg = couplerRegistration();
        final int seam = reg.indexOf("seamHandle()");
        final int foreign = reg.indexOf("foreignFace()");
        assertTrue(foreign > 0 && seam > 0, "the foreign face must return seamHandle()");
        final String beforeSeam = reg.substring(0, seam);
        assertTrue(!beforeSeam.contains("refusesSide") && !beforeSeam.contains("guard("),
                "a guard runs before the foreign face is served, so the seam can never conduct");
    }

    @Test
    @DisplayName("the grid face is behind the New Age guard")
    void gridFaceIsGuarded() throws IOException {
        final String reg = couplerRegistration();
        final int guard = reg.indexOf("refusesSide");
        final int grid = reg.indexOf("gridHandle()");
        assertTrue(guard > 0, "the grid face is not guarded: a New Age connector there destroys FE");
        assertTrue(grid > guard, "gridHandle() is returned before the guard runs");
    }

    @Test
    @DisplayName("a null level is never a seam")
    void nullLevelIsNotASeam() {
        assertFalse(NewAgeFeed.isForeignSeam(null, BlockPos.ZERO, Direction.NORTH));
    }

    @Test
    @DisplayName("isForeignSeam replaces isCouplerSeam and checks the generic FE capability")
    void isForeignSeamIsBroadenedAndUngated() throws IOException {
        final String src = Files.readString(
                Path.of("src/main/java/com/createstats/power/NewAgeFeed.java"), StandardCharsets.UTF_8);
        assertTrue(src.contains("public static boolean isForeignSeam("),
                "isForeignSeam is missing");
        assertFalse(src.contains("isCouplerSeam"),
                "isCouplerSeam still present -- rename it, do not leave a second predicate");
        final int from = src.indexOf("public static boolean isForeignSeam(");
        final int to = src.indexOf("\n    }", from);
        final String body = src.substring(from, to);
        assertTrue(body.contains("isFeedOn"), "isForeignSeam lost the New Age connector check");
        assertTrue(body.contains("Capabilities.EnergyStorage.BLOCK"),
                "isForeignSeam has no generic FE capability check, so it only serves New Age");

        final int available = body.indexOf("NewAgeCompat.isAvailable()");
        final int capability = body.indexOf("getCapability");
        assertTrue(available >= 0 && capability > available,
                "the generic capability check must be outside the New Age availability guard");
    }

    @Test
    @DisplayName("the PDU prices a Coupler once: surveyMember returns, ownGenerationOf has no branch")
    void thePduPricesACouplerExactlyOnce() throws IOException {
        final String pdu = Files.readString(
                Path.of("src/main/java/com/createstats/blockentity/PowerDistributionUnitBE.java"),
                StandardCharsets.UTF_8);

        final int member = pdu.indexOf("private void surveyMember(");
        assertTrue(member > 0, "surveyMember is gone");
        final int couplerIf = pdu.indexOf("instanceof com.createstats.blockentity.UniversalPowerCouplerBE coupler", member);
        assertTrue(couplerIf > member, "surveyMember no longer special-cases a Coupler");
        final int nextBrace = pdu.indexOf("\n        }", couplerIf);
        assertTrue(pdu.substring(couplerIf, nextBrace).contains("return;"),
                "surveyMember does not return after pricing a Coupler -- it is double-counted");

        final int own = pdu.indexOf("private static int ownGenerationOf(");
        assertTrue(own > 0, "ownGenerationOf is gone");
        final int ownEnd = pdu.indexOf("\n    }", own);
        assertFalse(pdu.substring(own, ownEnd).contains("UniversalPowerCouplerBE"),
                "ownGenerationOf still prices a Coupler; surveyCouplerSeam is the single source now");

        final String feed = Files.readString(
                Path.of("src/main/java/com/createstats/power/NewAgeFeed.java"), StandardCharsets.UTF_8);
        final int seam = feed.indexOf("public static boolean surveyCouplerSeam(");
        final int seamEnd = feed.indexOf("\n    }", seam);
        final String body = feed.substring(seam, seamEnd);
        assertTrue(body.contains("Math.min(cap"),
                "surveyCouplerSeam does not cap the priced generation at the Coupler's rating");
        assertTrue(body.contains("foldSourceFingerprint"),
                "surveyCouplerSeam does not fold the foreign generator set into the fingerprint");
        assertFalse(feed.contains("surveyConnector"),
                "the old per-generator surveyConnector is still there -- it is the double count");
    }
}
