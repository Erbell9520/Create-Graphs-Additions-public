package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeSegmentCapTest {
    private static HomeSegment.Device device(HomeAppliance.Category kind, int x) {
        final BlockPos at = new BlockPos(x, 64, 0);
        return new HomeSegment.Device(at, List.of(at), kind, Component.literal(kind + " " + x), false,
                HomeSegment.State.ON, 0, List.of(), 1, List.of(), 63, -1, null);
    }

    @Test
    @DisplayName("200 lamp runs and an oven and a washer: both appliances stay, the farthest lamps go")
    void appliancesSurvive() {
        final List<HomeSegment.Device> rows = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            rows.add(device(HomeAppliance.Category.LIGHTING, i));
        }
        final HomeSegment.Device oven = device(HomeAppliance.Category.KITCHEN, 500);
        final HomeSegment.Device washer = device(HomeAppliance.Category.LAUNDRY, 600);
        rows.add(oven);
        rows.add(washer);
        final List<HomeSegment.Device> kept = HomeSegment.cap(rows, 128, BlockPos.ZERO);
        assertEquals(128, kept.size());
        assertTrue(kept.contains(oven) && kept.contains(washer), "the appliances are kept");
        assertTrue(kept.contains(rows.get(0)), "the nearest lamp is kept");
        assertTrue(!kept.contains(rows.get(199)), "the farthest lamp is the one dropped");
        assertEquals(oven, kept.get(126), "what is kept keeps its order");
    }

    @Test
    @DisplayName("under the cap nothing changes")
    void underTheCap() {
        final List<HomeSegment.Device> rows = List.of(device(HomeAppliance.Category.LIGHTING, 1),
                device(HomeAppliance.Category.KITCHEN, 2));
        assertEquals(rows, HomeSegment.cap(rows, 128, BlockPos.ZERO));
    }
}
