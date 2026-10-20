package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorTierTest {
    private static final int BASE = 8_192;

    @Test
    @DisplayName("the ladder doubles: wire 1, 2, 4, 8 and connector 2, 4, 8")
    void theLadderDoubles() {
        final WireTier[] wires = WireTier.values();
        assertEquals(4, wires.length, "four wire grades");
        for (int i = 1; i < wires.length; i++) {
            assertEquals(wires[i - 1].multiplier() * 2, wires[i].multiplier(),
                    wires[i] + " is not twice " + wires[i - 1]);
        }
        final ConnectorTier[] connectors = ConnectorTier.values();
        assertEquals(3, connectors.length, "three connector grades");
        for (int i = 1; i < connectors.length; i++) {
            assertEquals(connectors[i - 1].multiplier() * 2, connectors[i].multiplier(),
                    connectors[i] + " is not twice " + connectors[i - 1]);
        }
    }

    @Test
    @DisplayName("every connector equals its tier's wire, and every cable is half its tier's wire")
    void connectorsMatchWiresAndCablesAreHalf() {
        assertEquals(WireTier.INSULATED.multiplier(), ConnectorTier.POWER.multiplier());
        assertEquals(WireTier.SHIELDED.multiplier(), ConnectorTier.ADVANCED.multiplier());
        assertEquals(WireTier.HV.multiplier(), ConnectorTier.INDUSTRIAL.multiplier());

        final CableTier[] cables = CableTier.values();
        final WireTier[] wires = WireTier.values();
        assertEquals(cables.length, wires.length, "one cable grade per wire grade");
        for (int i = 0; i < cables.length; i++) {
            assertEquals(BASE * wires[i].multiplier() / 2, cables[i].rate(BASE),
                    cables[i] + " is not half of " + wires[i]);
        }
    }

    @Test
    @DisplayName("one HV Line fills one Industrial Connector exactly")
    void oneHvLineFillsAnIndustrialConnector() {
        assertEquals(65_536, FeRates.connectorThroughput(BASE, ConnectorTier.INDUSTRIAL,
                List.of(WireTier.HV)));
        assertEquals(65_536, BASE * ConnectorTier.INDUSTRIAL.multiplier(),
                "the Industrial rating itself is 8x, the Starter Capacitor's peak burst");
    }

    @Test
    @DisplayName("three Shielded Wires into an Industrial Connector carry 65,536, no longer 98,304")
    void threeShieldedSpansAreCappedAtTheNewRating() {
        final List<WireTier> three = List.of(WireTier.SHIELDED, WireTier.SHIELDED, WireTier.SHIELDED);
        final int rate = FeRates.connectorThroughput(BASE, ConnectorTier.INDUSTRIAL, three);
        assertEquals(65_536, rate);
        assertTrue(rate < 98_304, "the 12x figure must not come back: " + rate);

        assertEquals(65_536, FeRates.connectorThroughput(BASE, ConnectorTier.INDUSTRIAL,
                List.of(WireTier.SHIELDED, WireTier.SHIELDED)));
    }

    @Test
    @DisplayName("spans add below the ceiling, and the ceiling is the connector's own grade")
    void spansAddUpToTheCeiling() {
        assertEquals(BASE, FeRates.connectorThroughput(BASE, ConnectorTier.INDUSTRIAL,
                List.of(WireTier.COPPER)));
        assertEquals(BASE * 3, FeRates.connectorThroughput(BASE, ConnectorTier.INDUSTRIAL,
                List.of(WireTier.COPPER, WireTier.INSULATED)));

        assertEquals(BASE * 2, FeRates.connectorThroughput(BASE, ConnectorTier.POWER,
                List.of(WireTier.HV)));
    }

    @Test
    @DisplayName("a bare connector is rated at its own grade, there being no wire to cap it")
    void aBareConnectorIsItsOwnGrade() {
        assertEquals(BASE * 8, FeRates.connectorThroughput(BASE, ConnectorTier.INDUSTRIAL, List.of()));
        assertEquals(BASE * 2, FeRates.connectorThroughput(BASE, ConnectorTier.POWER, List.of()));
    }

    @Test
    @DisplayName("an unknown ordinal decodes to the grade every existing world already has")
    void decodeIsSafe() {
        assertEquals(ConnectorTier.POWER, ConnectorTier.byOrdinal(-1));
        assertEquals(ConnectorTier.POWER, ConnectorTier.byOrdinal(99));
        assertEquals(ConnectorTier.INDUSTRIAL, ConnectorTier.byOrdinal(ConnectorTier.INDUSTRIAL.ordinal()));
        assertEquals(WireTier.HV, WireTier.byOrdinal(WireTier.HV.ordinal()));
        assertEquals(WireTier.COPPER, WireTier.byOrdinal(WireTier.values().length));
    }
}
