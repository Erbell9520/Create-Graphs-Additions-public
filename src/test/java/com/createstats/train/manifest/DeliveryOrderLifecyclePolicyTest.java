package com.createstats.train.manifest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DeliveryOrderLifecyclePolicyTest {
    @Test
    void completedOneShotIsAlwaysArchived() {
        assertTrue(DeliveryOrderLifecyclePolicy.isArchived(
                DeliveryOrderResetMode.ONCE, true, false));
        assertTrue(DeliveryOrderLifecyclePolicy.archivesOnCompletion(
                DeliveryOrderResetMode.ONCE));
        assertTrue(DeliveryOrderLifecyclePolicy.retiresFromActiveManifest(
                DeliveryOrderResetMode.ONCE, true));
    }

    @Test
    void incompleteOneShotRemainsActive() {
        assertFalse(DeliveryOrderLifecyclePolicy.isArchived(
                DeliveryOrderResetMode.ONCE, false, false));
        assertFalse(DeliveryOrderLifecyclePolicy.retiresFromActiveManifest(
                DeliveryOrderResetMode.ONCE, false));
    }

    private static final DeliveryOrderLifecyclePolicy.Direction LOAD_ITEMS =
            new DeliveryOrderLifecyclePolicy.Direction(ManifestOperation.LOAD, ManifestCargoKind.ITEM);
    private static final DeliveryOrderLifecyclePolicy.Direction UNLOAD_FLUIDS =
            new DeliveryOrderLifecyclePolicy.Direction(ManifestOperation.UNLOAD, ManifestCargoKind.FLUID);
    private static final DeliveryOrderLifecyclePolicy.Direction LOAD_FLUIDS =
            new DeliveryOrderLifecyclePolicy.Direction(ManifestOperation.LOAD, ManifestCargoKind.FLUID);

    @Test
    void editingAnotherDirectionKeepsAFinishedOneShotShut() {
        assertTrue(DeliveryOrderLifecyclePolicy.publishKeepsOnceGate(LOAD_ITEMS, Set.of(UNLOAD_FLUIDS)));
        assertTrue(DeliveryOrderLifecyclePolicy.publishKeepsOnceGate(LOAD_ITEMS, Set.of(LOAD_FLUIDS)),
                "same operation, other cargo kind, is a different direction");
    }

    @Test
    void aLineInTheSameDirectionTakesOverFromTheGate() {
        assertFalse(DeliveryOrderLifecyclePolicy.publishKeepsOnceGate(LOAD_ITEMS,
                Set.of(UNLOAD_FLUIDS, LOAD_ITEMS)));
    }

    @Test
    void publishingAnEmptyManifestReopensEverything() {
        assertFalse(DeliveryOrderLifecyclePolicy.publishKeepsOnceGate(LOAD_ITEMS, Set.of()));
    }

    @Test
    void recurringWorkOnlyArchivesWhenExplicitlyDismissed() {
        assertFalse(DeliveryOrderLifecyclePolicy.isArchived(
                DeliveryOrderResetMode.EVERY_ARRIVAL, true, false));
        assertTrue(DeliveryOrderLifecyclePolicy.isArchived(
                DeliveryOrderResetMode.EVERY_ARRIVAL, true, true));
    }
}
