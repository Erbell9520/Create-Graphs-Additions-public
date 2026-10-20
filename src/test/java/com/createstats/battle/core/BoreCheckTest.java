package com.createstats.battle.core;

import com.createstats.battle.core.BoreCheck.Bore;
import com.createstats.battle.core.BoreCheck.Cell;
import com.createstats.battle.core.BoreCheck.Chances;
import com.createstats.battle.core.BoreCheck.Outcome;
import com.createstats.battle.core.BoreCheck.Reason;
import com.createstats.battle.core.BoreCheck.Verdict;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoreCheckTest {
    private static final boolean[] OWNER = {true, true, false, false};
    private static final int CAST_IRON_SAFE = 2;
    private static final double CAST_IRON_PER_BARREL = 2.0;

    private static Verdict owner(String spec) {
        return BoreCheck.judge(bore(spec, OWNER, CAST_IRON_SAFE, CAST_IRON_PER_BARREL, Chances.STOCK, false));
    }

    private static Bore bore(String spec, boolean[] thick, int maxSafe, double perBarrel, Chances chances, boolean breechOpen) {
        String[] tokens = spec.isBlank() ? new String[0] : spec.trim().split("\\s+");
        List<Cell> cells = new ArrayList<>();
        int charges = 0;
        boolean safe = true;
        boolean shellDone = false;
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i];
            boolean th = i < thick.length ? thick[i] : false;
            switch (t.charAt(0)) {
                case 'C', 'c' -> {
                    int power = Integer.parseInt(t.substring(1));
                    boolean lights = t.charAt(0) == 'C' && power > 0;
                    safe &= charges == 0 && lights;
                    charges++;
                    cells.add(Cell.charge(th, 2f * power, 0.5f * power, lights, safe));
                }
                case 'P' -> {
                    charges++;
                    cells.add(Cell.charge(th, 2f, 1f, true, safe));
                }
                case 'p' -> {
                    charges++;
                    cells.add(Cell.charge(th, 1f, 0.5f, false, safe));
                }
                case 'S' -> {
                    cells.add(shellDone ? Cell.shell(th, false, false, 0, 0, false) : Cell.shell(th, true, true, 0f, 1f, true));
                    shellDone = true;
                }
                case 'X' -> cells.add(Cell.other(th));
                case '.' -> cells.add(Cell.empty(th));
                default -> throw new IllegalArgumentException(t);
            }
        }
        return new Bore(cells, breechOpen, false, maxSafe, perBarrel, 0.05f, chances, false, null);
    }

    private static Bore with(Bore b, List<Cell> cells) {
        return new Bore(cells, b.breechOpen(), b.dropMortar(), b.maxSafe(), b.minVelocityPerBarrel(), b.minimumSpread(),
                b.chances(), b.muzzleBlocked(), b.integrated());
    }

    @Test
    void theOwnersGunFiresAWholeLoad() {
        Verdict v = owner("C4 S . .");
        assertEquals(Reason.NONE, v.reason());
        assertTrue(v.fires());
        assertEquals(8f, v.speed());
        assertEquals(2f, v.stress());
        assertEquals(0, v.burst());
        assertEquals(0, v.dud(), "8 of power over two barrels is 4 a barrel, over cast iron's 2: no squib");

        assertEquals(Reason.NONE, owner("P P S .").reason());
    }

    @Test
    void anOverchargeOnTheOwnersGunIsHeld() {
        Verdict v = owner("C4 P S .");
        assertEquals(Reason.OVERLOAD, v.reason());
        assertEquals(Outcome.UNSAFE, v.reason().outcome());
        assertFalse(v.fires());
        assertEquals(0.5, v.burst(), 1e-6);
        assertEquals(3f, v.stress());
        assertEquals(2, v.maxSafe());
        assertEquals(1, v.cell(), "the charge that passes the limit");
        assertEquals(10f, v.speed(), "a held gun keeps aiming with the speed it would have");
    }

    @Test
    void everyChargeOverTheLimitRollsAgain() {
        Verdict v = BoreCheck.judge(bore("P P P S", new boolean[]{true, true, true, true}, 1, 1.0, Chances.STOCK, false));
        assertEquals(Reason.OVERLOAD, v.reason());
        assertEquals(0.75, v.burst(), 1e-6);
        assertEquals(1, v.cell(), "the reason names the first charge over the limit");
    }

    @Test
    void aChargeInABarrelBlockIsHeld() {
        Verdict v = BoreCheck.judge(bore("P P S .", new boolean[]{true, false, false, false}, 8, 1.0, Chances.STOCK, false));
        assertEquals(Reason.CHARGE_IN_BARREL, v.reason());
        assertEquals(0.2, v.burst(), 1e-6);
        assertEquals(1, v.cell());
    }

    @Test
    void aBarrelChargeOverTheLimitRollsBoth() {
        Verdict v = owner("P P P S");
        assertEquals(Reason.CHARGE_IN_BARREL, v.reason(), "the first roll CBC makes on that charge");
        assertEquals(0.6, v.burst(), 1e-6);
    }

    @Test
    void aCartridgeNotFirstOrNotAloneBurstsForCertain() {
        Verdict behind = owner("P C4 S .");
        assertEquals(Reason.CHARGE_ORDER, behind.reason());
        assertEquals(1.0, behind.burst(), 1e-6);
        assertEquals(1, behind.cell());
        assertEquals(Reason.CHARGE_ORDER, owner("C4 C4 S .").reason());

        assertEquals(Reason.CHARGE_ORDER, owner("P C0 S .").reason());
    }

    @Test
    void anythingAheadOfTheShellBurstsForCertain() {
        assertEquals(Reason.OBSTRUCTED, owner("C4 S S .").reason());
        Verdict stuck = owner("C4 S . S");
        assertEquals(Reason.OBSTRUCTED, stuck.reason());
        assertEquals(1.0, stuck.burst(), 1e-6);
        assertEquals(3, stuck.cell());
        assertEquals(Reason.OBSTRUCTED, owner("C4 S X .").reason());

        assertEquals(Reason.OBSTRUCTED, owner("X . . .").reason());
    }

    @Test
    void aBlockPastTheMuzzleBurstsIt() {
        Bore b = bore("C4 S . .", OWNER, CAST_IRON_SAFE, CAST_IRON_PER_BARREL, Chances.STOCK, false);
        Bore past = new Bore(b.cells(), false, false, 2, 2.0, 0.05f, Chances.STOCK, true, null);
        assertEquals(Reason.OBSTRUCTED, BoreCheck.judge(past).reason());
        assertEquals(4, BoreCheck.judge(past).cell());
    }

    @Test
    void aShellOutOfLineOrNotWholeBurstsForCertain() {
        Bore base = bore("C4", OWNER, CAST_IRON_SAFE, CAST_IRON_PER_BARREL, Chances.STOCK, false);
        Cell charge = base.cells().get(0);

        Verdict wrong = BoreCheck.judge(with(base, List.of(charge, Cell.shell(true, false, false, 0, 0, false))));
        assertEquals(Reason.BAD_SHELL, wrong.reason());
        assertEquals(1.0, wrong.burst(), 1e-6);

        Cell front = Cell.shell(true, true, false, 0, 0, false);
        Cell back = Cell.shell(false, true, true, 0f, 1f, true);
        assertEquals(Reason.BAD_SHELL, BoreCheck.judge(with(base, List.of(charge, front, Cell.empty(false), back))).reason());
        Verdict half = BoreCheck.judge(with(base, List.of(charge, front, Cell.empty(false), Cell.empty(false))));
        assertEquals(Reason.BAD_SHELL, half.reason(), "a gap inside a shell still being seated");
        assertEquals(Reason.BAD_SHELL, BoreCheck.judge(with(base, List.of(charge, front))).reason(), "not whole at the muzzle");

        assertEquals(Reason.NONE, BoreCheck.judge(with(base, List.of(charge, front, back, Cell.empty(false)))).reason());
    }

    @Test
    void aShellWithItsOwnPropellantMustStillBeASafeLoad() {
        Bore b = bore("C4 S . .", OWNER, CAST_IRON_SAFE, CAST_IRON_PER_BARREL, Chances.STOCK, false);
        Bore unsafe = new Bore(b.cells(), false, false, 2, 2.0, 0.05f, Chances.STOCK, false, new BoreCheck.Integrated(false, 2, 1));
        assertEquals(Reason.CHARGE_ORDER, BoreCheck.judge(unsafe).reason());
        Bore safe = new Bore(b.cells(), false, false, 2, 2.0, 0.05f, Chances.STOCK, false, new BoreCheck.Integrated(true, 2, 1));
        Verdict v = BoreCheck.judge(safe);
        assertEquals(Reason.NONE, v.reason(), "its stress is added after the walk, with no roll, as CBC adds it");
        assertEquals(10f, v.speed());
    }

    @Test
    void tooLittlePowerAtTheShellJamsItAndIsHeld() {
        Verdict gaps = owner("P . . S");
        assertEquals(Reason.UNDERCHARGED, gaps.reason());
        assertEquals(Outcome.UNSAFE, gaps.reason().outcome());
        assertEquals(3, gaps.cell());

        Bore b = bore("P S . .", OWNER, CAST_IRON_SAFE, CAST_IRON_PER_BARREL, Chances.STOCK, false);
        List<Cell> heavy = new ArrayList<>(b.cells());
        heavy.set(1, Cell.shell(true, true, true, 0f, 3f, true));
        assertEquals(Reason.UNDERCHARGED, BoreCheck.judge(with(b, heavy)).reason());
    }

    @Test
    void aQuickFiringBreechOpenOrShuttingFiresNothing() {
        Verdict v = BoreCheck.judge(bore("C4 S . .", OWNER, CAST_IRON_SAFE, CAST_IRON_PER_BARREL, Chances.STOCK, true));
        assertEquals(Reason.BREECH_OPEN, v.reason());
        assertEquals(Outcome.WONT_FIRE, v.reason().outcome());
        assertEquals(8f, v.speed(), "the speed it will have, so the gun aims before the breech shuts");

        assertEquals(Reason.BREECH_OPEN, BoreCheck.judge(bore("S . . .", OWNER, 2, 2.0, Chances.STOCK, true)).reason());

        Verdict over = BoreCheck.judge(bore("C4 P S .", OWNER, CAST_IRON_SAFE, CAST_IRON_PER_BARREL, Chances.STOCK, true));
        assertEquals(Reason.OVERLOAD, over.reason());
    }

    @Test
    void anEmptyOrDeadBreechEndFiresNothing() {
        assertEquals(Reason.NOTHING, owner(". . . .").reason());
        assertEquals(Reason.NOTHING, owner("").reason());
        assertEquals(Reason.NOTHING, owner(". P . .").reason(), "CBC looks only at the first cell for a start");

        assertEquals(Reason.DEAD_CHARGE, owner("C0 . . .").reason());

        assertEquals(Reason.DEAD_CHARGE, owner("c4 S . .").reason());
        assertEquals(Reason.DEAD_CHARGE, owner("p S . .").reason());
    }

    @Test
    void aShellWithNothingBehindItIsAStuckShell() {
        assertEquals(Reason.STUCK_SHELL, owner("C0 S . .").reason());
        assertEquals(Reason.STUCK_SHELL, owner(". . S .").reason());
    }

    @Test
    void chargesWithoutAShellOrAShellWithoutACharge() {
        assertEquals(Reason.NO_SHELL, owner("C4 . . .").reason());

        assertEquals(Reason.NO_CHARGE, owner("S . . .").reason());

        assertEquals(Outcome.WONT_FIRE, owner("S . . .").reason().outcome());
    }

    @Test
    void aDropMortarNeverFiresOnCommand() {
        Bore b = bore("C4 S . .", OWNER, CAST_IRON_SAFE, CAST_IRON_PER_BARREL, Chances.STOCK, false);
        Bore mortar = new Bore(b.cells(), false, true, 2, 2.0, 0.05f, Chances.STOCK, false, null);
        assertEquals(Reason.DROP_MORTAR, BoreCheck.judge(mortar).reason());
    }

    @Test
    void anAirGapCostsPowerAndMayStopTheIgnitionButIsFired() {
        Verdict v = owner("P . S .");
        assertEquals(Reason.NONE, v.reason());
        assertEquals(1f, v.speed());
        assertEquals(0, v.burst());
        assertEquals(0.33 + 0.67 * 0.25, v.dud(), 1e-6);
    }

    @Test
    void aSlowShellInALongBarrelMaySquibAndIsStillFired() {
        Verdict v = BoreCheck.judge(bore("C1 S . . .", new boolean[]{true, false, false, false, false}, CAST_IRON_SAFE,
                CAST_IRON_PER_BARREL, Chances.STOCK, false));
        assertEquals(Reason.NONE, v.reason());
        assertEquals(0.25 + 0.75 * 0.25, v.dud(), 1e-6);

        Verdict log = BoreCheck.judge(bore("P S . .", OWNER, 8, -1, Chances.STOCK, false));
        assertEquals(0.25 + 0.75 * 0.25, log.dud(), 1e-6);
    }

    @Test
    void theChancesCountOnlyTheWalksThatGetThatFar() {
        Bore b = bore("P . P P S", new boolean[]{true, true, true, true, true}, 1, 1.0, Chances.STOCK, false);
        Verdict v = BoreCheck.judge(b);
        assertEquals(Reason.OVERLOAD, v.reason());
        double afterSecond = 0.5 * (1 - 0.33);
        assertEquals(0.5 + afterSecond * 0.5, v.burst(), 1e-6);
        assertEquals(0.5 * 0.33, v.dud(), 1e-6);
    }

    @Test
    void withFailureOffNothingBurstsAndCbcFiresWhatItCan() {
        Chances off = new Chances(false, 0.25f, 0.2f, 0.5f, 0.33f);
        Verdict over = BoreCheck.judge(bore("C4 P S .", OWNER, CAST_IRON_SAFE, CAST_IRON_PER_BARREL, off, false));
        assertEquals(Reason.NONE, over.reason());
        assertEquals(10f, over.speed());

        assertEquals(Reason.NONE, BoreCheck.judge(bore("C4 S S .", OWNER, 2, 2.0, off, false)).reason());
        Verdict order = BoreCheck.judge(bore("P C4 S .", OWNER, 2, 2.0, off, false));
        assertEquals(Reason.NONE, order.reason());
        assertEquals(2f, order.speed());
    }

    @Test
    void aChanceOfZeroNeverRolls() {
        Chances none = new Chances(true, 0f, 0f, 0f, 0f);
        Verdict v = BoreCheck.judge(bore("C4 P S .", OWNER, CAST_IRON_SAFE, CAST_IRON_PER_BARREL, none, false));
        assertEquals(Reason.NONE, v.reason(), "CBC skips a roll whose chance is 0");
        assertEquals(0, v.burst());

        assertEquals(Reason.OBSTRUCTED, BoreCheck.judge(bore("C4 S S .", OWNER, 2, 2.0, none, false)).reason());
    }

    @Test
    void loadedMeansAShellAndAChargeThatCanBurn() {
        assertTrue(BoreCheck.loaded(bore("C4 S", OWNER, 2, 2.0, Chances.STOCK, false)));
        assertFalse(BoreCheck.loaded(bore("C0 S", OWNER, 2, 2.0, Chances.STOCK, false)), "a spent cartridge is no charge");
        assertFalse(BoreCheck.loaded(bore("C4", OWNER, 2, 2.0, Chances.STOCK, false)));
        assertFalse(BoreCheck.loaded(bore("S", OWNER, 2, 2.0, Chances.STOCK, false)));
    }

    @Test
    void aHandLoadedGunFiresHalfASecondAfterItsBreechShuts() {
        BoreCheck.Watch w = new BoreCheck.Watch();
        read(w, "C0 . . .", true, 0);
        read(w, "S . . .", true, 3);
        read(w, "C4 S . .", true, 8);
        read(w, "C4 S . .", true, 12);
        assertFalse(w.settled(12));
        read(w, "C4 S . .", false, 14);
        assertFalse(w.settled(14));
        assertFalse(w.settled(14 + BoreCheck.SETTLE - 1));
        assertTrue(w.settled(14 + BoreCheck.SETTLE), "half a second after the breech shut on the whole load");

        w.reset();
        assertFalse(w.settled(100));
        assertTrue(read(w, "C0 . . .", false, 25), "a changed load clears the dry latch");
        assertFalse(w.settled(100));
    }

    @Test
    void aLoadChangedWhileSettlingStartsTheHalfSecondAgain() {
        BoreCheck.Watch w = new BoreCheck.Watch();
        read(w, "C4 S . .", false, 0);
        assertFalse(read(w, "C4 S . .", false, 5), "the same load read again is not a change");
        read(w, "C4 P S", false, 6);
        assertFalse(w.settled(20), "an unsafe load is never settled");
        read(w, "C4 S . .", false, 7);
        assertFalse(w.settled(7 + BoreCheck.SETTLE - 1));
        assertTrue(w.settled(7 + BoreCheck.SETTLE));

        read(w, "C4 S . .", true, 30);
        read(w, "C4 S . .", false, 31);
        assertFalse(w.settled(31 + BoreCheck.SETTLE - 1));
    }

    @Test
    void aLoadNeverFireableIsNeverSettled() {
        BoreCheck.Watch w = new BoreCheck.Watch();
        assertFalse(w.settled(Long.MAX_VALUE));
        read(w, "S . . .", false, 0);
        assertFalse(w.settled(Long.MAX_VALUE));
    }

    @Test
    void theSignatureFollowsTheLoadAndTheBreech() {
        int loaded = BoreCheck.signature(bore("C4 S . .", OWNER, 2, 2.0, Chances.STOCK, false));
        assertEquals(loaded, BoreCheck.signature(bore("C4 S . .", OWNER, 2, 2.0, Chances.STOCK, false)));
        assertNotEquals(loaded, BoreCheck.signature(bore("C4 S . .", OWNER, 2, 2.0, Chances.STOCK, true)));
        assertNotEquals(loaded, BoreCheck.signature(bore("C3 S . .", OWNER, 2, 2.0, Chances.STOCK, false)));
        assertNotEquals(loaded, BoreCheck.signature(bore("C0 . . .", OWNER, 2, 2.0, Chances.STOCK, false)));
    }

    private static boolean read(BoreCheck.Watch w, String spec, boolean breechOpen, long now) {
        Bore b = bore(spec, OWNER, CAST_IRON_SAFE, CAST_IRON_PER_BARREL, Chances.STOCK, breechOpen);
        return w.observe(b, BoreCheck.judge(b), now);
    }
}
