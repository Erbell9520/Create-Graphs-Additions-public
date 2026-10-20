package com.createstats.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelGuardTest {
    @Test
    @DisplayName("An unclaimed panel breaks freely, or a misplaced one is welded to the wall")
    void unclaimed() {
        assertTrue(PanelGuard.mayBreak(false, false, false, false));
        assertTrue(PanelGuard.mayBreak(false, false, true, false));
    }

    @Test
    @DisplayName("A claimed panel refuses everybody but its owner")
    void claimed() {
        assertTrue(PanelGuard.mayBreak(true, true, false, false));
        assertFalse(PanelGuard.mayBreak(true, false, false, false));
    }

    @Test
    @DisplayName("Creative and operators always pass, because the alternative is a support ticket")
    void exemptions() {
        assertTrue(PanelGuard.mayBreak(true, false, true, false));
        assertTrue(PanelGuard.mayBreak(true, false, false, true));
    }

    @Test
    @DisplayName("Only a claimed panel is protected, so protection follows ownership and nothing else")
    void protectionFollowsOwnership() {
        assertFalse(PanelGuard.mayBreak(true, false, false, false));
        assertTrue(PanelGuard.mayBreak(false, false, false, false));
    }
}
