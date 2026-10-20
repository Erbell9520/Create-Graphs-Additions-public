package com.createstats.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockerAccessTest {
    private static boolean normal(boolean clearancePasses) {
        return LockerAccess.opensWithoutCode(LockerMode.GENERAL, true, true, true, false,
                clearancePasses, false, false);
    }

    private static boolean personal(boolean readerLive, boolean codeDemanded, boolean holder) {
        return LockerAccess.opensWithoutCode(LockerMode.PERSONAL, true, true, readerLive,
                codeDemanded, true, holder, false);
    }

    private static boolean sealed(boolean hasCode, boolean clearancePasses, boolean holder) {
        return LockerAccess.opensWithoutCode(LockerMode.GENERAL, true, hasCode, true, false,
                clearancePasses, holder, true);
    }

    @Test
    @DisplayName("Normally the roster decides and nothing else does")
    void ordinary() {
        assertTrue(normal(true));
        assertFalse(normal(false));
    }

    @Test
    @DisplayName("A box nobody claimed opens for anyone, until it is carrying a code")
    void unclaimed() {
        assertTrue(LockerAccess.opensWithoutCode(LockerMode.UNSET, false, false, false, false,
                        false, false, false),
                "an unwired locker with no code is just a box");

        assertFalse(LockerAccess.opensWithoutCode(LockerMode.UNSET, false, true, false, false, true,
                        true, false),
                "a sealed locker carries its code and stays shut wherever it is put down");
    }

    @Test
    @DisplayName("A dark reader refuses everybody, clearance or not")
    void readerDark() {
        assertFalse(LockerAccess.opensWithoutCode(LockerMode.GENERAL, true, true, false, false, true,
                        false, false),
                "no power and no panel are the same failure, and the ladder is not consulted");
    }

    @Test
    @DisplayName("The latch outlives the outage that raised it")
    void latch() {
        assertFalse(LockerAccess.opensWithoutCode(LockerMode.GENERAL, true, true, true, true, true,
                        false, false),
                "a locker that lost its reader keeps asking for the code after the reader is back");
    }

    @Test
    @DisplayName("An unanswered locker behaves exactly as a household one")
    void unsetIsGeneral() {
        for (boolean passes : new boolean[]{true, false}) {
            for (boolean houseSealed : new boolean[]{true, false}) {
                assertEquals(
                        LockerAccess.opensWithoutCode(LockerMode.GENERAL, true, true, true, false,
                                passes, false, houseSealed),
                        LockerAccess.opensWithoutCode(LockerMode.UNSET, true, true, true, false,
                                passes, false, houseSealed),
                        "unset must decide exactly what general decides");
            }
        }
    }

    @Test
    @DisplayName("A personal locker opens for its holder and for nobody else")
    void personalHolder() {
        assertTrue(personal(true, false, true));

        assertFalse(personal(true, false, false),
                "clearance is not consulted on a personal locker, not even a keyholder's");
    }

    @Test
    @DisplayName("Off the network a personal locker asks its holder for the code too")
    void personalOffNetwork() {
        assertFalse(personal(false, false, true),
                "no power or no panel means the box cannot recognise anybody, holder included");
        assertFalse(personal(true, true, true),
                "and the latch a lost reader leaves behind outlives it for the holder as well");
    }

    @Test
    @DisplayName("Only a code-less locker with a dark reader can be claimed")
    void claiming() {
        assertTrue(LockerAccess.mayClaimCode(LockerMode.GENERAL, true, false, false));

        assertFalse(LockerAccess.mayClaimCode(LockerMode.GENERAL, true, true, false),
                "a locker with a code asks for that code; it is not up for grabs");
        assertFalse(LockerAccess.mayClaimCode(LockerMode.GENERAL, true, false, true),
                "with the panel reachable, codes are set from the panel by somebody it trusts");
        assertFalse(LockerAccess.mayClaimCode(LockerMode.UNSET, false, false, false),
                "an unclaimed code-less locker already opens, so there is nothing to claim");

        assertTrue(LockerAccess.mayClaimCode(LockerMode.PERSONAL, false, false, false),
                "a personal locker with no code and no reader must not be a brick");
    }

    @Test
    @DisplayName("A sealed house makes the code the only way into a coded locker, owner included")
    void sealedDemandsTheCode() {
        assertFalse(sealed(true, true, false), "clearance must stop counting");
        assertFalse(sealed(true, true, true), "and so must having placed the box");
        assertFalse(sealed(true, false, false));
    }

    @Test
    @DisplayName("A sealed house cannot weld a locker that never had a code")
    void sealedWithoutACodeStillAsksTheRoster() {
        assertTrue(sealed(false, true, false));
        assertFalse(sealed(false, false, false));
        assertFalse(LockerAccess.mayClaimCode(LockerMode.GENERAL, true, false, true),
                "and the claim route is still shut, so nothing has become easier either");
    }

    @Test
    @DisplayName("A personal locker is not the house's to seal, in either direction")
    void sealedLeavesPersonalAlone() {
        assertTrue(LockerAccess.opensWithoutCode(LockerMode.PERSONAL, true, true, true, false,
                        true, true, true),
                "the holder still walks in");
        assertFalse(LockerAccess.opensWithoutCode(LockerMode.PERSONAL, true, true, true, false,
                        true, false, true),
                "and everybody else still types");
    }

    @Test
    @DisplayName("An unclaimed box is not in the house, so a seal does not reach it")
    void sealedLeavesUnclaimedAlone() {
        assertTrue(LockerAccess.opensWithoutCode(LockerMode.GENERAL, false, false, false, false,
                        false, false, true));
        assertFalse(LockerAccess.opensWithoutCode(LockerMode.GENERAL, false, true, false, false,
                        false, false, true),
                "a sealed drop still holds its code");
    }

    @Test
    @DisplayName("The seal is below the dark reader and the latch, not above them")
    void sealedDoesNotReorderTheLadder() {
        assertFalse(LockerAccess.opensWithoutCode(LockerMode.GENERAL, true, true, false, false,
                true, false, true), "a dark reader is still the first refusal");
        assertFalse(LockerAccess.opensWithoutCode(LockerMode.GENERAL, true, true, true, true,
                true, false, true), "and the latch is still the second");
    }
}
