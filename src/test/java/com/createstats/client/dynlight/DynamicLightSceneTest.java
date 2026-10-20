package com.createstats.client.dynlight;

import com.createstats.blockentity.StageLightBE;
import com.createstats.dynlight.BeamLight;
import com.createstats.dynlight.EmissionPlan;
import com.createstats.dynlight.Light;
import com.createstats.dynlight.PointLight;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicLightSceneTest {
    static final int X0 = 0;
    static final int X1 = 26;
    static final int Z0 = 0;
    static final int Z1 = 14;
    static final int FLOOR = 64;
    static final int CEILING = 70;
    static final int DIVIDER = 13;
    static final BlockPos STAGE_LIGHT = new BlockPos(6, 69, 11);
    static final Vec3 STAGE_CENTRE = Vec3.atCenterOf(STAGE_LIGHT);

    static final BlockPos LO = new BlockPos(X0 - 1, FLOOR - 1, Z0 - 1);
    static final BlockPos HI = new BlockPos(X1 + 1, CEILING + 1, Z1 + 1);

    static final Vec3 CAMERA = new Vec3(40.0, 95.0, 45.0);

    static final Vec3 FLOOR_NEAR = new Vec3(5.5, 65.0, 12.0);
    static final Vec3 FLOOR_FAR = new Vec3(10.0, 65.0, 12.5);
    static final Vec3 PILLAR_FACE = new Vec3(8.5, 67.0, 8.0);
    static final Vec3 WINDOW = new Vec3(13.0, 66.0, 4.5);

    @BeforeAll
    static void bootstrap() {
        VanillaBootstrap.ensure();
    }

    @Test
    void aHeldTorchLightsItsRoomAndOnlyTheDoorwayLetsItThrough() {
        final House house = new House(true);
        house.update(List.of(torch(5.5, 11.0)));
        assertEquals(14, house.light(5, 66, 11), "a torch's own level, in the carrier's eye cell");
        assertEquals(13, house.light(5, 65, 11), "one block down, one level less: vanilla's falloff");
        assertEquals(5, house.light(14, 66, 11), "through the doorway, nine blocks walked");
        assertEquals(0, house.light(20, 65, 4), "the far side of the wall stays dark: no light through stone");
    }

    @Test
    void aTorchCarriedAwayLeavesNothingBehind() {
        final House walked = new House(true);
        for (Vec3 at : path(new Vec3(3.5, 0, 11.0), new Vec3(20.0, 0, 11.0), new Vec3(20.0, 0, 5.5))) {
            walked.update(List.of(torch(at.x, at.z)));
        }
        assertEquals(0, walked.light(5, 65, 11), "where the walk started is dark again");
        final House fresh = new House(true);
        fresh.update(List.of(torch(20.0, 5.5)));
        assertArrayEquals(fresh.volume(), walked.volume(),
                "after forty moves the light is exactly what a fresh engine computes for the last position");
    }

    @Test
    void aStageLightPoolLandsOnThePillarAndThePillarCastsAShadow() {
        final House house = new House(true);
        house.setStageLit(true);
        house.update(List.of(stageBeam(PILLAR_FACE)));
        final Long2ByteMap cells = house.applied;
        assertEquals(1, cells.size(), "a pool this close is a single cell: its radius is under the ring's");
        final BlockPos pool = BlockPos.of(cells.keySet().iterator().nextLong());
        assertEquals(8, pool.getX(), "in the air just in front of the pillar's south face");
        assertEquals(8, pool.getZ());
        assertEquals(15, house.light(pool.getX(), pool.getY(), pool.getZ()), "full brightness inside the peak range");
        assertEquals(10, house.light(8, pool.getY(), 5),
                "behind the pillar, light has had to walk round it: five blocks, five levels");
    }

    @Test
    void aStageLightBeamPassesGlassButStoneStopsIt() {
        final House glass = new House(true);
        glass.setStageLit(true);
        glass.update(List.of(stageBeam(WINDOW)));
        final BlockPos throughGlass = centreCellPast(glass, DIVIDER);
        assertEquals(FLOOR + 1, throughGlass.getY(), "the centre of the beam lands on room B's floor");
        assertEquals(15, glass.light(throughGlass.getX(), throughGlass.getY(), throughGlass.getZ()));

        final House stone = new House(false);
        stone.setStageLit(true);
        stone.update(List.of(stageBeam(WINDOW)));
        final LongIterator it = stone.applied.keySet().iterator();
        while (it.hasNext()) {
            assertTrue(BlockPos.getX(it.nextLong()) < DIVIDER, "with no window every ray stops at the wall");
        }
        assertEquals(0, stone.light(throughGlass.getX(), throughGlass.getY(), throughGlass.getZ()),
                "and the spot the glass let it reach stays dark");
    }

    @Test
    void aServerLightPacketWipesTheLightAndTheReassertRestoresIt() {
        final House house = new House(true);
        house.update(List.of(stageBeam(FLOOR_NEAR)));
        final BlockPos pool = BlockPos.of(house.applied.keySet().iterator().nextLong());
        final byte[] lit = house.volume();
        assertEquals(15, house.light(pool.getX(), pool.getY(), pool.getZ()));

        house.engine.queueSectionData(LightLayer.BLOCK, SectionPos.of(pool), new DataLayer());
        house.engine.runLightUpdates();
        assertEquals(0, house.light(pool.getX(), pool.getY(), pool.getZ()),
                "the packet wipes it: the fault ClientPacketListenerLightMixin exists for");

        house.reassert();
        assertArrayEquals(lit, house.volume(), "re-checking the cells puts back exactly the light that was there");
    }

    @Test
    void theWholeDemoMatchesAFreshEngine() throws IOException {
        final List<Frame> frames = demo();
        final House house = new House(true);
        final JsonArray dump = new JsonArray();
        for (int i = 0; i < frames.size(); i++) {
            final Frame frame = frames.get(i);
            house.setStageLit(frame.stageOn());
            final EmissionPlan.Step step = house.update(frame.lights());
            if (i % 8 == 0 || i == frames.size() - 1) {
                final House fresh = new House(true);
                fresh.setStageLit(frame.stageOn());
                fresh.update(frame.lights());
                assertArrayEquals(fresh.volume(), house.volume(),
                        "frame " + i + ": incremental updates must never drift from a fresh computation");
            }
            dump.add(frame.toJson(house, step));
        }
        final String out = System.getenv("DYNLIGHT_FRAMES");
        if (out != null && !out.isBlank()) {
            final JsonObject scene = new JsonObject();
            scene.add("lo", ints(LO.getX(), LO.getY(), LO.getZ()));
            scene.add("hi", ints(HI.getX(), HI.getY(), HI.getZ()));
            scene.add("stageLight", ints(STAGE_LIGHT.getX(), STAGE_LIGHT.getY(), STAGE_LIGHT.getZ()));
            final JsonArray blocks = new JsonArray();
            house.blocks.long2ObjectEntrySet().forEach(e -> {
                final JsonArray b = ints(BlockPos.getX(e.getLongKey()), BlockPos.getY(e.getLongKey()),
                        BlockPos.getZ(e.getLongKey()));
                b.add(BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()).getPath());
                blocks.add(b);
            });
            scene.add("blocks", blocks);
            scene.add("frames", dump);
            final Path dir = Path.of(out);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("scene_frames.json"), scene.toString(), StandardCharsets.UTF_8);
        }
    }

    record Frame(String seg, double px, double pz, double yaw, boolean torch, boolean stageOn, Vec3 target) {
        List<Light> lights() {
            final List<Light> lights = new ArrayList<>();
            if (this.torch) {
                lights.add(DynamicLightSceneTest.torch(this.px, this.pz));
            }
            if (this.stageOn) {
                lights.add(stageBeam(this.target));
            }
            return lights;
        }

        JsonObject toJson(House house, EmissionPlan.Step step) {
            final JsonObject o = new JsonObject();
            o.addProperty("seg", this.seg);
            final JsonArray player = new JsonArray();
            player.add(this.px);
            player.add(FLOOR + 1);
            player.add(this.pz);
            player.add(this.yaw);
            o.add("player", player);
            o.addProperty("torch", this.torch);
            o.addProperty("stageOn", this.stageOn);
            if (this.stageOn) {
                final Vec3 dir = this.target.subtract(STAGE_CENTRE).normalize();
                o.add("stageDir", vec(dir));

                final BeamLight beam = stageBeam(this.target);
                final Vec3 lens = new Vec3(beam.x(), beam.y(), beam.z());
                final Vec3 end = lens.add(beam.dx() * beam.reach(), beam.dy() * beam.reach(), beam.dz() * beam.reach());
                final LightTracer.Landing landing = LightTracer.trace(house.world, lens, end,
                        BlockPos.containing(lens).asLong());
                o.add("lens", vec(lens));
                o.add("beamEnd", vec(landing == null ? end : landing.hit().getLocation()));
            }
            o.addProperty("lights", this.lights().size());
            o.addProperty("cells", house.applied.size());
            o.addProperty("changed", step.changed().length);
            o.addProperty("deferred", step.deferred());
            o.addProperty("light", Base64.getEncoder().encodeToString(house.volume()));
            return o;
        }
    }

    static List<Frame> demo() {
        final List<Frame> frames = new ArrayList<>();

        final List<Vec3> walk = path(new Vec3(3.5, 0, 11.0), new Vec3(20.0, 0, 11.0), new Vec3(20.0, 0, 5.5));
        for (int i = 0; i < walk.size(); i++) {
            final Vec3 at = walk.get(i);
            final Vec3 next = walk.get(Math.min(i + 1, walk.size() - 1));
            final double yaw = i + 1 < walk.size() ? yawOf(next.subtract(at)) : 180.0;
            frames.add(new Frame("A", at.x, at.z, yaw, true, false, FLOOR_NEAR));
        }
        final Vec3 stand = walk.get(walk.size() - 1);

        final Vec3[] keys = {FLOOR_NEAR, FLOOR_NEAR, FLOOR_FAR, PILLAR_FACE, PILLAR_FACE, WINDOW, WINDOW};
        final int[] steps = {4, 10, 10, 6, 12, 10};
        for (int k = 0; k < steps.length; k++) {
            for (int s = 0; s < steps[k]; s++) {
                final double t = smooth((s + 1) / (double) steps[k]);
                final Vec3 target = keys[k].add(keys[k + 1].subtract(keys[k]).scale(t));
                frames.add(new Frame("B", stand.x, stand.z, 90.0, false, true, target));
            }
        }

        final List<Vec3> back = path(stand, new Vec3(20.0, 0, 11.0), new Vec3(10.0, 0, 11.0));
        for (int i = 0; i < back.size(); i++) {
            final Vec3 at = back.get(i);
            final Vec3 next = back.get(Math.min(i + 1, back.size() - 1));
            final double yaw = i + 1 < back.size() ? yawOf(next.subtract(at)) : 90.0;
            frames.add(new Frame("C", at.x, at.z, yaw, true, true, WINDOW));
        }
        return frames;
    }

    static List<Vec3> path(Vec3... corners) {
        final List<Vec3> points = new ArrayList<>();
        points.add(corners[0]);
        for (int c = 1; c < corners.length; c++) {
            final Vec3 from = corners[c - 1];
            final Vec3 leg = corners[c].subtract(from);
            final int n = (int) Math.round(leg.length() / 0.5);
            for (int i = 1; i <= n; i++) {
                points.add(from.add(leg.scale(i / (double) n)));
            }
        }
        return points;
    }

    static double yawOf(Vec3 d) {
        return Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    static PointLight torch(double x, double z) {
        return new PointLight(x, FLOOR + 1 + 1.62, z, HeldItemLights.blockLevel(Items.TORCH));
    }

    static BeamLight stageBeam(Vec3 target) {
        final Vec3 dir = target.subtract(STAGE_CENTRE).normalize();
        final double[] pose = {STAGE_CENTRE.x + dir.x * 0.6, STAGE_CENTRE.y + dir.y * 0.6,
                STAGE_CENTRE.z + dir.z * 0.6, dir.x, dir.y, dir.z};
        return StageLightLights.beam(pose, StageLightBE.MAX_BRIGHTNESS, StageLightBE.PEAK_RANGE,
                StageLightBE.PEAK_RANGE, StageLightBE.MAX_RANGE, StageLightBE.DEFAULT_FOCUS,
                StageLightBE.DEFAULT_WIDTH);
    }

    static BlockPos centreCellPast(House house, int x) {
        final List<BlockPos> centre = new ArrayList<>();
        boolean any = false;
        for (Long2ByteMap.Entry e : house.applied.long2ByteEntrySet()) {
            if (BlockPos.getX(e.getLongKey()) > x) {
                any = true;
                if (e.getByteValue() == StageLightBE.MAX_BRIGHTNESS) {
                    centre.add(BlockPos.of(e.getLongKey()));
                }
            }
        }
        assertTrue(any, "some of the beam got past x " + x);
        assertEquals(1, centre.size(), "exactly one full-level cell past x " + x + ": the centre ray's");
        return centre.get(0);
    }

    static JsonArray vec(Vec3 v) {
        final JsonArray a = new JsonArray();
        a.add(v.x);
        a.add(v.y);
        a.add(v.z);
        return a;
    }

    static JsonArray ints(int... values) {
        final JsonArray a = new JsonArray();
        Arrays.stream(values).forEach(a::add);
        return a;
    }

    static final class House implements LightChunk, LightChunkGetter {
        final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
        final LevelLightEngine engine;

        Long2ByteMap applied = Long2ByteMaps.EMPTY_MAP;
        boolean stageLit;

        final BlockGetter world = new BlockGetter() {
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return House.this.blocks.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState());
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

        House(boolean glassWindow) {
            this.build(glassWindow);
            this.engine = new LevelLightEngine(this, true, false);
            for (int cx = -1; cx <= 2; cx++) {
                for (int cz = -1; cz <= 1; cz++) {
                    this.engine.setLightEnabled(new ChunkPos(cx, cz), true);
                    for (int sy = 3; sy <= 5; sy++) {
                        this.engine.updateSectionStatus(SectionPos.of(cx, sy, cz), false);
                    }
                }
            }
            this.engine.runLightUpdates();
        }

        private void build(boolean glassWindow) {
            final BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
            for (int x = X0; x <= X1; x++) {
                for (int z = Z0; z <= Z1; z++) {
                    final boolean inside = z > Z0 && z < Z1;
                    final boolean roomA = inside && x > X0 && x < DIVIDER;
                    final boolean roomB = inside && x > DIVIDER && x < X1;
                    this.put(x, FLOOR, z, roomA ? Blocks.OAK_PLANKS.defaultBlockState()
                            : roomB ? Blocks.SPRUCE_PLANKS.defaultBlockState() : stone);
                    this.put(x, CEILING, z, stone);
                    final boolean perimeter = x == X0 || x == X1 || z == Z0 || z == Z1;
                    for (int y = FLOOR + 1; y < CEILING; y++) {
                        if (perimeter) {
                            this.put(x, y, z, stone);
                        } else if (x == DIVIDER) {
                            final boolean door = z >= 10 && z <= 11 && y <= FLOOR + 2;
                            final boolean window = z >= 3 && z <= 5 && y <= FLOOR + 2;
                            if (!door) {
                                this.put(x, y, z, window && glassWindow ? Blocks.GLASS.defaultBlockState() : stone);
                            }
                        }
                    }
                }
            }
            for (int y = FLOOR + 1; y < CEILING; y++) {
                for (int z = 6; z <= 7; z++) {
                    this.put(8, y, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                }
            }
            for (int x = 17; x <= 20; x++) {
                for (int y = FLOOR + 1; y <= FLOOR + 2; y++) {
                    this.put(x, y, 1, Blocks.BOOKSHELF.defaultBlockState());
                }
            }
        }

        private void put(int x, int y, int z, BlockState state) {
            this.blocks.put(BlockPos.asLong(x, y, z), state);
        }

        EmissionPlan.Step update(List<Light> lights) {
            final Long2ByteOpenHashMap wanted = new Long2ByteOpenHashMap();
            for (Light light : lights) {
                LightTracer.resolve(this.world, light, wanted);
            }
            final EmissionPlan.Step step = EmissionPlan.plan(this.applied, wanted, CAMERA.x, CAMERA.y, CAMERA.z,
                    256, 64);
            if (!step.isIdle()) {
                this.applied = step.next();
                for (long pos : step.changed()) {
                    this.engine.checkBlock(BlockPos.of(pos));
                }
            }
            this.engine.runLightUpdates();
            return step;
        }

        void setStageLit(boolean lit) {
            if (lit != this.stageLit) {
                this.stageLit = lit;
                this.engine.checkBlock(STAGE_LIGHT);
                this.engine.runLightUpdates();
            }
        }

        void reassert() {
            final LongIterator it = this.applied.keySet().iterator();
            while (it.hasNext()) {
                this.engine.checkBlock(BlockPos.of(it.nextLong()));
            }
            this.engine.runLightUpdates();
        }

        int light(int x, int y, int z) {
            return this.engine.getLayerListener(LightLayer.BLOCK).getLightValue(new BlockPos(x, y, z));
        }

        byte[] volume() {
            final int nx = HI.getX() - LO.getX() + 1;
            final int ny = HI.getY() - LO.getY() + 1;
            final int nz = HI.getZ() - LO.getZ() + 1;
            final byte[] out = new byte[nx * ny * nz];
            int i = 0;
            for (int y = LO.getY(); y <= HI.getY(); y++) {
                for (int z = LO.getZ(); z <= HI.getZ(); z++) {
                    for (int x = LO.getX(); x <= HI.getX(); x++) {
                        out[i++] = (byte) this.light(x, y, z);
                    }
                }
            }
            return out;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            final long key = pos.asLong();
            final BlockState real = this.blocks.getOrDefault(key, Blocks.AIR.defaultBlockState());
            int emission = this.applied.get(key);
            if (this.stageLit && key == STAGE_LIGHT.asLong()) {
                emission = Math.max(emission, StageLightBE.LIT_LIGHT_LEVEL);
            }
            if (emission <= 0) {
                return real;
            }
            assertTrue(real.isAir() || (!real.canOcclude() && real.getLightBlock(this.world, pos) == 0),
                    "the stand-in for the hook covers cells that pass light like air; a light landed in " + real
                            + " at " + pos);
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
