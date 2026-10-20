package com.createstats.client.dynlight;

import com.createstats.CreateStats;
import com.createstats.dynlight.BeamCone;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vehiclelight.HailSequence;
import com.createstats.vehiclelight.HeadlightState;
import com.createstats.vehiclelight.LampShow;
import com.createstats.vehiclelight.VehicleLightBE;
import com.createstats.vehiclelight.VehicleLightBeams;
import com.createstats.vehiclelight.VehicleLightBlock;
import com.createstats.vehiclelight.VehicleLightGlow;
import com.createstats.vehiclelight.VehicleLightKind;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// re-draw the faces in the cone with light added. reads as lit, not as a sticker
@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class DrawnBeams {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final int LEASE_TICKS = ClientDynamicLights.LEASE_TICKS;

    static final int MAX_FACES_PER_LAMP = 800;

    static final int SUBDIVISIONS = 4;

    static final double SUBDIVIDE_NEAR = 14.0;
    static final double SUBDIVIDE_FAR = 30.0;

    static final double GATHER_MARGIN = 1.0;

    static final double REBUILD_MOVE = 0.75;
    static final double REBUILD_TURN_DEG = 3.0;

    static final int REBUILD_TICKS = 20;

    static final int MAX_SCAN_CELLS = 60_000;

    static final double SHADOW_CLEARANCE = 0.02;

    static final double MIN_LIGHT = 0.004;

    static final int SHADERPACK_POLL_TICKS = 20;

    private static final double CELL_HALF_DIAGONAL = Math.sqrt(3.0) / 2.0;
    private static final double COS_REBUILD_TURN = Math.cos(Math.toRadians(REBUILD_TURN_DEG));
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final RandomSource RANDOM = RandomSource.create();

    private static final Map<VehicleLightBE, Feed> FEEDS = new IdentityHashMap<>();
    private static final Map<VehicleLightBE, Gather> GATHERS = new IdentityHashMap<>();
    private static long tick;
    private static boolean failed;
    private static boolean shaderPack;
    @Nullable
    private static ClientLevel boundLevel;
    private static int lastLamps;
    private static int lastQuads;

    private static final int GRID = (SUBDIVISIONS + 1) * (SUBDIVISIONS + 1);
    private static final float[] GRID_X = new float[GRID];
    private static final float[] GRID_Y = new float[GRID];
    private static final float[] GRID_Z = new float[GRID];
    private static final float[] GRID_U = new float[GRID];
    private static final float[] GRID_V = new float[GRID];
    private static final double[] GRID_I = new double[GRID];

    private DrawnBeams() {
    }

    private record Feed(VehicleLightBE be, @Nullable Object craft, long until) {
    }

    static void publish(VehicleLightBE be, @Nullable Object craft) {
        if (be != null) {
            FEEDS.put(be, new Feed(be, craft, tick + LEASE_TICKS));
        }
    }

    static boolean isActive() {
        return !failed && !shaderPack && ClientDynamicLights.settings().drawnBeams();
    }

    static String status() {
        if (failed) {
            return "drawn beams FAILED (see log), block light only";
        }
        if (shaderPack) {
            return "drawn beams off: shaderpack";
        }
        if (!ClientDynamicLights.settings().drawnBeams()) {
            return "drawn beams off in config";
        }
        return String.format(Locale.ROOT, "drawn beams: %d lamps, %d quads", lastLamps, lastQuads);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tick++;
        final ClientLevel level = Minecraft.getInstance().level;
        if (level != boundLevel) {
            boundLevel = level;
            FEEDS.clear();
            GATHERS.clear();
        }
        if (tick % SHADERPACK_POLL_TICKS == 0) {
            shaderPack = Iris.shaderPackOn();
        }
        if (!FEEDS.isEmpty()) {
            FEEDS.values().removeIf(feed -> feed.until() < tick || feed.be().isRemoved());
            GATHERS.keySet().retainAll(FEEDS.keySet());
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return;
        }
        if (FEEDS.isEmpty() || !isActive()) {
            lastLamps = 0;
            lastQuads = 0;
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        try {
            draw(mc, level, event);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            GATHERS.clear();
            LOGGER.error("[Create Stats] The drawn headlight pass threw and is switched off until restart; "
                    + "headlights are block light alone from here", e);
        }
    }

    private static void draw(Minecraft mc, ClientLevel level, RenderLevelStageEvent event) {
        final float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        final Vec3 cam = event.getCamera().getPosition();
        final Frustum frustum = event.getFrustum();
        final long gameTime = level.getGameTime();
        final double skyDarken = level.getSkyDarken(partialTick);
        final double fogStart = RenderSystem.getShaderFogStart();
        final double fogEnd = RenderSystem.getShaderFogEnd();
        final boolean cylinderFog = RenderSystem.getShaderFogShape() == FogShape.CYLINDER;
        int lamps = 0;
        int quads = 0;

        final Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        try {
            final MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            final RenderType type = Type.TYPE;
            try {
                final VertexConsumer vc = buffers.getBuffer(type);
                for (Feed feed : FEEDS.values()) {
                    final int drawn = drawLamp(level, feed, vc, partialTick, gameTime, cam, frustum, skyDarken,
                            fogStart, fogEnd, cylinderFog);
                    if (drawn >= 0) {
                        lamps++;
                        quads += drawn;
                    }
                }
            } finally {
                buffers.endBatch(type);
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
        lastLamps = lamps;
        lastQuads = quads;
    }

    private static int drawLamp(ClientLevel level, Feed feed, VertexConsumer vc, float partialTick, long gameTime,
                                Vec3 cam, @Nullable Frustum frustum, double skyDarken, double fogStart,
                                double fogEnd, boolean cylinderFog) {
        final VehicleLightBE be = feed.be();
        if (be.isRemoved() || !(be.getBlockState().getBlock() instanceof VehicleLightBlock block)) {
            return -1;
        }
        final BlockState state = be.getBlockState();
        final VehicleLightKind kind = block.kind();
        final VehicleLightGlow glow = state.getValue(VehicleLightBlock.GLOW);
        final Direction facing = state.getValue(VehicleLightBlock.FACING);
        if (kind != VehicleLightKind.WHITE || facing.getStepY() != 0) {
            return -1;
        }
        final HailSequence.Hail hail = be.hailing();
        final HeadlightState head = be.headlight();
        final boolean cut = hail != null && head.isCut(hail.start());
        final LampShow.Lens shown = LampShow.lens(kind, glow, gameTime, partialTick, hail, cut);
        if (!shown.lit()) {
            return -1;
        }
        final VehicleLightGlow step = shown.shown() == VehicleLightGlow.BRIGHT
                ? VehicleLightGlow.BRIGHT : VehicleLightGlow.DIM;
        final BeamCone cone = VehicleLightBeams.drawn(step);

        final BlockPos pos = be.getBlockPos();
        final double localX = pos.getX() + 0.5 + facing.getStepX() * VehicleLightBeams.LENS_OFFSET;
        final double localY = pos.getY() + 0.5 + facing.getStepY() * VehicleLightBeams.LENS_OFFSET;
        final double localZ = pos.getZ() + 0.5 + facing.getStepZ() * VehicleLightBeams.LENS_OFFSET;
        double lx = localX;
        double ly = localY;
        double lz = localZ;
        Quaterniond bodyToWorld = null;
        if (feed.craft() != null) {
            final Vector3d world = SableBridge.renderLocalToWorld(feed.craft(), new Vector3d(localX, localY, localZ),
                    partialTick);
            if (world == null || !Double.isFinite(world.x) || !Double.isFinite(world.y)
                    || !Double.isFinite(world.z)) {
                return -1;
            }
            lx = world.x;
            ly = world.y;
            lz = world.z;
            bodyToWorld = SableBridge.renderOrientation(feed.craft(), partialTick);
        }
        final double down = head.downAt(partialTick);
        final double left = head.leftAt(partialTick);
        final double[] aim = VehicleLightBeams.aim(facing.getStepX(), facing.getStepY(), facing.getStepZ(),
                Double.isFinite(down) ? down : VehicleLightBeams.restingTip(step), Double.isFinite(left) ? left : 0.0);
        final Vector3d dir = new Vector3d(aim[0], aim[1], aim[2]);
        if (bodyToWorld != null) {
            bodyToWorld.transform(dir);
        }
        if (!(dir.lengthSquared() > 1.0e-6)) {
            return -1;
        }
        dir.normalize();
        if (frustum != null && !frustum.isVisible(coneBox(lx, ly, lz, dir.x, dir.y, dir.z, cone))) {
            return -1;
        }

        Gather gather = GATHERS.get(be);
        if (gather == null || gather.stale(lx, ly, lz, dir.x, dir.y, dir.z, step, tick)) {
            gather = Gather.build(level, lx, ly, lz, dir.x, dir.y, dir.z, cone, step, tick);
            GATHERS.put(be, gather);
        }
        final BeamCone.Aimed aimed = cone.aim(lx, ly, lz, dir.x, dir.y, dir.z);
        return gather.draw(vc, aimed, cone, shown.level() * cone.gain(), cam, skyDarken, fogStart, fogEnd,
                cylinderFog);
    }

    static AABB coneBox(double lx, double ly, double lz, double ax, double ay, double az, BeamCone cone) {
        final double reach = cone.reach();
        final double radius = reach * Math.tan(Math.toRadians(cone.outerDeg()));
        final double fx = lx + ax * reach;
        final double fy = ly + ay * reach;
        final double fz = lz + az * reach;
        final double ex = radius * Math.sqrt(Math.max(0.0, 1.0 - ax * ax));
        final double ey = radius * Math.sqrt(Math.max(0.0, 1.0 - ay * ay));
        final double ez = radius * Math.sqrt(Math.max(0.0, 1.0 - az * az));
        return new AABB(Math.min(lx, fx - ex), Math.min(ly, fy - ey), Math.min(lz, fz - ez),
                Math.max(lx, fx + ex), Math.max(ly, fy + ey), Math.max(lz, fz + ez));
    }

    private record Candidate(long pos, @Nullable Direction side, double distanceSq) {
    }

    static final class Gather {
        private final double lx;
        private final double ly;
        private final double lz;
        private final double ax;
        private final double ay;
        private final double az;
        private final VehicleLightGlow step;
        private final long builtTick;
        private final List<Quad> quads;

        private Gather(double lx, double ly, double lz, double ax, double ay, double az, VehicleLightGlow step,
                       long builtTick, List<Quad> quads) {
            this.lx = lx;
            this.ly = ly;
            this.lz = lz;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.step = step;
            this.builtTick = builtTick;
            this.quads = quads;
        }

        boolean stale(double lx, double ly, double lz, double ax, double ay, double az, VehicleLightGlow step,
                      long tick) {
            if (tick == this.builtTick) {
                return false;
            }
            if (step != this.step) {
                return true;
            }
            final double dx = lx - this.lx;
            final double dy = ly - this.ly;
            final double dz = lz - this.lz;
            if (dx * dx + dy * dy + dz * dz > REBUILD_MOVE * REBUILD_MOVE) {
                return true;
            }
            if (ax * this.ax + ay * this.ay + az * this.az < COS_REBUILD_TURN) {
                return true;
            }
            return tick - this.builtTick >= REBUILD_TICKS;
        }

        static Gather build(ClientLevel level, double lx, double ly, double lz, double ax, double ay, double az,
                            BeamCone cone, VehicleLightGlow step, long tick) {
            final Minecraft mc = Minecraft.getInstance();
            final double reach = cone.reach();
            final double tanOuter = Math.tan(Math.toRadians(cone.outerDeg()));
            final double farRadius = reach * tanOuter + GATHER_MARGIN;
            final double fx = lx + ax * reach;
            final double fy = ly + ay * reach;
            final double fz = lz + az * reach;
            final double ex = farRadius * Math.sqrt(Math.max(0.0, 1.0 - ax * ax));
            final double ey = farRadius * Math.sqrt(Math.max(0.0, 1.0 - ay * ay));
            final double ez = farRadius * Math.sqrt(Math.max(0.0, 1.0 - az * az));
            final int x0 = Mth.floor(Math.min(lx, fx - ex) - GATHER_MARGIN);
            final int x1 = Mth.floor(Math.max(lx, fx + ex) + GATHER_MARGIN);
            final int y0 = Math.max(level.getMinBuildHeight(), Mth.floor(Math.min(ly, fy - ey) - GATHER_MARGIN));
            final int y1 = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(Math.max(ly, fy + ey) + GATHER_MARGIN));
            final int z0 = Mth.floor(Math.min(lz, fz - ez) - GATHER_MARGIN);
            final int z1 = Mth.floor(Math.max(lz, fz + ez) + GATHER_MARGIN);

            final List<Candidate> candidates = new ArrayList<>();
            final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
            int scanned = 0;
            scan:
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        if (++scanned > MAX_SCAN_CELLS) {
                            break scan;
                        }
                        final double cx = x + 0.5;
                        final double cy = y + 0.5;
                        final double cz = z + 0.5;
                        final double vx = cx - lx;
                        final double vy = cy - ly;
                        final double vz = cz - lz;
                        final double along = vx * ax + vy * ay + vz * az;
                        if (along < -1.0 || along > reach + 1.0) {
                            continue;
                        }
                        final double perpSq = vx * vx + vy * vy + vz * vz - along * along;
                        final double limit = Math.max(0.0, along) * tanOuter + GATHER_MARGIN + CELL_HALF_DIAGONAL;
                        if (perpSq > limit * limit) {
                            continue;
                        }
                        final BlockState state = level.getBlockState(pos.set(x, y, z));
                        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
                            continue;
                        }
                        final long key = pos.asLong();
                        for (Direction side : DIRECTIONS) {
                            final double sx = cx + side.getStepX() * 0.5;
                            final double sy = cy + side.getStepY() * 0.5;
                            final double sz = cz + side.getStepZ() * 0.5;

                            if ((lx - sx) * side.getStepX() + (ly - sy) * side.getStepY()
                                    + (lz - sz) * side.getStepZ() <= 0.0) {
                                continue;
                            }
                            if (!Block.shouldRenderFace(state, level, pos, side, neighbour.setWithOffset(pos, side))) {
                                continue;
                            }
                            final double dx = sx - lx;
                            final double dy = sy - ly;
                            final double dz = sz - lz;
                            candidates.add(new Candidate(key, side, dx * dx + dy * dy + dz * dz));
                        }
                        if (!state.canOcclude()) {
                            candidates.add(new Candidate(key, null, vx * vx + vy * vy + vz * vz));
                        }
                    }
                }
            }
            candidates.sort(Comparator.comparingDouble(Candidate::distanceSq));

            final List<Quad> quads = new ArrayList<>();
            final BlockColors colours = mc.getBlockColors();
            final Vec3 lens = new Vec3(lx, ly, lz);
            final long lensCell = BlockPos.asLong(Mth.floor(lx), Mth.floor(ly), Mth.floor(lz));
            final List<BakedQuad> baked = new ArrayList<>();
            int faces = 0;
            for (Candidate c : candidates) {
                if (faces >= MAX_FACES_PER_LAMP) {
                    break;
                }
                final BlockPos at = BlockPos.of(c.pos());
                final BlockState state = level.getBlockState(at);
                final BakedModel model = mc.getBlockRenderer().getBlockModel(state);
                final ModelData data = level.getModelData(at);
                final long seed = state.getSeed(at);
                baked.clear();
                RANDOM.setSeed(seed);
                for (RenderType layer : model.getRenderTypes(state, RANDOM, data)) {
                    if (layer == RenderType.translucent()) {
                        continue;
                    }
                    RANDOM.setSeed(seed);
                    baked.addAll(model.getQuads(state, c.side(), RANDOM, data, layer));
                }
                if (baked.isEmpty()) {
                    continue;
                }
                faces++;

                final Direction side = c.side();
                final Vec3 target = side == null ? Vec3.atCenterOf(at)
                        : new Vec3(at.getX() + 0.5 + side.getStepX() * (0.5 + SHADOW_CLEARANCE),
                        at.getY() + 0.5 + side.getStepY() * (0.5 + SHADOW_CLEARANCE),
                        at.getZ() + 0.5 + side.getStepZ() * (0.5 + SHADOW_CLEARANCE));
                if (LightTracer.trace(level, lens, target, lensCell) != null) {
                    continue;
                }
                final BlockPos open = side == null ? at : at.relative(side);
                final int blockLight = level.getBrightness(LightLayer.BLOCK, open);
                final int skyLight = level.getBrightness(LightLayer.SKY, open);
                for (BakedQuad q : baked) {
                    final int tint = q.isTinted() ? colours.getColor(state, level, at, q.getTintIndex()) : 0xFFFFFF;
                    quads.add(Quad.of(q, at, side, tint, blockLight, skyLight));
                }
            }
            return new Gather(lx, ly, lz, ax, ay, az, step, tick, quads);
        }

        int draw(VertexConsumer vc, BeamCone.Aimed aimed, BeamCone cone, double scale, Vec3 cam, double skyDarken,
                 double fogStart, double fogEnd, boolean cylinderFog) {
            if (!(scale > 0.0)) {
                return 0;
            }
            final double red = cone.red();
            final double green = cone.green();
            final double blue = cone.blue();
            int drawn = 0;
            for (Quad q : this.quads) {
                final double dx = q.cx - cam.x;
                final double dy = q.cy - cam.y;
                final double dz = q.cz - cam.z;
                final double flatSq = dx * dx + dz * dz;
                final double distSq = flatSq + dy * dy;
                final double fogDistance = cylinderFog ? Math.max(Math.sqrt(flatSq), Math.abs(dy)) : Math.sqrt(distSq);
                final double fade = BeamCone.fogFade(fogDistance, fogStart, fogEnd);
                if (fade <= 0.0) {
                    continue;
                }
                final double daylight = BeamCone.daylight(q.blockLight, q.skyLight, skyDarken);
                if (daylight <= 0.0) {
                    continue;
                }
                final double k = scale * fade * daylight;
                final int s = distSq < SUBDIVIDE_NEAR * SUBDIVIDE_NEAR ? SUBDIVISIONS
                        : distSq < SUBDIVIDE_FAR * SUBDIVIDE_FAR ? 2 : 1;
                final int n = s + 1;
                boolean any = false;
                for (int j = 0; j <= s; j++) {
                    final double t = (double) j / s;
                    for (int i = 0; i <= s; i++) {
                        final double u = (double) i / s;

                        final double a = (1.0 - u) * (1.0 - t);
                        final double b = (1.0 - u) * t;
                        final double c = u * t;
                        final double d = u * (1.0 - t);
                        final double x = a * q.p[0] + b * q.p[3] + c * q.p[6] + d * q.p[9];
                        final double y = a * q.p[1] + b * q.p[4] + c * q.p[7] + d * q.p[10];
                        final double z = a * q.p[2] + b * q.p[5] + c * q.p[8] + d * q.p[11];
                        final double light = BeamCone.expose(aimed.at(x, y, z, q.nx, q.ny, q.nz) * k);
                        final int g = j * n + i;
                        GRID_X[g] = (float) (x - cam.x);
                        GRID_Y[g] = (float) (y - cam.y);
                        GRID_Z[g] = (float) (z - cam.z);
                        GRID_U[g] = (float) (a * q.uv[0] + b * q.uv[2] + c * q.uv[4] + d * q.uv[6]);
                        GRID_V[g] = (float) (a * q.uv[1] + b * q.uv[3] + c * q.uv[5] + d * q.uv[7]);
                        GRID_I[g] = light;
                        any |= light > MIN_LIGHT;
                    }
                }
                if (!any) {
                    continue;
                }
                for (int j = 0; j < s; j++) {
                    for (int i = 0; i < s; i++) {
                        vertex(vc, q, j * n + i, red, green, blue);
                        vertex(vc, q, (j + 1) * n + i, red, green, blue);
                        vertex(vc, q, (j + 1) * n + i + 1, red, green, blue);
                        vertex(vc, q, j * n + i + 1, red, green, blue);
                        drawn++;
                    }
                }
            }
            return drawn;
        }

        private static void vertex(VertexConsumer vc, Quad q, int g, double red, double green, double blue) {
            final double light = GRID_I[g];
            final int r = (int) Math.round(255.0 * Math.min(1.0, red * q.tintR * light));
            final int gr = (int) Math.round(255.0 * Math.min(1.0, green * q.tintG * light));
            final int b = (int) Math.round(255.0 * Math.min(1.0, blue * q.tintB * light));
            vc.addVertex(GRID_X[g], GRID_Y[g], GRID_Z[g]).setUv(GRID_U[g], GRID_V[g]).setColor(r, gr, b, 255);
        }
    }

    static final class Quad {
        final double[] p = new double[12];

        final float[] uv = new float[8];
        final float nx;
        final float ny;
        final float nz;
        final float tintR;
        final float tintG;
        final float tintB;
        final int blockLight;
        final int skyLight;
        final double cx;
        final double cy;
        final double cz;

        static Quad of(BakedQuad q, BlockPos at, @Nullable Direction side, int tint, int blockLight, int skyLight) {
            final float nx;
            final float ny;
            final float nz;
            final Direction facing = side != null ? side : q.isShade() ? q.getDirection() : null;
            if (facing != null) {
                nx = facing.getStepX();
                ny = facing.getStepY();
                nz = facing.getStepZ();
            } else {
                nx = Float.NaN;
                ny = Float.NaN;
                nz = Float.NaN;
            }
            final int[] v = q.getVertices();
            final double[] p = new double[12];
            final float[] uv = new float[8];
            for (int i = 0; i < 4; i++) {
                final int o = i * 8;
                p[3 * i] = at.getX() + Float.intBitsToFloat(v[o]);
                p[3 * i + 1] = at.getY() + Float.intBitsToFloat(v[o + 1]);
                p[3 * i + 2] = at.getZ() + Float.intBitsToFloat(v[o + 2]);
                uv[2 * i] = Float.intBitsToFloat(v[o + 4]);
                uv[2 * i + 1] = Float.intBitsToFloat(v[o + 5]);
            }
            return new Quad(p, uv, nx, ny, nz, tint, blockLight, skyLight);
        }

        private Quad(double[] p, float[] uv, float nx, float ny, float nz, int tint, int blockLight, int skyLight) {
            System.arraycopy(p, 0, this.p, 0, 12);
            System.arraycopy(uv, 0, this.uv, 0, 8);
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.tintR = ((tint >> 16) & 0xFF) / 255.0f;
            this.tintG = ((tint >> 8) & 0xFF) / 255.0f;
            this.tintB = (tint & 0xFF) / 255.0f;
            this.blockLight = blockLight;
            this.skyLight = skyLight;
            this.cx = (this.p[0] + this.p[3] + this.p[6] + this.p[9]) / 4.0;
            this.cy = (this.p[1] + this.p[4] + this.p[7] + this.p[10]) / 4.0;
            this.cz = (this.p[2] + this.p[5] + this.p[8] + this.p[11]) / 4.0;
        }
    }

    // crack decal polygon offset, no z fight with the terrain it redraws
    private static final class Type {
        static final RenderType TYPE = RenderType.create("create_stats_drawn_beam", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 1 << 18, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(Shards.TEXTURE_TIMES_COLOUR)
                        .setTextureState(Shards.BLOCK_ATLAS)
                        .setTransparencyState(Shards.ADD)
                        .setDepthTestState(Shards.LEQUAL)
                        .setWriteMaskState(Shards.COLOUR_ONLY)
                        .setLayeringState(Shards.OFFSET)
                        .createCompositeState(false));

        private Type() {
        }
    }

    private static final class Shards extends RenderStateShard {
        static final ShaderStateShard TEXTURE_TIMES_COLOUR = new ShaderStateShard(GameRenderer::getPositionTexColorShader);
        static final TextureStateShard BLOCK_ATLAS = RenderStateShard.BLOCK_SHEET_MIPPED;
        static final DepthTestStateShard LEQUAL = RenderStateShard.LEQUAL_DEPTH_TEST;
        static final WriteMaskStateShard COLOUR_ONLY = RenderStateShard.COLOR_WRITE;
        static final LayeringStateShard OFFSET = RenderStateShard.POLYGON_OFFSET_LAYERING;

        static final TransparencyStateShard ADD = new TransparencyStateShard("create_stats_drawn_beam_add", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });

        private Shards() {
            super("create_stats_drawn_beam_shards", () -> {
            }, () -> {
            });
        }
    }

    private static final class Iris {
        @Nullable
        private static Object api;
        @Nullable
        private static Method inUse;
        private static boolean looked;

        static boolean shaderPackOn() {
            if (!looked) {
                looked = true;
                try {
                    final Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = c.getMethod("getInstance").invoke(null);
                    inUse = c.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    api = null;
                    inUse = null;
                }
            }
            if (api == null || inUse == null) {
                return false;
            }
            try {
                return Boolean.TRUE.equals(inUse.invoke(api));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
    }
}
