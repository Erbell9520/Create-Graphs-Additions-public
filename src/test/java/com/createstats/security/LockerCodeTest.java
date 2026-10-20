package com.createstats.security;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockerCodeTest {
    private static final String RIGHT = "1234";
    private static final String WRONG = "0000";

    private static LockerCode withCode() {
        final LockerCode code = new LockerCode();
        assertTrue(code.set(RIGHT));
        return code;
    }

    @Test
    @DisplayName("Only four digits are a code")
    void wellFormed() {
        assertTrue(LockerCode.wellFormed("0000"));
        assertTrue(LockerCode.wellFormed("9999"));
        assertFalse(LockerCode.wellFormed(null));
        assertFalse(LockerCode.wellFormed(""));
        assertFalse(LockerCode.wellFormed("123"));
        assertFalse(LockerCode.wellFormed("12345"));
        assertFalse(LockerCode.wellFormed("12a4"));
        assertFalse(LockerCode.wellFormed(" 123"));
    }

    @Test
    @DisplayName("A locker with no code has no keypad route")
    void noCode() {
        final LockerCode code = new LockerCode();
        assertFalse(code.isSet());
        assertEquals(LockerCode.Result.NO_CODE, code.offer(RIGHT, 0L));
    }

    @Test
    @DisplayName("The right code opens it and the wrong one does not")
    void openAndRefuse() {
        final LockerCode code = withCode();
        assertEquals(LockerCode.Result.OPENED, code.offer(RIGHT, 0L));
        assertEquals(LockerCode.Result.WRONG, code.offer(WRONG, 1L));
        assertEquals(LockerCode.Result.MALFORMED, code.offer("12", 2L));
    }

    @Test
    @DisplayName("Three wrong in a row shuts the keypad for a minute and reports tamper")
    void threeStrikes() {
        final LockerCode code = withCode();
        assertEquals(LockerCode.Result.WRONG, code.offer(WRONG, 0L));
        assertEquals(LockerCode.Result.WRONG, code.offer(WRONG, 1L));
        assertEquals(LockerCode.Result.TAMPER, code.offer(WRONG, 2L));
        assertTrue(code.lockedOut(2L));
        assertEquals(LockerCode.LOCKOUT_TICKS, code.lockoutRemaining(2L));
    }

    @Test
    @DisplayName("The right code does not walk through a lockout")
    void lockoutBeatsTheAnswer() {
        final LockerCode code = withCode();
        code.offer(WRONG, 0L);
        code.offer(WRONG, 0L);
        assertEquals(LockerCode.Result.TAMPER, code.offer(WRONG, 0L));

        assertEquals(LockerCode.Result.LOCKED_OUT, code.offer(RIGHT, 100L));
        assertEquals(LockerCode.Result.LOCKED_OUT,
                code.offer(RIGHT, LockerCode.LOCKOUT_TICKS - 1L));
        assertEquals(LockerCode.Result.OPENED, code.offer(RIGHT, LockerCode.LOCKOUT_TICKS));
    }

    @Test
    @DisplayName("Strikes are consecutive, so an honest mistype never accumulates")
    void strikesReset() {
        final LockerCode code = withCode();
        assertEquals(LockerCode.Result.WRONG, code.offer(WRONG, 0L));
        assertEquals(LockerCode.Result.WRONG, code.offer(WRONG, 1L));
        assertEquals(LockerCode.Result.OPENED, code.offer(RIGHT, 2L));
        assertEquals(0, code.strikes());

        assertEquals(LockerCode.Result.WRONG, code.offer(WRONG, 3L));
        assertEquals(LockerCode.Result.WRONG, code.offer(WRONG, 4L));
        assertFalse(code.lockedOut(4L));
    }

    @Test
    @DisplayName("Setting a code clears a lockout somebody else raised")
    void settingClearsTheLockout() {
        final LockerCode code = withCode();
        code.offer(WRONG, 0L);
        code.offer(WRONG, 0L);
        code.offer(WRONG, 0L);
        assertTrue(code.lockedOut(1L));
        assertTrue(code.set("4321"));
        assertFalse(code.lockedOut(1L));
        assertEquals(LockerCode.Result.OPENED, code.offer("4321", 1L));
    }

    @Test
    @DisplayName("A malformed code is refused without changing anything")
    void malformedSetChangesNothing() {
        final LockerCode code = withCode();
        assertFalse(code.set("12"));
        assertEquals(LockerCode.Result.OPENED, code.offer(RIGHT, 0L));
    }

    @Test
    @DisplayName("The lockout survives a save, so mining the chunk out is not a reset button")
    void roundTrip() {
        final LockerCode code = withCode();
        code.offer(WRONG, 0L);
        code.offer(WRONG, 0L);
        assertEquals(LockerCode.Result.TAMPER, code.offer(WRONG, 0L));

        final CompoundTag tag = code.save();
        final LockerCode loaded = new LockerCode();
        loaded.load(tag);
        assertTrue(loaded.isSet());
        assertTrue(loaded.lockedOut(0L));
        assertEquals(LockerCode.Result.LOCKED_OUT, loaded.offer(RIGHT, 10L));
        assertEquals(LockerCode.Result.OPENED, loaded.offer(RIGHT, LockerCode.LOCKOUT_TICKS));
    }

    @Test
    @DisplayName("A copy is the same lock, which is what a sealed locker carries")
    void copyCarriesEverything() {
        final LockerCode code = withCode();
        code.offer(WRONG, 0L);
        code.offer(WRONG, 0L);
        code.offer(WRONG, 0L);
        final LockerCode sealed = code.copy();
        assertTrue(sealed.isSet());
        assertTrue(sealed.lockedOut(0L));
        assertEquals(LockerCode.Result.OPENED, sealed.offer(RIGHT, LockerCode.LOCKOUT_TICKS));
    }

    @Test
    @DisplayName("Clearing removes the route entirely")
    void clear() {
        final LockerCode code = withCode();
        code.clear();
        assertFalse(code.isSet());
        assertEquals(LockerCode.Result.NO_CODE, code.offer(RIGHT, 0L));
    }
}
