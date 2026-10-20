package com.createstats.driverhelmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudBootTest {
    @Test
    void darkUntilTheLatchThenTheSelfTestThenTheReadouts() {
        assertEquals(HudBoot.Phase.DARK, HudBoot.phase(0.2, false, 2.0), "the visor is still closing");
        assertEquals(HudBoot.Phase.SELF_TEST, HudBoot.phase(HudBoot.START + 0.01, false, 2.0));
        assertEquals(HudBoot.Phase.LIVE, HudBoot.phase(HudBoot.START + HudBoot.SELF_TEST + 0.01, false, 2.0),
                "no welcome: straight to the readouts");
    }

    @Test
    void theWelcomeCrawlsTheWholeNameThroughBeforeTheReadouts() {
        final int text = LedFont.width("WELCOME STEVE", 2, 1, 1);
        final double length = HudBoot.welcomeLength(text, 97);
        assertEquals((text + 97) / HudBoot.CRAWL, length, 1e-9);
        final double after = HudBoot.START + HudBoot.SELF_TEST;
        assertEquals(HudBoot.Phase.WELCOME, HudBoot.phase(after + 0.1, true, length));
        assertEquals(HudBoot.Phase.LIVE, HudBoot.phase(after + length + 0.01, true, length));
        assertEquals(0.1, HudBoot.into(after + 0.1, HudBoot.Phase.WELCOME), 1e-9);
        assertTrue(length < 3.5, "a welcome, not a wait: " + length);
    }

    @Test
    void theBootRunsFromTheLatestOfTheVisorTheHelmetAndTheHelm() {
        final double none = Double.NaN;
        assertEquals(HudBoot.Anchor.NONE, HudBoot.anchor(none, none, none));
        assertEquals(HudBoot.Anchor.WORN, HudBoot.anchor(none, 100, none));
        assertEquals(HudBoot.Anchor.STROKE, HudBoot.anchor(120, 100, none), "the visor closed after it went on");
        assertEquals(HudBoot.Anchor.STROKE, HudBoot.anchor(100, 100, none), "closed as it went on: the stroke's boot");
        assertEquals(HudBoot.Anchor.WORN, HudBoot.anchor(80, 100, none), "an old stroke from before it went on");

        assertEquals(HudBoot.Anchor.HELM, HudBoot.anchor(120, 100, 5000));
        assertEquals(HudBoot.Anchor.HELM, HudBoot.anchor(none, none, 5000), "a helm taken with a visor already down");
        assertEquals(HudBoot.Anchor.STROKE, HudBoot.anchor(6000, 100, 5000), "a visor closed after sitting down");
        assertEquals(HudBoot.Anchor.WORN, HudBoot.anchor(none, 6000, 5000), "a helmet put on after sitting down");
    }

    @Test
    void theWelcomeWaitsForAStandingCarAndThePlatesFadeIn() {
        assertTrue(HudBoot.welcomeAllowed(0.0));
        assertTrue(HudBoot.welcomeAllowed(-HudBoot.WELCOME_MAX_SPEED));
        assertFalse(HudBoot.welcomeAllowed(HudBoot.WELCOME_MAX_SPEED + 0.1), "moving: the driver needs the speed");
        assertEquals(0.0, HudBoot.fade(HudBoot.START), 1e-9);
        assertEquals(1.0, HudBoot.fade(HudBoot.START + HudBoot.FADE), 1e-9);
    }
}
