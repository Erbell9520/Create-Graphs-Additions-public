package com.createstats.train.manifest;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RetiredOnceAllowanceTest {
    private static final FreightManifestResourceKey IRON = new FreightManifestResourceKey(
        ManifestCargoKind.ITEM, ResourceLocation.withDefaultNamespace("iron_ingot"));

    @Test
    void finalRetirementClosesTheOtherwiseEmptyAllowance() {
        ManifestAllowance empty = ManifestAllowance.fromDemands(List.of(), ManifestOperation.LOAD, null);
        assertEquals(ManifestAllowance.UNRESTRICTED, empty.allowanceFor(IRON));
        assertEquals(0, empty.withCompletedOnceGate(true).allowanceFor(IRON));
        assertEquals(ManifestAllowance.UNRESTRICTED, empty.withCompletedOnceGate(false).allowanceFor(IRON));
    }

    @Test
    void retiredGateDoesNotBlockRemainingOrdersInItsDirection() {
        ManifestAllowance active = ManifestAllowance.fromDemands(
            List.of(new ManifestAllowance.Demand(IRON, 64, 16)), ManifestOperation.LOAD, null);
        assertEquals(48, active.withCompletedOnceGate(true).allowanceFor(IRON));
    }
}
