package com.createstats.power;

import com.createstats.power.PortableFeTransfer.Input;
import com.createstats.power.PortableFeTransfer.Mode;
import com.createstats.power.PortableFeTransfer.Plan;
import com.createstats.power.PortableFeTransfer.Reason;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableFeManagedTest {
    private static final int RATE = 32_768;
    private static final long TRAIN = 4_096_000L;
    private static final long STATION = 10_000_000L;

    private static final class Load implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return Math.max(0, maxReceive);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return RATE;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private static FeGridNode node(long key, int buffer, IEnergyStorage handler) {
        return new FeGridNode() {
            @Override
            public long nodeKey() {
                return key;
            }

            @Override
            public int storedEnergy() {
                return 0;
            }

            @Override
            public void setStoredEnergy(int amount) {
            }

            @Override
            public int bufferCapacity() {
                return buffer;
            }

            @Override
            public int transferRate() {
                return RATE;
            }

            @Override
            public IEnergyStorage attachedStorage() {
                return handler;
            }
        };
    }

    private static Input charge(long stationStored, boolean wasHolding, boolean holds, boolean wrong) {
        return new Input(Mode.CHARGE, true, false, false, RATE, 0, 0L, TRAIN,
                stationStored, STATION, 50, wasHolding, holds, wrong);
    }

    private static Input draw(boolean holds, boolean wrong) {
        return new Input(Mode.DRAW, true, false, false, RATE, 0, TRAIN, TRAIN,
                0L, 0L, 50, false, holds, wrong);
    }

    @Test
    @DisplayName("KI-36: a leg's own reading holds no station, so a floor read from it can never hold")
    void legObserveHasNoStation() {
        final FeGridSolver.Result leg = FeGridSolver.observe(List.of(node(1L, 16_000, null),
                node(2L, 0, new Load())));
        assertEquals(0, leg.reserveCapacity());
        for (int floor = 10; floor <= 90; floor += 10) {
            assertFalse(PortableFeTransfer.floorHolds(false, leg.reserve(), leg.reserveCapacity(), floor));
        }
    }

    @Test
    @DisplayName("KI-36: read from the manager's stamp, a station at 40% holds a 50% floor")
    void stampedStationHoldsTheFloor() {
        final FeManagerView view = FeManagerView.leg((int) (STATION * 40 / 100), (int) STATION, false);
        final Plan plan = PortableFeTransfer.plan(charge(view.stored(), false, false, false));
        assertEquals(Reason.FLOOR_HELD, plan.reason());
        assertEquals(0, plan.gridIntake());
        final Plan healthy = PortableFeTransfer.plan(charge(STATION * 80 / 100, false, false, false));
        assertEquals(Reason.READY, healthy.reason());
        assertEquals(RATE, healthy.gridIntake());
    }

    @Test
    @DisplayName("a board above in brownout stops the train charging, and what is already in hand still lands")
    void stationHoldStopsCharging() {
        final Input in = new Input(Mode.CHARGE, true, false, false, RATE, 5_000, 0L, TRAIN,
                STATION, STATION, 50, false, true, false);
        final Plan plan = PortableFeTransfer.plan(in);
        assertEquals(Reason.STATION_HELD, plan.reason());
        assertEquals(0, plan.gridIntake());
        assertEquals(5_000, plan.trainDelivery(), "FE already out of the circuit must not be stranded");
    }

    @Test
    @DisplayName("a charging hold means nothing to a train that is feeding the station")
    void stationHoldDoesNotStopDrawing() {
        final Plan plan = PortableFeTransfer.plan(draw(true, false));
        assertEquals(Reason.READY, plan.reason());
        assertEquals(RATE, plan.trainPull());
    }

    @Test
    @DisplayName("KI-39: charging on a controller input, or drawing on an output, names the mistake and moves nothing")
    void wrongTerminalIsNamed() {
        final Plan c = PortableFeTransfer.plan(charge(STATION, false, false, true));
        assertEquals(Reason.WRONG_TERMINAL, c.reason());
        assertEquals(0, c.gridIntake());
        final Plan d = PortableFeTransfer.plan(draw(false, true));
        assertEquals(Reason.WRONG_TERMINAL, d.reason());
        assertEquals(0, d.trainPull(), "a train on a terminal nothing reads from must not be emptied");
    }

    @Test
    @DisplayName("the ordinary-circuit constructor means no manager: neither hold nor wrong terminal")
    void ordinaryInputHasNoManager() {
        final Input in = new Input(Mode.CHARGE, true, false, false, RATE, 0, 0L, TRAIN, 0L, 0L, 50, false);
        assertFalse(in.stationHolds() || in.wrongTerminal());
        assertEquals(Reason.READY, PortableFeTransfer.plan(in).reason());
    }

    @Test
    @DisplayName("saved reasons keep their ordinals: new values only ever go on the end")
    void reasonOrdinalsAreStable() {
        final Reason[] saved = {Reason.READY, Reason.SWITCHED_OFF, Reason.DISCONNECTED, Reason.NO_STORAGE,
                Reason.LOCKED, Reason.FLOOR_HELD, Reason.TRAIN_FULL, Reason.TRAIN_EMPTY};
        for (int i = 0; i < saved.length; i++) {
            assertEquals(i, saved[i].ordinal());
        }
    }

    @Test
    @DisplayName("one manager stamping a circuit twice is one station, not two; the flags are unions")
    void mergeDoesNotDoubleCount() {
        final FeManagerView out = FeManagerView.output(3_000, 10_000, false);
        final FeManagerView twice = out.merge(out);
        assertEquals(3_000, twice.stored());
        assertEquals(10_000, twice.capacity());

        final FeManagerView both = FeManagerView.input(3_000, 10_000).merge(out);
        assertTrue(both.servesLoads() && both.takesSources());

        final FeManagerView held = out.merge(FeManagerView.leg(0, 0, true));
        assertTrue(held.holdsCharging());
        assertEquals(10_000, held.capacity(), "the larger station is the one kept");

        final FeManagerView input = FeManagerView.input(1, 2);
        assertFalse(input.servesLoads());
        assertTrue(input.takesSources());
        assertFalse(FeManagerView.output(1, 2, false).takesSources());
    }
}
