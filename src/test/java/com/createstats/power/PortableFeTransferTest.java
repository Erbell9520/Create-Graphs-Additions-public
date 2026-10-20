package com.createstats.power;

import com.createstats.power.PortableFeTransfer.Input;
import com.createstats.power.PortableFeTransfer.Mode;
import com.createstats.power.PortableFeTransfer.Plan;
import com.createstats.power.PortableFeTransfer.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableFeTransferTest {
    private static Input charge(int rate, int buffer, long trainStored, long trainCap,
                                long baseStored, long baseCap, int floor, boolean wasHolding) {
        return new Input(Mode.CHARGE, true, false, false, rate, buffer, trainStored, trainCap,
                baseStored, baseCap, floor, wasHolding);
    }

    private static Input draw(int rate, int buffer, long trainStored, long trainCap, int floor,
                              boolean wasHolding) {
        return new Input(Mode.DRAW, true, false, false, rate, buffer, trainStored, trainCap,
                0, 0, floor, wasHolding);
    }

    @Test
    @DisplayName("the buffer is the dial: an empty buffer takes exactly one tick's rate")
    void bufferIsTheCeiling() {
        final Plan plan = PortableFeTransfer.plan(charge(8192, 0, 0, 1_000_000, 0, 0, 50, false));
        assertEquals(Reason.READY, plan.reason());
        assertEquals(8192, plan.gridIntake());
        assertEquals(8192, PortableFeTransfer.bufferCapacity(8192));
    }

    @Test
    @DisplayName("the buffer is floored above zero, so the handler never reads as a foreign network mouth")
    void bufferNeverZero() {
        assertTrue(PortableFeTransfer.bufferCapacity(0) > 0);
        assertTrue(PortableFeTransfer.bufferCapacity(-5) > 0);
    }

    @Test
    @DisplayName("a train with little room is not over-fed: waiting FE counts against its room")
    void intakeBoundedByTrainRoom() {
        final Plan plan = PortableFeTransfer.plan(charge(8192, 1000, 998_500, 1_000_000, 0, 0, 0, false));
        assertEquals(500, plan.gridIntake());
        assertEquals(1000, plan.trainDelivery(), "the buffer still delivers what the train has room for");
    }

    @Test
    @DisplayName("delivery is the smallest of buffer, rate and room")
    void deliveryIsBounded() {
        assertEquals(300, PortableFeTransfer.plan(charge(8192, 300, 0, 1_000_000, 0, 0, 0, false)).trainDelivery());
        assertEquals(40, PortableFeTransfer.plan(charge(8192, 300, 999_960, 1_000_000, 0, 0, 0, false)).trainDelivery());
    }

    @Test
    @DisplayName("a base at its floor stops feeding the train, and buffered FE still reaches it")
    void chargeFloorHolds() {
        final Plan plan = PortableFeTransfer.plan(charge(8192, 500, 0, 1_000_000, 50, 100, 50, false));
        assertEquals(Reason.FLOOR_HELD, plan.reason());
        assertEquals(0, plan.gridIntake());
        assertEquals(500, plan.trainDelivery());
        assertTrue(plan.floorHolding());
    }

    @Test
    @DisplayName("a held floor is not released until the source is five points clear of it")
    void floorHysteresis() {
        assertTrue(PortableFeTransfer.floorHolds(true, 54, 100, 50));
        assertFalse(PortableFeTransfer.floorHolds(true, 55, 100, 50));
        assertFalse(PortableFeTransfer.floorHolds(false, 51, 100, 50));
        assertTrue(PortableFeTransfer.floorHolds(false, 50, 100, 50));
    }

    @Test
    @DisplayName("a circuit with no banks has nothing to protect, so the floor never holds")
    void noReserveNoFloor() {
        final Plan plan = PortableFeTransfer.plan(charge(8192, 0, 0, 1_000_000, 0, 0, 90, true));
        assertEquals(Reason.READY, plan.reason());
        assertFalse(plan.floorHolding());
    }

    @Test
    @DisplayName("a floor of zero never holds")
    void zeroFloor() {
        assertFalse(PortableFeTransfer.floorHolds(true, 0, 100, 0));
    }

    @Test
    @DisplayName("a full train takes nothing from the circuit")
    void fullTrain() {
        final Plan plan = PortableFeTransfer.plan(charge(8192, 0, 1_000_000, 1_000_000, 0, 0, 0, false));
        assertEquals(Reason.TRAIN_FULL, plan.reason());
        assertEquals(0, plan.gridIntake());
    }

    @Test
    @DisplayName("draw mode pulls one tick's rate, never below the train's floor")
    void drawRespectsTrainFloor() {
        final Plan plenty = PortableFeTransfer.plan(draw(8192, 0, 900_000, 1_000_000, 50, false));
        assertEquals(8192, plenty.trainPull());
        final Plan near = PortableFeTransfer.plan(draw(8192, 0, 500_100, 1_000_000, 50, false));
        assertEquals(100, near.trainPull(), "only what is above the floor");
        final Plan at = PortableFeTransfer.plan(draw(8192, 0, 500_000, 1_000_000, 50, false));
        assertEquals(0, at.trainPull());
        assertEquals(Reason.FLOOR_HELD, at.reason());
    }

    @Test
    @DisplayName("draw mode never overfills its own buffer")
    void drawRespectsBuffer() {
        assertEquals(192, PortableFeTransfer.plan(draw(8192, 8000, 900_000, 1_000_000, 0, false)).trainPull());
    }

    @Test
    @DisplayName("an empty train says empty, not held")
    void emptyTrain() {
        assertEquals(Reason.TRAIN_EMPTY, PortableFeTransfer.plan(draw(8192, 0, 0, 1_000_000, 0, false)).reason());
    }

    @Test
    @DisplayName("drawn FE is offered to the circuit even after the train leaves")
    void drawnFeIsNeverStranded() {
        final Plan plan = PortableFeTransfer.plan(new Input(Mode.DRAW, false, false, false, 8192, 700,
                0, 0, 0, 0, 50, false));
        assertEquals(Reason.DISCONNECTED, plan.reason());
        assertTrue(plan.offersToGrid());
        assertEquals(0, plan.trainPull());
    }

    @Test
    @DisplayName("redstone stops everything, both directions")
    void switchedOff() {
        final Plan c = PortableFeTransfer.plan(new Input(Mode.CHARGE, true, true, false, 8192, 500,
                0, 1_000_000, 0, 0, 0, false));
        assertEquals(Reason.SWITCHED_OFF, c.reason());
        assertEquals(0, c.gridIntake());
        assertEquals(0, c.trainDelivery());
        final Plan d = PortableFeTransfer.plan(new Input(Mode.DRAW, true, true, false, 8192, 500,
                900_000, 1_000_000, 0, 0, 0, false));
        assertEquals(0, d.trainPull());
        assertFalse(d.offersToGrid());
    }

    @Test
    @DisplayName("a locked interface moves nothing in either direction")
    void locked() {
        final Plan plan = PortableFeTransfer.plan(new Input(Mode.CHARGE, true, false, true, 8192, 500,
                0, 1_000_000, 0, 0, 0, false));
        assertEquals(Reason.LOCKED, plan.reason());
        assertEquals(0, plan.gridIntake());
        assertEquals(0, plan.trainDelivery());
    }

    @Test
    @DisplayName("a contraption with no storage aboard is named as such")
    void noStorage() {
        assertEquals(Reason.NO_STORAGE,
                PortableFeTransfer.plan(charge(8192, 0, 0, 0, 0, 0, 0, false)).reason());
    }

    @Test
    @DisplayName("a disconnected charger takes nothing and delivers nothing")
    void disconnectedCharger() {
        final Plan plan = PortableFeTransfer.plan(new Input(Mode.CHARGE, false, false, false, 8192, 500,
                0, 0, 0, 0, 0, false));
        assertEquals(0, plan.gridIntake());
        assertEquals(0, plan.trainDelivery());
        assertFalse(plan.offersToGrid(), "a charger never offers its buffer back");
    }

    @Test
    @DisplayName("over many ticks a charger moves exactly its rate per tick into a train")
    void sustainedRate() {
        int buffer = 0;
        long train = 0;
        final long cap = 10_000_000L;
        for (int tick = 0; tick < 100; tick++) {
            final Plan intake = PortableFeTransfer.plan(charge(4096, buffer, train, cap, 0, 0, 0, false));
            buffer += intake.gridIntake();
            final Plan deliver = PortableFeTransfer.plan(charge(4096, buffer, train, cap, 0, 0, 0, false));
            buffer -= deliver.trainDelivery();
            train += deliver.trainDelivery();
        }
        assertEquals(409_600L, train);
    }
}
