package com.createstats.train.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StationVisitLifecycleTest {
    private static final FreightManifestResourceKey IRON =
        new FreightManifestResourceKey(ManifestCargoKind.ITEM,
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("iron_ingot"));

    private static void deliver(FreightManifestStationState state, UUID lineId, int amount, long tick) {
        state.getMutableVisitOrders().getOrCreate(lineId, tick).addDelivered(amount, amount, tick);
    }

    @Test
    void aVisitStartsWithNothingDelivered() {
        FreightManifestStationState state = new FreightManifestStationState();

        assertFalse(state.isVisitActive(UUID.randomUUID()));
        assertTrue(state.beginVisit(UUID.randomUUID(), "Ore Run", 100L));
        assertFalse(state.isVisitSnapshotTaken(), "the baseline cargo snapshot is owed, not assumed");
        assertTrue(state.getVisitOrders().getLineStates().isEmpty());
    }

    @Test
    void theSameTrainStandingStillKeepsItsFigures() {
        FreightManifestStationState state = new FreightManifestStationState();
        UUID train = UUID.randomUUID();
        UUID line = UUID.randomUUID();

        state.beginVisit(train, "Ore Run", 100L);
        deliver(state, line, 64, 101L);

        assertFalse(state.beginVisit(train, "Ore Run", 102L), "still the same visit");
        assertEquals(64, state.getVisitOrders().get(line).getDeliveredAmount());
    }

    @Test
    void aDifferentTrainStartsFromZero() {
        FreightManifestStationState state = new FreightManifestStationState();
        UUID line = UUID.randomUUID();

        state.beginVisit(UUID.randomUUID(), "Ore Run", 100L);
        deliver(state, line, 64, 101L);

        UUID second = UUID.randomUUID();
        assertTrue(state.beginVisit(second, "Coal Run", 200L));
        assertTrue(state.isVisitActive(second));
        assertTrue(state.getVisitOrders().getLineStates().isEmpty());
    }

    @Test
    void theSameTrainReturningStartsFromZero() {
        FreightManifestStationState state = new FreightManifestStationState();
        UUID train = UUID.randomUUID();
        UUID line = UUID.randomUUID();

        state.beginVisit(train, "Ore Run", 100L);
        deliver(state, line, 64, 101L);
        assertTrue(state.endVisit(120L));

        assertTrue(state.beginVisit(train, "Ore Run", 400L));
        assertTrue(state.getVisitOrders().getLineStates().isEmpty(),
            "a returning train must not inherit its own previous delivery");
    }

    @Test
    void departureLeavesTheFiguresBehindAsAReceipt() {
        FreightManifestStationState state = new FreightManifestStationState();
        UUID line = UUID.randomUUID();

        state.beginVisit(UUID.randomUUID(), "Ore Run", 100L);
        deliver(state, line, 64, 101L);
        assertTrue(state.endVisit(120L));

        assertFalse(state.isVisitActive(UUID.randomUUID()));
        assertTrue(state.hasLastVisit());
        assertEquals(64, state.getLastVisitOrders().get(line).getDeliveredAmount());
        assertEquals("Ore Run", state.getLastVisitTrainName());
    }

    @Test
    void closingAnEmptyBerthTwiceIsANoOp() {
        FreightManifestStationState state = new FreightManifestStationState();

        assertFalse(state.endVisit(100L));
        state.beginVisit(UUID.randomUUID(), "Ore Run", 100L);
        assertTrue(state.endVisit(120L));
        assertFalse(state.endVisit(121L));
    }

    @Test
    void onlyOneCallerAllocatesPerTick() {
        FreightManifestStationState state = new FreightManifestStationState();

        assertTrue(state.claimAllocationTick(500L));
        assertFalse(state.claimAllocationTick(500L));
        assertTrue(state.claimAllocationTick(501L));
    }

    @Test
    void cumulativeVisitMovementAccumulatesAndResetsWithTheVisit() {
        FreightManifestStationState state = new FreightManifestStationState();
        state.beginVisit(UUID.randomUUID(), "Ore Run", 100L);

        TrainCargoSnapshot step = new TrainCargoSnapshot();
        step.addAmount(IRON, 16);
        state.recordVisitMovement(step, new TrainCargoSnapshot());
        state.recordVisitMovement(step, new TrainCargoSnapshot());

        assertEquals(32, state.getVisitMovedToStation().getAmount(IRON));
        assertEquals(0, state.getVisitMovedToTrain().getAmount(IRON));

        state.endVisit(200L);
        assertEquals(0, state.getVisitMovedToStation().getAmount(IRON));
    }

    @Test
    void operationalHistoryKeepsTheNewestSixtyFourReceipts() {
        FreightManifestStationState state = new FreightManifestStationState();
        for (int i = 0; i < 80; i++) {
            state.addHistory(DeliveryOrderHistoryType.COMPLETED, i, "Receipt " + i, "detail");
        }

        assertEquals(64, state.getHistoryEntries().size());
        assertEquals("Receipt 79", state.getHistoryEntries().get(0).getTitle());
        assertEquals("Receipt 16", state.getHistoryEntries().get(63).getTitle());
    }
}
