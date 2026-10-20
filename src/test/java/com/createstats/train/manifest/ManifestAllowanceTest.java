package com.createstats.train.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import com.createstats.train.manifest.ManifestAllowance.Demand;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ManifestAllowanceTest {
    private static final FreightManifestResourceKey IRON =
        new FreightManifestResourceKey(ManifestCargoKind.ITEM, ResourceLocation.withDefaultNamespace("iron_ingot"));
    private static final FreightManifestResourceKey GOLD =
        new FreightManifestResourceKey(ManifestCargoKind.ITEM, ResourceLocation.withDefaultNamespace("gold_ingot"));

    private static ManifestUnloadTracker.StationVisitState freshVisit() {
        return ManifestUnloadTracker.getOrCreate(UUID.randomUUID(), UUID.randomUUID());
    }

    private static ManifestAllowance unload(List<Demand> demands, ManifestUnloadTracker.StationVisitState visit) {
        return ManifestAllowance.fromDemands(demands, ManifestOperation.UNLOAD, visit);
    }

    @Test
    void aDirectionNoLineAsksForIsLeftAlone() {
        ManifestAllowance load = ManifestAllowance.fromDemands(List.of(), ManifestOperation.LOAD, null);

        assertFalse(load.isRestricted(),
            "a manifest with only unload lines must not start blocking the yard from loading");
        assertEquals(ManifestAllowance.UNRESTRICTED, load.allowanceFor(IRON));
    }

    @Test
    void aGovernedDirectionRefusesCargoNoLineAsksFor() {
        ManifestAllowance allowance = unload(List.of(new Demand(IRON, 64, 0)), null);

        assertTrue(allowance.isRestricted());
        assertEquals(64, allowance.allowanceFor(IRON));
        assertEquals(0, allowance.allowanceFor(GOLD));
    }

    @Test
    void deliveredProgressComesOffTheAllowance() {
        assertEquals(24, unload(List.of(new Demand(IRON, 64, 40)), null).allowanceFor(IRON));
    }

    @Test
    void aFulfilledOrderAuthorisesNothingFurther() {
        ManifestAllowance allowance = unload(List.of(new Demand(IRON, 64, 64)), freshVisit());

        assertTrue(allowance.isRestricted(),
            "a station whose whole manifest is complete must still refuse cargo, not fall back to vanilla");
        assertEquals(0, allowance.allowanceFor(IRON));
    }

    @Test
    void whatAlreadyMovedThisVisitCapsAnOrderNothingIsCrediting() {
        ManifestUnloadTracker.StationVisitState visit = freshVisit();
        visit.record(IRON, ManifestOperation.UNLOAD, 50);

        assertEquals(14, unload(List.of(new Demand(IRON, 64, 0)), visit).allowanceFor(IRON));
    }

    @Test
    void theTwoCeilingsDoNotStack() {
        ManifestUnloadTracker.StationVisitState visit = freshVisit();
        visit.record(IRON, ManifestOperation.UNLOAD, 30);

        assertEquals(34, unload(List.of(new Demand(IRON, 64, 30)), visit).allowanceFor(IRON),
            "30 delivered and 30 moved is one 30, not 60");
    }

    @Test
    void movementInTheOtherDirectionIsNotCharged() {
        ManifestUnloadTracker.StationVisitState visit = freshVisit();
        visit.record(IRON, ManifestOperation.LOAD, 40);

        assertEquals(64, unload(List.of(new Demand(IRON, 64, 0)), visit).allowanceFor(IRON),
            "loading iron onto the train must not eat the allowance for unloading it");
    }

    @Test
    void twoLinesForTheSameCargoSumIntoOneAllowance() {
        ManifestAllowance allowance = ManifestAllowance.fromDemands(
            List.of(new Demand(IRON, 64, 64), new Demand(IRON, 32, 0)), ManifestOperation.LOAD, null);

        assertEquals(32, allowance.allowanceFor(IRON));
    }

    @Test
    void spendingBooksATransferAgainstTheCapture() {
        ManifestAllowance allowance = unload(List.of(new Demand(IRON, 64, 0)), null);

        allowance.spend(IRON, 20);
        assertEquals(44, allowance.allowanceFor(IRON));
        allowance.spend(IRON, 100);
        assertEquals(0, allowance.allowanceFor(IRON), "an allowance never reports a negative remainder");
    }

    @Test
    void spendingOnAnUnrestrictedDirectionIsANoOp() {
        ManifestAllowance load = ManifestAllowance.fromDemands(List.of(), ManifestOperation.LOAD, null);

        load.spend(IRON, 20);
        assertEquals(ManifestAllowance.UNRESTRICTED, load.allowanceFor(IRON));
    }

    @Test
    void overDeliveryDoesNotHandBackAnAllowance() {
        ManifestUnloadTracker.StationVisitState visit = freshVisit();
        visit.record(IRON, ManifestOperation.UNLOAD, 200);

        assertEquals(0, unload(List.of(new Demand(IRON, 64, 64)), visit).allowanceFor(IRON));
    }

    @Test
    void aVisitIsKeyedToItsTrain() {
        UUID station = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        ManifestUnloadTracker.getOrCreate(station, first).record(IRON, ManifestOperation.UNLOAD, 64);

        ManifestUnloadTracker.StationVisitState second =
            ManifestUnloadTracker.getOrCreate(station, UUID.randomUUID());

        assertEquals(0, second.getMoved(IRON, ManifestOperation.UNLOAD),
            "the next train at the berth starts its own visit rather than inheriting the last one's");
    }
}
