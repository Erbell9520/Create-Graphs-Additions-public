package com.createstats.repeller;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnIndexTest {
    private static SpawnIndex.Entry<String> entry(long id, String type, RepellerShape shape, double x, double y,
                                                  double z, double r) {
        return new SpawnIndex.Entry<>(id, type, shape, x, y, z, r, new AtomicLong());
    }

    @Test
    void anEmptyLevelAnswersAtOnce() {
        final SpawnIndex<String> idx = SpawnIndex.empty();
        assertTrue(idx.isEmpty());
        assertNull(idx.find("minecraft:zombie", 0, 64, 0));
        assertSame(SpawnIndex.empty(), SpawnIndex.of(List.of()), "no repellers shares the one empty index");
    }

    @Test
    void onlyTheTunedMobInsideTheVolumeIsFound() {
        final SpawnIndex<String> idx = SpawnIndex.of(List.of(
                entry(1, "minecraft:zombie", RepellerShape.SPHERE, 0.5, 64.5, 0.5, 16)));
        assertNotNull(idx.find("minecraft:zombie", 10.5, 64.0, 5.5));
        assertNull(idx.find("minecraft:skeleton", 10.5, 64.0, 5.5), "a repeller holds one frequency");
        assertNull(idx.find("minecraft:zombie", 20.5, 64.0, 0.5), "outside the sphere");
        assertNull(idx.find("minecraft:zombie", 0.5, 84.0, 0.5), "above the sphere");
        assertTrue(idx.mentions("minecraft:zombie"));
        assertFalse(idx.mentions("minecraft:phantom"));
    }

    @Test
    void aPhantomColumnReachesTheSkyAndNotPastItsEdge() {
        final SpawnIndex<String> idx = SpawnIndex.of(List.of(
                entry(2, "minecraft:phantom", RepellerShape.COLUMN, 100.5, 70.5, -40.5, 24)));
        assertNotNull(idx.find("minecraft:phantom", 110.0, 104.0, -30.0), "34 blocks up");
        assertNotNull(idx.find("minecraft:phantom", 100.5, 319.0, -40.5), "at the build height");
        assertNull(idx.find("minecraft:phantom", 125.5, 90.0, -40.5), "25 out of a 24 column");
    }

    @Test
    void aBigSphereIsFoundFromEveryChunkItCovers() {
        final SpawnIndex<String> idx = SpawnIndex.of(List.of(
                entry(3, "minecraft:creeper", RepellerShape.SPHERE, -200.5, 40.5, 300.5, 64)));
        int found = 0;
        for (double x = -264; x <= -137; x += 3) {
            for (double z = 237; z <= 364; z += 3) {
                final double dx = x + 200.5;
                final double dz = z - 300.5;
                final boolean inside = dx * dx + dz * dz <= 64 * 64;
                final boolean hit = idx.find("minecraft:creeper", x, 40.5, z) != null;
                assertEquals(inside, hit, "at " + x + ", " + z);
                if (hit) {
                    found++;
                }
            }
        }
        assertTrue(found > 1_000);
    }

    @Test
    void aRepellerCoveringNothingIsLeftOut() {
        final SpawnIndex<String> idx = SpawnIndex.of(List.of(
                entry(4, "minecraft:zombie", RepellerShape.SPHERE, 0.5, 64.5, 0.5, 0.0),
                entry(5, null, RepellerShape.SPHERE, 0.5, 64.5, 0.5, 16)));
        assertTrue(idx.isEmpty(), "zero radius and no station are both nothing to index");
    }

    @Test
    void aLeavingMobFollowsItsRepellerByIdWhereverItStands() {
        final SpawnIndex.Entry<String> a = entry(8, "minecraft:zombie", RepellerShape.SPHERE, 0.5, 64.5, 0.5, 16);
        final SpawnIndex<String> idx = SpawnIndex.of(List.of(a));
        assertSame(a, idx.byId(8));
        assertNull(idx.find("minecraft:zombie", 18.5, 64.5, 0.5), "two past the edge, the spawn check sees nothing");
        assertSame(a, idx.byId(8), "and the leaving mob still knows which volume it is leaving");
        assertNull(idx.byId(9));
        assertNull(SpawnIndex.<String>empty().byId(8));
        final SpawnIndex<String> shrunk = SpawnIndex.of(List.of(
                entry(8, "minecraft:zombie", RepellerShape.SPHERE, 0.5, 64.5, 0.5, 12)));
        assertEquals(12.0, shrunk.byId(8).radius(), "a new snapshot answers with the radius as it is now");
    }

    @Test
    void twoRepellersOnDifferentFrequenciesOverlapWithoutInterfering() {
        final SpawnIndex.Entry<String> a = entry(6, "minecraft:zombie", RepellerShape.SPHERE, 0.5, 64.5, 0.5, 20);
        final SpawnIndex.Entry<String> b = entry(7, "minecraft:spider", RepellerShape.SPHERE, 8.5, 64.5, 0.5, 20);
        final SpawnIndex<String> idx = SpawnIndex.of(List.of(a, b));
        assertSame(a, idx.find("minecraft:zombie", 4.5, 64.5, 0.5));
        assertSame(b, idx.find("minecraft:spider", 4.5, 64.5, 0.5));
        assertEquals(2, idx.size());
    }
}
