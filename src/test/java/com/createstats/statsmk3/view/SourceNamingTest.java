package com.createstats.statsmk3.view;

import java.util.List;

import com.createstats.blockentity.SourceConfig;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceNamingTest {
    private static StudioContext.Source source(String id, String type, int x, int y, int z) {
        final StudioContext.Source s = new StudioContext.Source(id);
        s.config = new SourceConfig(id, new BlockPos(x, y, z), type);
        return s;
    }

    @Test
    void aLinkedMachineIsCalledWhatItIsAndNotItsHandle() {
        final StudioContext.Source only = source("stressometer_1", "Stressometer", -1284, 71, 903);
        StudioContext.name(List.of(only));
        assertEquals("Stressometer", only.displayName());
    }

    @Test
    void aTypedNameWins() {
        final StudioContext.Source a = source("stressometer_1", "Stressometer", -1284, 71, 903);
        a.config.setUserLabel("Kiln line");
        StudioContext.name(List.of(a));
        assertEquals("Kiln line", a.displayName());
    }

    @Test
    void aSecondOfATypeEarnsACoordinateAndItsSiblingDoesToo() {
        final StudioContext.Source a = source("stressometer_1", "Stressometer", -1284, 71, 903);
        final StudioContext.Source b = source("stressometer_2", "Stressometer", 12, 64, -7);
        final StudioContext.Source tank = source("tank_1", "Fluid Tank", 3, 64, 3);
        StudioContext.name(List.of(a, b, tank));

        assertEquals("Stressometer -1284, 71, 903", a.displayName());
        assertEquals("Stressometer 12, 64, -7", b.displayName());

        assertEquals("Fluid Tank", tank.displayName());
    }

    @Test
    void anUnregisteredSourceFallsBackToAReadableHandle() {
        final StudioContext.Source loose = new StudioContext.Source("boiler_hall_2");
        StudioContext.name(List.of(loose));
        assertEquals("Boiler hall 2", loose.displayName());
    }
}
