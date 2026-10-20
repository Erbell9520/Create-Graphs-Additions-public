package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeSeamConductorGuardTest {
    private static final Path GRID =
            Path.of("src/main/java/com/createstats/power/FeWireNetwork.java");

    private static String giveToExternal() throws IOException {
        final String src = Files.readString(GRID, StandardCharsets.UTF_8);
        final int from = src.indexOf("public int giveToExternal(");
        assertTrue(from > 0, "giveToExternal is gone or has been renamed");
        final int end = src.indexOf("\n    /**", from);
        assertTrue(end > from, "cannot find the end of giveToExternal");
        return src.substring(from, end);
    }

    @Test
    @DisplayName("the seam clamps what it gives to the conductors between it and the power")
    void theSeamAsksTheConductors() throws IOException {
        assertTrue(giveToExternal().contains("conductorAllowance(requesterKey)"),
                "a foreign pull is limited by the circuit throat alone, which branching turns off");
    }

    @Test
    @DisplayName("and books what it gave against them")
    void theSeamBooksWhatItGave() throws IOException {
        final String body = giveToExternal();
        assertTrue(body.contains("bookConductorDelivery(requesterKey, given)"),
                "an allowance that is read and never spent is not an allowance");

        final int simulate = body.lastIndexOf("if (!simulate) {");
        assertTrue(simulate > 0, "the commit branch is gone");
        final int closes = body.indexOf("\n            }", simulate);
        assertTrue(closes > simulate, "cannot find the end of the commit branch");
        assertTrue(body.substring(simulate, closes).contains("bookConductorDelivery(requesterKey, given)"),
                "the booking must sit inside the commit branch, or a simulate spends the allowance");
    }
}
