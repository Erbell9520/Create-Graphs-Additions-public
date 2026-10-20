package com.createstats.powertrack.client;

import com.createstats.CreateStats;
import com.createstats.powertrack.FeederPoint;
import com.createstats.powertrack.GraphRail;
import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.RailMesher;
import com.createstats.powertrack.core.SectionGraph;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// block models can't know about junctions, so one pass draws the rail
@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class PowerRailRenderer {
    public static final ResourceLocation RAIL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "block/power_track/rail_parts");

    private record Bucket(double ox, double oy, double oz, List<RailMesher.Quad> quads, AABB box) {
    }

    private static final class Mesh {
        final int fingerprint;
        final ResourceKey<Level> dimension;
        final RailLayout layout;
        final List<RailMesher.Tap> taps;

        final double[][] bounds;

        final Map<Integer, List<Bucket>> byEdge = new HashMap<>();

        final Map<Long, List<Bucket>> buckets = new HashMap<>();
        final java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        final java.util.Set<Integer> queued = new java.util.HashSet<>();
        long aroundChunk = Long.MIN_VALUE;
        int aroundRadius = -1;

        Mesh(int fingerprint, ResourceKey<Level> dimension, RailLayout layout, List<RailMesher.Tap> taps) {
            this.fingerprint = fingerprint;
            this.dimension = dimension;
            this.layout = layout;
            this.taps = taps;
            final List<RailLayout.Shape> shapes = layout.shapes();
            this.bounds = new double[shapes.size()][];
            for (int i = 0; i < shapes.size(); i++) {
                bounds[i] = shapes.get(i).bounds();
            }
        }
    }

    private static final int EDGES_PER_FRAME = 24;

    private static final int KEEP_MARGIN = 3;

    private static final Map<UUID, Mesh> MESHES = new HashMap<>();
    private static long builtAt = -1;
    private static ResourceKey<Level> builtFor;

    private PowerRailRenderer() {
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final Level level = mc.level;
        if (level == null) {
            return;
        }
        refresh(level);
        if (MESHES.isEmpty()) {
            return;
        }
        final Vec3 cam = event.getCamera().getPosition();
        final Frustum frustum = event.getFrustum();
        final int radius = Math.max(2, mc.options.getEffectiveRenderDistance());
        final int cx = (int) Math.floor(cam.x) >> 4;
        final int cz = (int) Math.floor(cam.z) >> 4;
        final TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(RAIL_TEXTURE);
        final PoseStack ps = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        final VertexConsumer vc = buffers.getBuffer(RenderType.cutoutMipped());
        final Map<Long, Integer> lights = new HashMap<>();
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        final int span = (2 * radius + 1) * (2 * radius + 1);
        int budget = EDGES_PER_FRAME;
        for (Mesh mesh : MESHES.values()) {
            if (mesh.dimension != level.dimension()) {
                continue;
            }
            budget -= keepNear(mesh, cx, cz, radius, budget);
            if (mesh.buckets.size() <= span) {
                for (Map.Entry<Long, List<Bucket>> en : mesh.buckets.entrySet()) {
                    final int bx = (int) (en.getKey() >> 32);
                    final int bz = (int) (long) en.getKey();
                    if (Math.abs(bx - cx) <= radius && Math.abs(bz - cz) <= radius && level.hasChunk(bx, bz)) {
                        drawAll(en.getValue(), ps, vc, cam, frustum, sprite, level, lights, probe);
                    }
                }
            } else {
                for (int x = cx - radius; x <= cx + radius; x++) {
                    for (int z = cz - radius; z <= cz + radius; z++) {
                        final List<Bucket> list = mesh.buckets.get(chunkKey(x, z));

                        if (list != null && level.hasChunk(x, z)) {
                            drawAll(list, ps, vc, cam, frustum, sprite, level, lights, probe);
                        }
                    }
                }
            }
        }
        buffers.endBatch(RenderType.cutoutMipped());
    }

    private static void drawAll(List<Bucket> list, PoseStack ps, VertexConsumer vc, Vec3 cam, Frustum frustum,
                                TextureAtlasSprite sprite, Level level, Map<Long, Integer> lights,
                                BlockPos.MutableBlockPos probe) {
        for (Bucket b : list) {
            if (frustum != null && !frustum.isVisible(b.box())) {
                continue;
            }
            ps.pushPose();
            ps.translate(b.ox() - cam.x, b.oy() - cam.y, b.oz() - cam.z);
            final PoseStack.Pose pose = ps.last();
            for (RailMesher.Quad q : b.quads()) {
                final long key = BlockPos.asLong(q.lightX(), q.lightY(), q.lightZ());
                Integer light = lights.get(key);
                if (light == null) {
                    light = LevelRenderer.getLightColor(level, probe.set(q.lightX(), q.lightY(), q.lightZ()));
                    lights.put(key, light);
                }
                emit(vc, pose, q, sprite, light);
            }
            ps.popPose();
        }
    }

    public static void emit(VertexConsumer vc, PoseStack.Pose pose, RailMesher.Quad q, TextureAtlasSprite sprite,
                            int light) {
        final float nx = q.nx();
        final float ny = q.ny();
        final float nz = q.nz();
        final float shade = Math.min(1f, nx * nx * 0.6f + nz * nz * 0.8f + ny * ny * (ny > 0 ? 1f : 0.5f));
        final int c = Math.round(255 * shade);
        final float[] uv = q.uv();
        final float u0 = sprite.getU(uv[0] / 16f);
        final float v0 = sprite.getV(uv[1] / 16f);
        final float u1 = sprite.getU(uv[2] / 16f);
        final float v1 = sprite.getV(uv[3] / 16f);
        final float[][] corners = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        final float[] p = q.xyz();
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, p[3 * i], p[3 * i + 1], p[3 * i + 2]).setColor(c, c, c, 255)
                    .setUv(corners[i][0], corners[i][1]).setLight(light).setNormal(pose, nx, ny, nz);
        }
    }

    private static void refresh(Level level) {
        if (builtAt == ClientRail.revision() && builtFor == level.dimension()) {
            return;
        }
        builtAt = ClientRail.revision();
        builtFor = level.dimension();
        final Map<UUID, Mesh> next = new HashMap<>();
        for (GraphRail rail : ClientRail.rails()) {
            if (!rail.hasPower()) {
                continue;
            }
            final Mesh known = MESHES.get(rail.graphId);
            if (known != null && known.fingerprint == rail.fingerprint && known.dimension == level.dimension()) {
                next.put(rail.graphId, known);
                continue;
            }

            final RailLayout layout = rail.layout(level.dimension());
            final Mesh mesh = new Mesh(rail.fingerprint, level.dimension(), layout, taps(rail, layout, level));
            if (known != null && known.dimension == level.dimension()) {
                carryOver(known, mesh);
            }
            next.put(rail.graphId, mesh);
        }
        MESHES.clear();
        MESHES.putAll(next);
    }

    private static void carryOver(Mesh from, Mesh to) {
        final Map<Long, Integer> index = new HashMap<>();
        final List<RailLayout.Shape> shapes = to.layout.shapes();
        for (int i = 0; i < shapes.size(); i++) {
            index.put(SectionGraph.key(shapes.get(i).lo(), shapes.get(i).hi()), i);
        }
        for (Map.Entry<Integer, List<Bucket>> en : from.byEdge.entrySet()) {
            final RailLayout.Shape old = from.layout.shapes().get(en.getKey());
            final Integer i = index.get(SectionGraph.key(old.lo(), old.hi()));
            if (i == null) {
                continue;
            }
            final RailLayout.Shape now = shapes.get(i);
            if (!old.sameLine(now)
                    || !RailMesher.sameRail(from.layout.sections().pieces(old.lo(), old.hi()),
                    to.layout.sections().pieces(now.lo(), now.hi()))
                    || !tapsOn(from.taps, old).equals(tapsOn(to.taps, now))) {
                continue;
            }
            to.byEdge.put(i, en.getValue());
            for (Bucket b : en.getValue()) {
                to.buckets.computeIfAbsent(chunkKey((int) Math.floor(b.ox() / 16.0), (int) Math.floor(b.oz() / 16.0)),
                        k -> new ArrayList<>()).add(b);
            }
        }
    }

    private static List<RailMesher.Tap> tapsOn(List<RailMesher.Tap> taps, RailLayout.Shape shape) {
        final List<RailMesher.Tap> out = new ArrayList<>();
        for (RailMesher.Tap t : taps) {
            if (t.lo() == shape.lo() && t.hi() == shape.hi()) {
                out.add(t);
            }
        }
        return out;
    }

    private static int keepNear(Mesh mesh, int cx, int cz, int radius, int budget) {
        final long here = chunkKey(cx, cz);
        if (here != mesh.aroundChunk || radius != mesh.aroundRadius) {
            mesh.aroundChunk = here;
            mesh.aroundRadius = radius;
            final double near = (radius + 1) * 16.0;
            final double far = (radius + KEEP_MARGIN) * 16.0;
            final double camX = cx * 16.0 + 8.0;
            final double camZ = cz * 16.0 + 8.0;
            final List<double[]> wanted = new ArrayList<>();
            for (int i = 0; i < mesh.bounds.length; i++) {
                final double[] b = mesh.bounds[i];
                final double dx = Math.max(0.0, Math.max(b[0] - camX, camX - b[2]));
                final double dz = Math.max(0.0, Math.max(b[1] - camZ, camZ - b[3]));
                final double away = Math.max(dx, dz);
                if (away <= near && !mesh.byEdge.containsKey(i) && !mesh.queued.contains(i)) {
                    wanted.add(new double[]{away, i});
                } else if (away > far && mesh.byEdge.containsKey(i)) {
                    letGo(mesh, i);
                }
            }

            wanted.sort((p, q) -> Double.compare(p[0], q[0]));
            for (double[] w : wanted) {
                mesh.queued.add((int) w[1]);
                mesh.queue.add((int) w[1]);
            }
        }
        int done = 0;
        while (done < budget && !mesh.queue.isEmpty()) {
            final int i = mesh.queue.poll();
            mesh.queued.remove(i);
            if (!mesh.byEdge.containsKey(i)) {
                meshEdge(mesh, i);
                done++;
            }
        }
        return done;
    }

    private static void letGo(Mesh mesh, int edge) {
        final List<Bucket> mine = mesh.byEdge.remove(edge);
        if (mine == null) {
            return;
        }
        for (Bucket b : mine) {
            final long key = chunkKey((int) Math.floor(b.ox() / 16.0), (int) Math.floor(b.oz() / 16.0));
            final List<Bucket> list = mesh.buckets.get(key);
            if (list != null) {
                list.removeIf(x -> x == b);
                if (list.isEmpty()) {
                    mesh.buckets.remove(key);
                }
            }
        }
    }

    private static void meshEdge(Mesh mesh, int edge) {
        final RailLayout.Shape shape = mesh.layout.shapes().get(edge);
        final List<Bucket> mine = new ArrayList<>();
        {
            final List<SectionGraph.Piece> pieces = mesh.layout.sections().pieces(shape.lo(), shape.hi());
            final double[] origin = shape.point(0);
            final List<RailMesher.Quad> quads = RailMesher.meshEdge(shape, pieces, mesh.taps, origin);

            final Map<Long, List<RailMesher.Quad>> byChunk = new HashMap<>();
            for (RailMesher.Quad q : quads) {
                final float[] p = q.xyz();
                final double mx = origin[0] + (p[0] + p[3] + p[6] + p[9]) / 4.0;
                final double mz = origin[2] + (p[2] + p[5] + p[8] + p[11]) / 4.0;
                byChunk.computeIfAbsent(chunkKey((int) Math.floor(mx) >> 4, (int) Math.floor(mz) >> 4),
                        k -> new ArrayList<>()).add(q);
            }
            for (Map.Entry<Long, List<RailMesher.Quad>> en : byChunk.entrySet()) {
                final int bx = (int) (en.getKey() >> 32);
                final int bz = (int) (long) en.getKey();
                final double ox = bx * 16.0;
                final double oz = bz * 16.0;
                final double oy = Math.floor(origin[1]);
                final List<RailMesher.Quad> rebased = new ArrayList<>(en.getValue().size());
                double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
                double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                for (RailMesher.Quad q : en.getValue()) {
                    final float[] p = q.xyz();
                    final float[] r = new float[12];
                    for (int i = 0; i < 4; i++) {
                        final double wx = origin[0] + p[3 * i];
                        final double wy = origin[1] + p[3 * i + 1];
                        final double wz = origin[2] + p[3 * i + 2];
                        r[3 * i] = (float) (wx - ox);
                        r[3 * i + 1] = (float) (wy - oy);
                        r[3 * i + 2] = (float) (wz - oz);
                        minX = Math.min(minX, wx);
                        minY = Math.min(minY, wy);
                        minZ = Math.min(minZ, wz);
                        maxX = Math.max(maxX, wx);
                        maxY = Math.max(maxY, wy);
                        maxZ = Math.max(maxZ, wz);
                    }
                    rebased.add(new RailMesher.Quad(r, q.nx(), q.ny(), q.nz(), q.uv(), q.lightX(), q.lightY(), q.lightZ()));
                }
                final Bucket bucket = new Bucket(ox, oy, oz, rebased,
                        new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.05));
                mesh.buckets.computeIfAbsent(en.getKey(), k -> new ArrayList<>()).add(bucket);
                mine.add(bucket);
            }
        }
        mesh.byEdge.put(edge, mine);
    }

    private static List<RailMesher.Tap> taps(GraphRail rail, RailLayout layout, Level level) {
        final List<RailMesher.Tap> out = new ArrayList<>();
        for (GraphRail.At<FeederPoint> f : rail.feeders(level.dimension())) {
            final RailLayout.Shape shape = layout.shape(f.a(), f.b());
            final BlockPos pos = f.point().blockEntityPos;
            if (shape == null || pos == null) {
                continue;
            }
            final double at = f.a() < f.b() ? f.position() : shape.length() - f.position();
            final double[] p = shape.point(at);
            final double[] t = shape.tangent(at);
            final double side = t[1] * (pos.getX() + 0.5 - p[0]) - t[0] * (pos.getZ() + 0.5 - p[2]);
            out.add(new RailMesher.Tap(shape.lo(), shape.hi(), at, side >= 0 ? 1 : -1));
        }
        return out;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MESHES.clear();
        builtAt = -1;
        builtFor = null;
    }
}
