package com.createstats.client.dynlight;

import com.createstats.dynlight.BeamLight;
import com.createstats.dynlight.EmissionPlan;
import com.createstats.dynlight.Light;
import com.createstats.vehiclelight.HailSequence;
import com.createstats.vehiclelight.VehicleLightBeams;
import com.createstats.vehiclelight.VehicleLightGlow;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// the lamp's block is in the plot, the road is just air. that's the case
class VehicleLightSceneTest {
    static final int ROAD = 64;

    static final BlockPos LAMP = new BlockPos(0, 66, 40);

    @BeforeAll
    static void bootstrap() {
        VanillaBootstrap.ensure();
    }

    static BeamLight headlight(BlockPos lamp, VehicleLightGlow glow, boolean dipped) {
        final double[] aim = dipped ? VehicleLightBeams.aim(0, 0, -1, glow) : new double[] {0, 0, -1};
        final double[] pose = {lamp.getX() + 0.5, lamp.getY() + 0.5, lamp.getZ() + 0.5 - VehicleLightBeams.LENS_OFFSET,
                aim[0], aim[1], aim[2]};
        return VehicleLightBeams.headlight(pose, glow);
    }

    static int brightestOnRoad(Road road, int zFrom, int zTo) {
        int best = 0;
        for (int z = zFrom; z <= zTo; z++) {
            best = Math.max(best, road.light(0, ROAD + 1, z));
        }
        return best;
    }

    @Test
    void theDippedBeamLightsTheRoadAheadWhereALevelBeamLightsNothing() {
        final Road dipped = new Road();
        dipped.show(List.of(headlight(LAMP, VehicleLightGlow.DIM, true)));
        final Road level = new Road();
        level.show(List.of(headlight(LAMP, VehicleLightGlow.DIM, false)));

        assertTrue(brightestOnRoad(dipped, 28, 33) >= 10, "the dipped beam's pool is on the road ahead: "
                + brightestOnRoad(dipped, 28, 33));
        assertTrue(brightestOnRoad(level, 28, 33) <= 2, "a level beam leaves the road in front dark: "
                + brightestOnRoad(level, 28, 33));

        assertEquals(0, brightestOnRoad(dipped, 0, 9), "and the dipped beam stops short of the far road");
        assertTrue(brightestOnRoad(dipped, 13, 18) > 0, "but reaches the road it gave up at 20 blocks: "
                + brightestOnRoad(dipped, 13, 18));
        assertTrue(dipped.light(0, ROAD + 1, 47) <= 3, "with nothing much behind the lamp");
    }

    @Test
    void theFullBeamLightsTheNearRoadAndReachesFarDownIt() {
        final Road dipped = new Road();
        dipped.show(List.of(headlight(LAMP, VehicleLightGlow.DIM, true)));
        final Road full = new Road();
        full.show(List.of(headlight(LAMP, VehicleLightGlow.BRIGHT, true)));
        final Road level = new Road();
        level.show(List.of(headlight(LAMP, VehicleLightGlow.BRIGHT, false)));

        final int far = brightestOnRoad(full, 16, 20);
        assertTrue(far >= 8, "the full beam lights the road twenty blocks out: " + far);
        assertTrue(far > brightestOnRoad(dipped, 16, 20), "where the dipped beam does not reach");
        assertTrue(brightestOnRoad(full, 28, 33) >= 10, "and still the road in front: " + brightestOnRoad(full, 28, 33));

        assertTrue(brightestOnRoad(level, 28, 33) <= 4, "which a level full beam leaves dark: "
                + brightestOnRoad(level, 28, 33));
    }

    static BeamLight aimed(BlockPos lamp, VehicleLightGlow glow, double down, double left) {
        final double[] aim = VehicleLightBeams.aim(0, 0, -1, down, left);
        final double[] pose = {lamp.getX() + 0.5, lamp.getY() + 0.5, lamp.getZ() + 0.5 - VehicleLightBeams.LENS_OFFSET,
                aim[0], aim[1], aim[2]};
        return VehicleLightBeams.headlight(pose, glow);
    }

    static int brightestZ(Road road, int zFrom, int zTo) {
        int best = -1;
        int at = zTo;
        for (int z = zTo; z >= zFrom; z--) {
            final int v = road.light(0, ROAD + 1, z);
            if (v > best) {
                best = v;
                at = z;
            }
        }
        return at;
    }

    static double height(BlockPos lamp) {
        return lamp.getY() + 0.5 - (ROAD + 1);
    }

    @Test
    void levelledHeadlightsLandTheirPoolTheSameDistanceAheadFromAnyHeight() {
        final BlockPos[] lamps = {new BlockPos(0, ROAD + 1, 40), LAMP, new BlockPos(0, ROAD + 4, 40)};
        final int[] levelled = new int[lamps.length];
        final int[] fixed = new int[lamps.length];
        final int[] pool = new int[lamps.length];
        for (int i = 0; i < lamps.length; i++) {
            final Road road = new Road();
            road.show(List.of(aimed(lamps[i], VehicleLightGlow.DIM,
                    VehicleLightBeams.tipFor(height(lamps[i]), VehicleLightGlow.DIM), 0.0)));
            levelled[i] = brightestZ(road, 0, 36);
            pool[i] = brightestOnRoad(road, levelled[i], levelled[i]);
            final Road unlevelled = new Road();
            unlevelled.show(List.of(headlight(lamps[i], VehicleLightGlow.DIM, true)));
            fixed[i] = brightestZ(unlevelled, 0, 36);
        }
        System.out.println("[VehicleLightSceneTest] dipped pool z, levelled " + java.util.Arrays.toString(levelled)
                + " at " + java.util.Arrays.toString(pool) + ", fixed tip " + java.util.Arrays.toString(fixed));
        for (int i = 0; i < lamps.length; i++) {
            assertTrue(levelled[i] >= 27 && levelled[i] <= 33, "levelled from " + height(lamps[i])
                    + " the pool lands about ten blocks ahead of the lamp at z 40: z " + levelled[i]);
            assertTrue(pool[i] >= 9, "and it is a real pool there: " + pool[i]);
        }
        assertTrue(Math.abs(levelled[0] - levelled[2]) <= 1, "the same distance from half a block and three and a half");

        assertTrue(fixed[0] - fixed[2] >= 6, "the fixed tip spreads the same lamps' pools far apart: z " + fixed[0]
                + " and " + fixed[2]);
    }

    @Test
    void theHeightProbeFindsTheTopOfTheRoadASlabAndWaterAndNothingOverAGap() {
        final Road road = new Road();
        road.blocks.put(BlockPos.asLong(3, ROAD + 1, 10), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        road.blocks.put(BlockPos.asLong(5, ROAD + 1, 10), Blocks.WATER.defaultBlockState());
        final double lensY = ROAD + 2.5;
        assertEquals(1.5, VehicleLightLights.groundBelow(road.world, 0.5, lensY, 10.5), 1e-9, "a block and a half over stone");
        assertEquals(1.0, VehicleLightLights.groundBelow(road.world, 3.5, lensY, 10.5), 1e-9, "a slab's top is half a block up");
        final double water = VehicleLightLights.groundBelow(road.world, 5.5, lensY, 10.5);
        assertTrue(water > 0.5 && water < 1.0, "a pond's surface, not its bed: " + water);
        assertTrue(Double.isNaN(VehicleLightLights.groundBelow(road.world, 40.5, lensY, 10.5)),
                "off the edge of the road nothing is within reach: no height, and the leveller keeps its last");
    }

    @Test
    void theGreetingsReferenceRunMovesThePoolDownUpLeftAndRight() {
        final double rest = VehicleLightBeams.DIPPED_DOWN;
        final Road middle = new Road();
        middle.show(List.of(aimed(LAMP, VehicleLightGlow.DIM, rest, 0.0)));
        final Road down = new Road();
        down.show(List.of(aimed(LAMP, VehicleLightGlow.DIM, rest + HailSequence.RUN_DOWN, 0.0)));
        final Road up = new Road();
        up.show(List.of(aimed(LAMP, VehicleLightGlow.DIM, rest - HailSequence.RUN_UP, 0.0)));
        final int zMiddle = brightestZ(middle, 0, 37);
        final int zDown = brightestZ(down, 0, 37);
        final Road left = new Road();
        left.show(List.of(aimed(LAMP, VehicleLightGlow.DIM, rest, HailSequence.RUN_SIDE)));
        final Road right = new Road();
        right.show(List.of(aimed(LAMP, VehicleLightGlow.DIM, rest, -HailSequence.RUN_SIDE)));
        final int farMiddle = lightSum(middle, 19, 24);
        final int farUp = lightSum(up, 19, 24);
        final int farDown = lightSum(down, 19, 24);
        final int nearMiddle = lightSum(middle, 35, 38);
        final int nearDown = lightSum(down, 35, 38);
        System.out.println("[VehicleLightSceneTest] run: pool z down " + zDown + ", rest " + zMiddle + "; far road light up "
                + farUp + ", rest " + farMiddle + ", down " + farDown + "; near road light rest " + nearMiddle + ", down "
                + nearDown + "; pool x left " + brightestX(left, zMiddle) + ", right " + brightestX(right, zMiddle));

        assertTrue(zDown > zMiddle, "down: the pool comes nearer the lamp (z " + zDown + " against " + zMiddle + ")");

        assertTrue(farUp > farMiddle, "up sends light further down the road: " + farUp + " against " + farMiddle);
        assertTrue(nearDown > nearMiddle, "down brings light nearer the lamp: " + nearDown + " against " + nearMiddle);

        assertTrue(brightestX(left, zMiddle) < 0, "left swings the pool west: x " + brightestX(left, zMiddle));
        assertTrue(brightestX(right, zMiddle) > 0, "right swings it east: x " + brightestX(right, zMiddle));
    }

    static int lightSum(Road road, int zFrom, int zTo) {
        int sum = 0;
        for (int z = zFrom; z <= zTo; z++) {
            sum += road.light(0, ROAD + 1, z);
        }
        return sum;
    }

    static int brightestX(Road road, int z) {
        int best = -1;
        int at = 0;
        for (int x = -8; x <= 8; x++) {
            final int v = road.light(x, ROAD + 1, z);
            if (v > best || (v == best && Math.abs(x) < Math.abs(at))) {
                best = v;
                at = x;
            }
        }
        return at;
    }

    @Test
    void aVehicleDrivingDownTheRoadLeavesNoLightBehind() {
        final Road driven = new Road();
        for (int step = 0; step <= 10; step++) {
            driven.show(List.of(headlight(LAMP.north(step), VehicleLightGlow.BRIGHT, true)));
        }
        final Road fresh = new Road();
        fresh.show(List.of(headlight(LAMP.north(10), VehicleLightGlow.BRIGHT, true)));
        for (int z = -10; z <= 50; z++) {
            for (int y = ROAD + 1; y <= ROAD + 4; y++) {
                for (int x = -4; x <= 4; x++) {
                    assertEquals(fresh.light(x, y, z), driven.light(x, y, z),
                            "after ten blocks of driving the road is lit exactly as a fresh engine lights it, at "
                                    + x + " " + y + " " + z);
                }
            }
        }
    }

    static final class Road implements LightChunk, LightChunkGetter {
        final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
        final LevelLightEngine engine;
        Long2ByteMap applied = Long2ByteMaps.EMPTY_MAP;

        final BlockGetter world = new BlockGetter() {
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return Road.this.blocks.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState());
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return this.getBlockState(pos).getFluidState();
            }

            @Override
            public int getHeight() {
                return 384;
            }

            @Override
            public int getMinBuildHeight() {
                return -64;
            }
        };

        Road() {
            for (int x = -12; x <= 12; x++) {
                for (int z = -16; z <= 56; z++) {
                    this.blocks.put(BlockPos.asLong(x, ROAD, z), Blocks.STONE.defaultBlockState());
                }
            }
            this.engine = new LevelLightEngine(this, true, false);
            for (int cx = -2; cx <= 1; cx++) {
                for (int cz = -2; cz <= 4; cz++) {
                    this.engine.setLightEnabled(new ChunkPos(cx, cz), true);
                    for (int sy = 3; sy <= 5; sy++) {
                        this.engine.updateSectionStatus(SectionPos.of(cx, sy, cz), false);
                    }
                }
            }
            this.engine.runLightUpdates();
        }

        void show(List<Light> lights) {
            final Long2ByteOpenHashMap wanted = new Long2ByteOpenHashMap();
            for (Light light : lights) {
                LightTracer.resolve(this.world, light, wanted);
            }
            final EmissionPlan.Step step = EmissionPlan.plan(this.applied, wanted, 0.0, 80.0, 40.0, 256, 256);
            if (!step.isIdle()) {
                this.applied = step.next();
                for (long pos : step.changed()) {
                    this.engine.checkBlock(BlockPos.of(pos));
                }
            }
            this.engine.runLightUpdates();
        }

        int light(int x, int y, int z) {
            return this.engine.getLayerListener(LightLayer.BLOCK).getLightValue(new BlockPos(x, y, z));
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            final long key = pos.asLong();
            final BlockState real = this.blocks.getOrDefault(key, Blocks.AIR.defaultBlockState());
            final int emission = this.applied.get(key);
            if (emission <= 0) {
                return real;
            }
            assertTrue(real.isAir(), "a headlight's light landed inside " + real + " at " + pos);
            return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, emission);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }

        @Override
        public void findBlockLightSources(BiConsumer<BlockPos, BlockState> output) {
        }

        @Override
        public ChunkSkyLightSources getSkyLightSources() {
            return null;
        }

        @Override
        public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
            return this;
        }

        @Override
        public BlockGetter getLevel() {
            return this;
        }
    }
}
