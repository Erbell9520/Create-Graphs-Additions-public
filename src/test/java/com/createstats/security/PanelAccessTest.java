package com.createstats.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PanelAccessTest {
    @Test
    @DisplayName("No password means no gate, so upgrading a world does not brick its panels")
    void noPasswordNoGate() {
        assertEquals(PanelAccess.Entry.OPEN, PanelAccess.demand(false, false, false, 0));
        assertEquals(PanelAccess.Entry.OPEN, PanelAccess.demand(false, false, false, 1));
        assertEquals(PanelAccess.Entry.OPEN, PanelAccess.demand(false, false, true, Clearance.MAX));
    }

    @Test
    @DisplayName("Always ask beats the owner, which is the whole of what the setting promises")
    void alwaysAskBeatsOwner() {
        assertEquals(PanelAccess.Entry.PASSWORD,
                PanelAccess.demand(true, true, true, Clearance.MAX));
    }

    @Test
    @DisplayName("Always ask is worth nothing without a password to ask for")
    void alwaysAskNeedsAPassword() {
        assertEquals(PanelAccess.Entry.OPEN, PanelAccess.demand(false, true, false, 1));
        assertEquals(PanelAccess.Entry.OPEN, PanelAccess.demand(false, true, true, 0));
    }

    @Test
    @DisplayName("The owner and a keyholder walk in, everybody else types")
    void theLadder() {
        assertEquals(PanelAccess.Entry.OPEN, PanelAccess.demand(true, false, true, 0),
                "the owner bypass does not go through the ladder, exactly as at every door");
        assertEquals(PanelAccess.Entry.OPEN,
                PanelAccess.demand(true, false, false, PanelAccess.KEYHOLDER));
        assertEquals(PanelAccess.Entry.PASSWORD,
                PanelAccess.demand(true, false, false, PanelAccess.KEYHOLDER - 1));
        assertEquals(PanelAccess.Entry.PASSWORD, PanelAccess.demand(true, false, false, 0),
                "somebody not on the roster at all is asked, not refused outright");
    }

    @Test
    @DisplayName("Keyholder is the top of the existing ladder, not a sixth level")
    void keyholderIsFive() {
        assertEquals(Clearance.MAX, PanelAccess.KEYHOLDER);
    }

    @Test
    @DisplayName("A clearance above the top still passes, so clamping cannot lock the top rung out")
    void aboveTheTop() {
        assertEquals(PanelAccess.Entry.OPEN,
                PanelAccess.demand(true, false, false, Clearance.MAX + 3));
    }

    @Test
    @DisplayName("Joining a locked house asks, and asks the owner too")
    void joiningALockedHouseAsks() {
        assertEquals(PanelAccess.Entry.PASSWORD, PanelAccess.demandOnJoin(true),
                "a fresh panel is not in the house yet, so it has no bypass to inherit");
    }

    @Test
    @DisplayName("Joining an unlocked house is not gated, for the reason nothing else here is")
    void joiningAnUnlockedHouseIsNotGated() {
        assertEquals(PanelAccess.Entry.OPEN, PanelAccess.demandOnJoin(false));
    }

    @Test
    @DisplayName("A join is not the entry ladder wearing a different hat")
    void joinIsNotTheLadder() {
        assertEquals(PanelAccess.Entry.OPEN, PanelAccess.demand(true, false, true, 0));
        assertEquals(PanelAccess.Entry.OPEN,
                PanelAccess.demand(true, false, false, PanelAccess.KEYHOLDER));
        assertEquals(PanelAccess.Entry.PASSWORD, PanelAccess.demandOnJoin(true));
    }
}
