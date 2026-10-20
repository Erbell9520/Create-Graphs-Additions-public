package com.createstats.powertrack.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeederLampTest {
    @Test
    @DisplayName("a supplier with charge is green, as the section it energises is live")
    void supplierWithChargeIsGreen() {
        assertEquals(FeederLamp.GREEN, FeederLamp.of(SectionExchange.Role.SUPPLY, 1, 0));
        assertEquals(FeederLamp.GREEN, FeederLamp.of(SectionExchange.Role.SUPPLY, 4096, 4096));
    }

    @Test
    @DisplayName("a supplier with a dry buffer is dark: its section is not live")
    void drySupplierIsDark() {
        assertEquals(FeederLamp.DARK, FeederLamp.of(SectionExchange.Role.SUPPLY, 0, 4096));
        assertEquals(FeederLamp.DARK, FeederLamp.of(SectionExchange.Role.SUPPLY, -5, 4096));
    }

    @Test
    @DisplayName("a collector with room is amber, as the section it collects from is collecting")
    void collectorWithRoomIsAmber() {
        assertEquals(FeederLamp.AMBER, FeederLamp.of(SectionExchange.Role.COLLECT, 0, 1));
        assertEquals(FeederLamp.AMBER, FeederLamp.of(SectionExchange.Role.COLLECT, 4096, 4096));
    }

    @Test
    @DisplayName("a full collector is dark: it can take nothing")
    void fullCollectorIsDark() {
        assertEquals(FeederLamp.DARK, FeederLamp.of(SectionExchange.Role.COLLECT, 4096, 0));
    }

    @Test
    @DisplayName("an idle feeder is dark whatever its buffer holds")
    void idleIsDark() {
        assertEquals(FeederLamp.DARK, FeederLamp.of(SectionExchange.Role.IDLE, 4096, 4096));
        assertEquals(FeederLamp.DARK, FeederLamp.of(null, 4096, 4096));
    }

    @Test
    @DisplayName("the colours are the lamp codes the renderer draws: 1 green, -1 amber, 0 none")
    void codesAreTheRenderersCodes() {
        assertEquals(1, FeederLamp.GREEN);
        assertEquals(-1, FeederLamp.AMBER);
        assertEquals(0, FeederLamp.DARK);
    }
}
