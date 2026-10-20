package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class StarterPortTest {
    private static long now;

    private static StarterPort port() {
        now = 1000;
        return new StarterPort(() -> now, () -> { });
    }

    @Test
    @DisplayName("idle reads as a load, firing reads as a generator, and it is never storage or nothing")
    void roles() {
        final StarterPort p = port();
        for (boolean open : new boolean[]{true, false}) {
            p.setChargeOpen(open);
            p.setFiring(false, 0);
            assertEquals(FeNodeRole.Kind.LOAD, FeNodeRole.of(p, 65536).kind());
        }
        p.setStored(CapacitorDischarge.CAPACITY);
        p.setFiring(true, now);
        assertEquals(FeNodeRole.Kind.GENERATOR, FeNodeRole.of(p, 65536).kind());
        p.setStored(0);
        assertEquals(FeNodeRole.Kind.GENERATOR, FeNodeRole.of(p, 65536).kind(), "empty mid-burst is still a source");
        for (boolean f : new boolean[]{false, true}) {
            p.setFiring(f, now);
            final FeNodeRole.Kind k = FeNodeRole.of(p, 65536).kind();
            assertNotEquals(FeNodeRole.Kind.STORAGE, k);
            assertNotEquals(FeNodeRole.Kind.NONE, k);
        }
    }

    @Test
    @DisplayName("charging is held to the charge rate across every caller, only while open, and a simulate takes nothing")
    void chargeRate() {
        final StarterPort p = port();
        assertEquals(0, p.receiveEnergy(5000, false), "closed");
        p.setChargeOpen(true);
        final int rate = CapacitorDischarge.CHARGE_FE_PER_TICK;
        assertEquals(rate, p.receiveEnergy(rate * 5, true));
        assertEquals(0, p.stored(), "a simulate is a question");
        assertEquals(600, p.receiveEnergy(600, false));
        assertEquals(rate - 600, p.receiveEnergy(rate * 5, false), "a second grid on the other cheek shares the tick");
        assertEquals(0, p.receiveEnergy(rate * 5, false));
        now++;
        assertEquals(rate, p.receiveEnergy(rate * 5, false));
        p.setStored(CapacitorDischarge.CAPACITY - 10);
        now++;
        assertEquals(10, p.receiveEnergy(5000, false), "never past full");
    }

    @Test
    @DisplayName("a full burst into a sink that takes everything gives exactly the capacity, and ends empty")
    void fullBurst() {
        final StarterPort p = port();
        p.setStored(CapacitorDischarge.CAPACITY);
        p.setFiring(true, now);
        assertEquals(0, p.receiveEnergy(1000, false), "no charging while firing");
        long out = 0;
        for (int t = 0; t < CapacitorDischarge.BURST_TICKS; t++) {
            out += p.extractEnergy(Integer.MAX_VALUE, false);
            assertEquals(0, p.extractEnergy(Integer.MAX_VALUE, false), "one allowance per tick, however many callers");
            now++;
        }
        p.setFiring(false, 0);
        assertEquals(CapacitorDischarge.CAPACITY, out);
        assertEquals(0, p.stored());
        assertEquals(0, p.dissipatedThisBurst(), "a grid that takes everything leaves nothing to burn");
    }

    @Test
    @DisplayName("what the grid does not take burns off: every shot ends empty, and the burn is counted")
    void untakenBurnsOff() {
        final StarterPort p = port();
        p.setStored(CapacitorDischarge.CAPACITY);
        p.setFiring(true, now);
        long out = 0;
        for (int t = 0; t < CapacitorDischarge.BURST_TICKS; t++) {
            out += p.extractEnergy(8192, false);
            p.settle();
            now++;
        }
        p.setFiring(false, 0);
        assertEquals(0, p.stored(), "a shot into a thin grid still empties it");
        assertEquals(CapacitorDischarge.CAPACITY - out, p.dissipatedThisBurst(), "and every FE is accounted for");
        assertEquals(0, p.extractEnergy(8192, false), "idle gives nothing");
    }

    @Test
    @DisplayName("on no grid at all it still empties on time, tick by tick along the ramp")
    void emptiesWithNobodyListening() {
        final StarterPort p = port();
        p.setStored(CapacitorDischarge.CAPACITY);
        p.setFiring(true, now);
        for (int t = 0; t < CapacitorDischarge.BURST_TICKS; t++) {
            p.settle();
            now++;
            p.settle();
            assertEquals(CapacitorDischarge.CAPACITY - CapacitorDischarge.drainedAfter(t + 1), p.stored(),
                    "after burst tick " + t);
        }
        p.setFiring(false, 0);
        assertEquals(0, p.stored());
        assertEquals(CapacitorDischarge.CAPACITY, p.dissipatedThisBurst());
    }

    @Test
    @DisplayName("the burn never pre-empts a grid that solves late in the tick: it settles only on the next")
    void burnWaitsForTheTick() {
        final StarterPort p = port();
        p.setStored(CapacitorDischarge.CAPACITY);
        p.setFiring(true, now);
        p.settle();
        p.settle();
        final int allowance = CapacitorDischarge.allowanceAt(0);
        assertEquals(allowance, p.extractEnergy(Integer.MAX_VALUE, false), "the block ticked first; the grid still gets it all");
        assertEquals(0, p.dissipatedThisBurst());
    }

    @Test
    @DisplayName("the charge gate: a bare pusher always, a manager unless it holds or is short, a grid unless short")
    void chargeGate() {
        assertEquals(true, StarterPort.chargeGate(false, false, false, true),
                "a creative source bolted straight on has no grid to be short: it is the surplus");
        assertEquals(true, StarterPort.chargeGate(true, true, false, false), "a whole managed circuit charges it");
        assertEquals(false, StarterPort.chargeGate(true, true, true, false), "the panel is holding its charge");

        assertEquals(false, StarterPort.chargeGate(true, true, false, true),
                "never while the manager says it left something else short");
        assertEquals(true, StarterPort.chargeGate(true, false, false, false),
                "a whole grid charges it, banks feeding other things or not (the first build's bug)");
        assertEquals(false, StarterPort.chargeGate(true, false, false, true), "never while machines go short");
    }

    @Test
    @DisplayName("KI-95: a manager paying out of its refill opens the gate over the machines that refill cut")
    void refillFundedGate() {
        assertEquals(false, StarterPort.chargeGate(true, true, false, true, false),
                "unfunded: the refill's own cuts shut it, as before");
        assertEquals(true, StarterPort.chargeGate(true, true, false, true, true),
                "funded: the FE offered is the refill's, which no machine was going to get");
        assertEquals(false, StarterPort.chargeGate(true, true, true, true, true),
                "a board above in brownout still outranks every battery below it");
        assertEquals(false, StarterPort.chargeGate(true, false, false, true, true),
                "an ordinary grid has no refill, so the flag means nothing there");
        assertEquals(StarterPort.chargeGate(true, true, false, true),
                StarterPort.chargeGate(true, true, false, true, false), "the four-argument gate is the unfunded one");
    }

    @Test
    @DisplayName("KI-69: a managed circuit is short only on the manager's word, and only where it takes the burst")
    void managedShort() {
        assertEquals(false, StarterPort.managedShort(null), "no stamp yet is not short");
        assertEquals(false, StarterPort.managedShort(FeManagerView.leg(0, 0, false)),
                "a leg with nothing unfunded does not fire, whatever observe() says its demand is");
        assertEquals(true, StarterPort.managedShort(FeManagerView.leg(0, 0, false, 1)));
        assertEquals(false, StarterPort.managedShort(FeManagerView.leg(0, 0, false, 5_000).refusingSources()),
                "behind a Power Limiter the panel never draws, so a shot would only burn off");
        assertEquals(true, StarterPort.managedShort(FeManagerView.output(0, 0, false, 5_000)),
                "a controller output draws a burst even though it reads no ordinary source (KI-73)");
        assertEquals(true, StarterPort.managedShort(FeManagerView.input(0, 0, 5_000)));
    }

    @Test
    @DisplayName("KI-76: a board refilling its buffer is not a short grid, a board short of supply is")
    void feedShort() {
        assertEquals(true, StarterPort.feedShort(4_000, 0, 0, false), "no board: the grid's word");
        assertEquals(false, StarterPort.feedShort(4_000, 4_000, 0, false), "its own charging is not short");
        assertEquals(false, StarterPort.feedShort(16_384, 0, 16_384, false),
                "a panel on Insulated Wire asking its whole rating to refill a buffer, every machine served");
        assertEquals(true, StarterPort.feedShort(16_384, 0, 16_384, true), "the board says its branch is short");
        assertEquals(true, StarterPort.feedShort(20_000, 0, 16_384, false),
                "more short than the board could ever have asked: something else is starving");
        assertEquals(false, StarterPort.feedShort(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, false),
                "no overflow into a false positive");
    }

    @Test
    @DisplayName("each cheek is its own gate on one store, and both cheeks answer every role question the same")
    void faces() {
        final StarterPort p = port();
        p.setChargeOpen(StarterPort.GLAND, true);
        p.setChargeOpen(StarterPort.LUG, false);
        assertEquals(0, p.face(StarterPort.LUG).receiveEnergy(500, false));
        assertEquals(500, p.face(StarterPort.GLAND).receiveEnergy(500, false));
        assertEquals(500, p.face(StarterPort.LUG).getEnergyStored(), "one store behind both");
        for (int f : new int[]{StarterPort.GLAND, StarterPort.LUG}) {
            assertEquals(FeNodeRole.Kind.LOAD, FeNodeRole.of(p.face(f), 65536).kind());
        }
        p.setFiring(true, now);
        for (int f : new int[]{StarterPort.GLAND, StarterPort.LUG}) {
            assertEquals(FeNodeRole.Kind.GENERATOR, FeNodeRole.of(p.face(f), 65536).kind());
        }
        assertEquals(500, p.face(StarterPort.LUG).extractEnergy(Integer.MAX_VALUE, false)
                + p.face(StarterPort.GLAND).extractEnergy(Integer.MAX_VALUE, false));
    }

    @Test
    @DisplayName("its own unmet appetite is known, so a 512 FE/t generator does not read as a short grid")
    void ownDemand() {
        final StarterPort p = port();
        p.setChargeOpen(true);
        assertEquals(CapacitorDischarge.CHARGE_FE_PER_TICK, p.receiveEnergy(CapacitorDischarge.CHARGE_FE_PER_TICK * 4, true), "the solve asks what it wants");
        assertEquals(512, p.receiveEnergy(512, false), "and the generator only makes 512");
        assertEquals(CapacitorDischarge.CHARGE_FE_PER_TICK - 512, p.unservedAtLastSolve());
        now++;
        assertEquals(CapacitorDischarge.CHARGE_FE_PER_TICK - 512, p.unservedAtLastSolve(), "still the last solve's answer until the next one asks");
        p.receiveEnergy(4096, true);
        p.receiveEnergy(4096, false);
        assertEquals(0, p.unservedAtLastSolve());
        now++;
        p.receiveEnergy(4096, true);
        p.receiveEnergy(512, false);
        assertEquals(CapacitorDischarge.CHARGE_FE_PER_TICK - 512, p.unservedAtLastSolve());
        p.setChargeOpen(false);
        now++;
        p.receiveEnergy(4096, true);
        assertEquals(0, p.unservedAtLastSolve(), "a shut gate asks for nothing, and that replaces the stale figure");
    }

    @Test
    @DisplayName("the allowance is read off game time, so a grid that solves before the block ticks gets the same answer")
    void allowanceByGameTime() {
        final StarterPort p = port();
        p.setStored(CapacitorDischarge.CAPACITY);
        p.setFiring(true, now - 100);
        assertEquals(CapacitorDischarge.allowanceAt(100), p.extractEnergy(Integer.MAX_VALUE, true));
    }
}
