package com.createstats.gui.pdu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PduBalanceBarTest {
    @Test
    @DisplayName("an idle branch says it is idle rather than denying the devices exist")
    void idleDevicesAreNotDeniedAltogether() {
        assertEquals("18 devices connected, drawing nothing", PduBalanceBar.emptyStateText(18));
    }

    @Test
    @DisplayName("nothing wired is a different sentence from nothing drawing")
    void nothingWiredIsItsOwnSentence() {
        assertEquals("No devices on the branch", PduBalanceBar.emptyStateText(0));
    }

    @Test
    @DisplayName("one device does not read as one devices")
    void singularReads() {
        assertEquals("1 device connected, drawing nothing", PduBalanceBar.emptyStateText(1));
    }
}
