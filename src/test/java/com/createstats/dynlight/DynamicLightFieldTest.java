package com.createstats.dynlight;

import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicLightFieldTest {
    private static final long LIT = BlockPos.asLong(10, 70, -4);
    private static final long DARK = BlockPos.asLong(11, 70, -4);

    private final Object clientEngine = new Object();
    private final Object serverEngine = new Object();

    private void bindWithOneCell(int level) {
        DynamicLightField.bind(this.clientEngine);
        final Long2ByteOpenHashMap cells = new Long2ByteOpenHashMap();
        cells.put(LIT, (byte) level);
        DynamicLightField.publish(Long2ByteMaps.unmodifiable(cells));
    }

    @AfterEach
    void unbind() {
        DynamicLightField.bind(null);
    }

    @Test
    void anUnboundFieldChangesNothing() {
        DynamicLightField.bind(null);
        assertEquals(3, DynamicLightField.emission(this.clientEngine, LIT, 3));
        assertEquals(0, DynamicLightField.emission(null, LIT, 0), "a null engine is never the bound one");
    }

    @Test
    void onlyTheBoundEngineSeesADynamicLight() {
        this.bindWithOneCell(12);
        assertEquals(12, DynamicLightField.emission(this.clientEngine, LIT, 0));
        assertEquals(0, DynamicLightField.emission(this.serverEngine, LIT, 0),
                "the integrated server's engine must never see client light");
        assertEquals(0, DynamicLightField.emission(this.clientEngine, DARK, 0));
    }

    @Test
    void theBrighterOfTheBlockAndTheDynamicLightWins() {
        this.bindWithOneCell(12);
        assertEquals(14, DynamicLightField.emission(this.clientEngine, LIT, 14), "a torch stays a torch");
        assertEquals(12, DynamicLightField.emission(this.clientEngine, LIT, 5));
    }

    @Test
    void rebindingForgetsEveryCellOfTheOldLevel() {
        this.bindWithOneCell(9);
        final Object nextLevelsEngine = new Object();
        DynamicLightField.bind(nextLevelsEngine);
        assertEquals(0, DynamicLightField.emission(nextLevelsEngine, LIT, 0));
        assertEquals(0, DynamicLightField.emission(this.clientEngine, LIT, 0));
    }

    @Test
    void theHookIsProvenLiveOnlyByTheBoundEngineAsking() {
        this.bindWithOneCell(9);
        assertFalse(DynamicLightField.asked());
        DynamicLightField.emission(this.serverEngine, LIT, 0);
        assertFalse(DynamicLightField.asked(), "the server's engine asking proves nothing about the client's");
        DynamicLightField.emission(this.clientEngine, DARK, 0);
        assertTrue(DynamicLightField.asked());
        DynamicLightField.bind(this.clientEngine);
        assertFalse(DynamicLightField.asked(), "a new binding has to be proven again");
    }
}
