package com.createstats.statsmk3.page;

import java.util.List;

import com.createstats.blockentity.SourceConfig;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceNamesTest {
    private static SourceConfig source(String id, String type, int x, int y, int z) {
        return new SourceConfig(id, new BlockPos(x, y, z), type);
    }

    @Test
    void aLoneMachineIsCalledWhatItIs() {
        final SourceConfig only = source("stressometer_1", "Stressometer", -1284, 71, 903);
        assertFalse(SourceNames.ambiguous(List.of(only), only));
        assertEquals("Stressometer", SourceNames.label(only, false, false));
        assertEquals("Stressometer", SourceNames.label(only, true, false));
    }

    @Test
    void aSecondOfTheSameTypeEarnsACoordinate() {
        final SourceConfig a = source("stressometer_1", "Stressometer", -1284, 71, 903);
        final SourceConfig b = source("stressometer_2", "Stressometer", 12, 64, -7);
        final SourceConfig other = source("tank_1", "Fluid Tank", 3, 64, 3);
        final List<SourceConfig> all = List.of(a, b, other);

        assertTrue(SourceNames.ambiguous(all, a));
        assertTrue(SourceNames.ambiguous(all, b));

        assertFalse(SourceNames.ambiguous(all, other));
        assertEquals("Fluid Tank", SourceNames.label(other, false, SourceNames.ambiguous(all, other)));

        assertEquals("Stressometer -1284, 71, 903", SourceNames.label(a, false, true));
        assertEquals("Stressometer 12, 64, -7", SourceNames.label(b, false, true));
    }

    @Test
    void theTypeIsDroppedWhereTheContextCarriesIt() {
        final SourceConfig a = source("stressometer_1", "Stressometer", -1284, 71, 903);
        assertEquals("-1284, 71, 903", SourceNames.label(a, true, true));
    }

    @Test
    void aTypedNameIsLeftExactlyAlone() {
        final SourceConfig a = source("stressometer_1", "Stressometer", -1284, 71, 903);
        a.setUserLabel("Kiln line");
        assertTrue(SourceNames.named(a));
        assertEquals("Kiln line", SourceNames.label(a, false, true));
        assertEquals("Kiln line", SourceNames.label(a, true, true));
        assertEquals("Kiln line", SourceNames.label(a, false, false));
    }

    @Test
    void clearingANameFallsBackToTheGeneratedOne() {
        final SourceConfig a = source("stressometer_1", "Stressometer", 8, 70, 8);
        a.setUserLabel("Kiln line");
        a.setUserLabel(null);
        assertFalse(SourceNames.named(a));
        assertEquals("Stressometer 8, 70, 8", SourceNames.label(a, false, true));
    }
}
