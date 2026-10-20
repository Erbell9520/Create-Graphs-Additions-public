package com.createstats.power;

import com.createstats.block.GarageDoorFrame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarageDoorPowerTest {
    private static void everySize(java.util.function.IntConsumer check) {
        for (int w = GarageDoorFrame.MIN_WIDTH; w <= GarageDoorFrame.MAX_WIDTH; w++) {
            for (int h = GarageDoorFrame.MIN_HEIGHT; h <= GarageDoorFrame.MAX_DROP; h++) {
                check.accept(w * h);
            }
        }
    }

    @Test
    @DisplayName("a move costs sixty per block of curtain")
    void cycleScalesWithArea() {
        assertEquals(540, GarageDoorPower.cycleCost(9));
        assertEquals(3240, GarageDoorPower.cycleCost(54));
        everySize(area -> assertEquals(60 * area, GarageDoorPower.cycleCost(area)));
    }

    @Test
    @DisplayName("the buffer always holds three whole moves")
    void bufferIsThreeCycles() {
        everySize(area -> {
            assertEquals(3 * GarageDoorPower.cycleCost(area), GarageDoorPower.buffer(area));
            assertTrue(GarageDoorPower.canAffordCycle(GarageDoorPower.buffer(area), area));
        });
    }

    @Test
    @DisplayName("a spent cycle is back inside a second, at every size")
    void transferRefillsACycleInsideASecond() {
        everySize(area -> {
            final long moved = (long) GarageDoorPower.transferRate(area) * GarageDoorPower.REFILL_TICKS;
            assertTrue(moved >= GarageDoorPower.cycleCost(area),
                    "area " + area + " refills " + moved + " of " + GarageDoorPower.cycleCost(area));
        });
    }

    @Test
    @DisplayName("no shutter transfers slower than a Powered Door")
    void smallShutterIsNeverSlowerThanADoor() {
        everySize(area -> assertTrue(GarageDoorPower.transferRate(area) >= DoorPower.TRANSFER_RATE,
                "area " + area));
    }

    @Test
    @DisplayName("a formed shutter always draws something, and a bigger one draws more")
    void idleIsNeverZeroAndRises() {
        everySize(area -> assertTrue(GarageDoorPower.idleDraw(area) >= 1, "area " + area));
        assertEquals(1, GarageDoorPower.idleDraw(4));
        assertEquals(2, GarageDoorPower.idleDraw(9));
        assertEquals(7, GarageDoorPower.idleDraw(54));
        assertTrue(GarageDoorPower.idleDraw(54) > GarageDoorPower.idleDraw(9));
    }

    @Test
    @DisplayName("the rated draw covers refilling the buffer, not just standing there")
    void ratedDrawCoversRecharge() {
        everySize(area -> {
            assertTrue(GarageDoorPower.ratedDrawFor(0, area) > GarageDoorPower.idleDraw(area),
                    "area " + area + " did not rate its own recharge");

            assertEquals(GarageDoorPower.idleDraw(area),
                    GarageDoorPower.ratedDrawFor(GarageDoorPower.buffer(area), area),
                    "area " + area + " kept asking after it was full");
        });
    }

    @Test
    @DisplayName("an empty buffer cannot pay for a move")
    void emptyBufferRefuses() {
        everySize(area -> {
            assertFalse(GarageDoorPower.canAffordCycle(0, area));
            assertFalse(GarageDoorPower.canAffordCycle(GarageDoorPower.cycleCost(area) - 1, area));
        });
    }
}
