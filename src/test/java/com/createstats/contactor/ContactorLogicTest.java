package com.createstats.contactor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactorLogicTest {
    private static final boolean NO = false;
    private static final boolean NC = true;

    @Test
    @DisplayName("left makes the left pole, right the right one, both make all three; normally closed inverts each")
    void theTruthTable() {
        assertArrayEquals(new boolean[] {false, false, false}, ContactorLogic.poles(false, false, NO));
        assertArrayEquals(new boolean[] {true, false, false}, ContactorLogic.poles(true, false, NO));
        assertArrayEquals(new boolean[] {false, false, true}, ContactorLogic.poles(false, true, NO));
        assertArrayEquals(new boolean[] {true, true, true}, ContactorLogic.poles(true, true, NO));
        for (boolean l : new boolean[] {false, true}) {
            for (boolean r : new boolean[] {false, true}) {
                final boolean[] open = ContactorLogic.poles(l, r, NO);
                final boolean[] closed = ContactorLogic.poles(l, r, NC);
                for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
                    assertEquals(!open[pole], closed[pole], "normally closed must invert pole " + pole);
                }
            }
        }
    }

    @Test
    @DisplayName("the middle pole is operated only by both armatures, which is the evener bar's whole logic")
    void theMiddleIsTheAnd() {
        assertFalse(ContactorLogic.operated(ContactorLogic.MIDDLE, true, false));
        assertFalse(ContactorLogic.operated(ContactorLogic.MIDDLE, false, true));
        assertTrue(ContactorLogic.operated(ContactorLogic.MIDDLE, true, true));
        assertTrue(ContactorLogic.operated(ContactorLogic.LEFT, true, false));
        assertFalse(ContactorLogic.operated(ContactorLogic.LEFT, false, true));
        assertTrue(ContactorLogic.operated(ContactorLogic.RIGHT, false, true));
    }

    @Test
    @DisplayName("six studs: L1 to L3 on top, T1 to T3 below, each paired with the one across its pole")
    void theStuds() {
        assertEquals(6, ContactorLogic.STUDS);
        final String[] names = {"L1", "L2", "L3", "T1", "T2", "T3"};
        for (int stud = 0; stud < ContactorLogic.STUDS; stud++) {
            assertEquals(names[stud], ContactorLogic.studName(stud));
            assertEquals(stud < 3, ContactorLogic.isLine(stud));
            assertEquals(stud % 3, ContactorLogic.poleOf(stud));
            final int pair = ContactorLogic.pairedStud(stud);
            assertEquals(ContactorLogic.poleOf(stud), ContactorLogic.poleOf(pair), "a pair is one pole");
            assertEquals(stud, ContactorLogic.pairedStud(pair), "pairing goes both ways");
            assertTrue(ContactorLogic.isLine(stud) != ContactorLogic.isLine(pair), "a pair is a line and a load stud");
        }
        assertFalse(ContactorLogic.isStud(-1));
        assertFalse(ContactorLogic.isStud(6));
        assertEquals(-1, ContactorLogic.pairedStud(6));
    }

    @Test
    @DisplayName("a comparator reads 0 all open, 1 made and idle, and the busiest made pole against its own rating")
    void theComparator() {
        final int[] rating = {8_192, 16_384, 8_192};
        assertEquals(0, ContactorLogic.comparatorLevel(new boolean[] {false, false, false}, new int[] {9, 9, 9}, rating));
        assertEquals(1, ContactorLogic.comparatorLevel(new boolean[] {true, false, false}, new int[3], rating));
        assertEquals(15, ContactorLogic.comparatorLevel(new boolean[] {true, false, false},
                new int[] {8_192, 0, 0}, rating));

        assertEquals(8, ContactorLogic.comparatorLevel(new boolean[] {true, false, true},
                new int[] {4_096, 0, 4_096}, rating));

        assertEquals(1, ContactorLogic.comparatorLevel(new boolean[] {true, false, false},
                new int[] {0, 0, 99_999}, rating));

        assertEquals(15, ContactorLogic.comparatorLevel(new boolean[] {true, false, false},
                new int[] {5, 0, 0}, new int[3]));
        int last = 0;
        for (int load = 0; load <= 8_192; load += 256) {
            final int level = ContactorLogic.comparatorLevel(new boolean[] {true, false, false},
                    new int[] {load, 0, 0}, rating);
            assertTrue(level >= last, "the reading never falls as the load rises");
            last = level;
        }
    }

    @Test
    @DisplayName("sparks and arc gas are sized by the current a pole breaks, and a dead circuit throws none")
    void sparks() {
        assertEquals(0, ContactorLogic.sparksPerPole(0));
        assertEquals(1, ContactorLogic.sparksPerPole(1));
        assertEquals(ContactorLogic.MAX_SPARKS_PER_POLE, ContactorLogic.sparksPerPole(Integer.MAX_VALUE));
        assertEquals(0.0, ContactorLogic.sparkSpeed(0));
        assertTrue(ContactorLogic.sparkSpeed(65_536) > ContactorLogic.sparkSpeed(64));
        assertEquals(0, ContactorLogic.chutePuffs(1_023));
        assertEquals(1, ContactorLogic.chutePuffs(1_024));
        assertEquals(2, ContactorLogic.chutePuffs(8_192));
    }

    @Test
    @DisplayName("an armature at rest moves at once; NEVER is tested for, not subtracted")
    void aMoveFromRestIsNeverDelayed() {
        assertEquals(ContactorLogic.Step.MOVE, ContactorLogic.step(true, false, ContactorLogic.NEVER, 0L));
        assertEquals(ContactorLogic.Step.MOVE, ContactorLogic.step(true, false, ContactorLogic.NEVER, Long.MAX_VALUE));
        assertEquals(ContactorLogic.Step.STAY, ContactorLogic.step(true, true, ContactorLogic.NEVER, 100L));
        assertEquals(ContactorLogic.Step.MOVE, ContactorLogic.step(false, true, 100L, 104L));
        assertEquals(ContactorLogic.Step.WAIT, ContactorLogic.step(false, true, 100L, 103L));
        assertEquals(1, ContactorLogic.ticksToDwellEnd(ContactorLogic.NEVER, 50L));
        assertEquals(3, ContactorLogic.ticksToDwellEnd(100L, 101L));

        assertTrue(ContactorLogic.due(1_000L, 10L, ContactorLogic.DWELL_TICKS));
        assertTrue(new ContactorState().lastMoveTick(ContactorState.LEFT_SIDE) == ContactorLogic.NEVER);
    }

    private static List<Boolean> drive(boolean[] coil) {
        final ContactorState state = new ContactorState();
        boolean in = false;
        long pending = Long.MIN_VALUE;
        final List<Boolean> out = new ArrayList<>();
        for (int t = 0; t < coil.length; t++) {
            final boolean changed = t > 0 && coil[t] != coil[t - 1];
            if (changed || t == 0 || t == pending) {
                if (t == pending) {
                    pending = Long.MIN_VALUE;
                }
                switch (ContactorLogic.step(coil[t], in, state.lastMoveTick(ContactorState.LEFT_SIDE), t)) {
                    case MOVE -> {
                        in = coil[t];
                        state.recordMove(ContactorState.LEFT_SIDE, t);
                    }
                    case WAIT -> {
                        if (pending == Long.MIN_VALUE) {
                            pending = t + ContactorLogic.ticksToDwellEnd(state.lastMoveTick(ContactorState.LEFT_SIDE), t);
                        }
                    }
                    case STAY -> {
                    }
                }
            }
            out.add(in);
        }
        return out;
    }

    @Test
    @DisplayName("a one-tick pulse pulls in at once and drops out a dwell later")
    void aOneTickPulse() {
        final boolean[] coil = new boolean[12];
        coil[1] = true;
        final List<Boolean> in = drive(coil);
        assertTrue(in.get(1), "pulled in on the pulse's own tick");
        for (int t = 2; t < 1 + ContactorLogic.DWELL_TICKS; t++) {
            assertTrue(in.get(t), "held in through the dwell, tick " + t);
        }
        assertFalse(in.get(1 + ContactorLogic.DWELL_TICKS), "dropped out at the dwell's end");
    }

    @Test
    @DisplayName("a one-tick clock: the armature moves at most every dwell, keeps following, and ends where the coil does")
    void aOneTickClock() {
        final boolean[] coil = new boolean[60];
        for (int t = 0; t < 40; t++) {
            coil[t] = t % 2 == 1;
        }
        final List<Boolean> in = drive(coil);
        int lastMove = -100;
        int moves = 0;
        for (int t = 1; t < in.size(); t++) {
            if (!in.get(t).equals(in.get(t - 1))) {
                assertTrue(t - lastMove >= ContactorLogic.DWELL_TICKS, "moves at " + lastMove + " and " + t);
                lastMove = t;
                moves++;
            }
        }
        assertTrue(moves >= 6, "still following the clock: " + moves + " moves");
        assertEquals(coil[coil.length - 1], in.get(in.size() - 1), "settles where the coil is");
    }

    @Test
    @DisplayName("each armature keeps its own dwell: one coil's move never holds the other back")
    void twoDwells() {
        final ContactorState state = new ContactorState();
        state.recordMove(ContactorState.LEFT_SIDE, 100L);
        assertEquals(ContactorLogic.Step.WAIT,
                ContactorLogic.step(false, true, state.lastMoveTick(ContactorState.LEFT_SIDE), 101L));
        assertEquals(ContactorLogic.Step.MOVE,
                ContactorLogic.step(true, false, state.lastMoveTick(ContactorState.RIGHT_SIDE), 101L));
    }

    @Test
    @DisplayName("the first tick is taken once, and the comparator is told only of a change")
    void bookkeeping() {
        final ContactorState state = new ContactorState();
        assertTrue(state.takeFirstTick());
        assertFalse(state.takeFirstTick());
        assertFalse(state.moveComparator(0));
        assertTrue(state.moveComparator(3));
        assertFalse(state.moveComparator(3));
        assertEquals(3, state.comparatorLevel());
    }

    @Test
    @DisplayName("a goggle load is sent on a two percent move or any move to or from zero")
    void theSyncDeadband() {
        assertFalse(ContactorLogic.worthSending(1_000, 1_000));
        assertFalse(ContactorLogic.worthSending(1_000, 1_019));
        assertTrue(ContactorLogic.worthSending(1_000, 1_021));
        assertTrue(ContactorLogic.worthSending(0, 1));
        assertTrue(ContactorLogic.worthSending(5, 0));
    }
}
