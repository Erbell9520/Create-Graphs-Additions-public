package com.createstats.renderer;

import com.createstats.blockentity.SparkCrownBE;
import com.createstats.chunkloader.client.CrownLightSink;
import com.createstats.chunkloader.client.CrownMatterSink;
import com.createstats.chunkloader.client.CrownRenderTypes;
import com.createstats.chunkloader.core.CrownState;
import com.createstats.chunkloader.core.CrownStuds;
import com.createstats.chunkloader.look.CrownAnchors;
import com.createstats.chunkloader.look.CrownFx;
import com.createstats.chunkloader.look.CrownMesh;
import com.createstats.chunkloader.look.CrownMotion;
import com.createstats.chunkloader.look.CrownPose;
import com.createstats.holomk3.render.HoloRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// decides nothing, draws CrownState
@OnlyIn(Dist.CLIENT)
public class SparkCrownRenderer implements BlockEntityRenderer<SparkCrownBE> {
    public static final double NEAR = 28.0;

    private static final Map<SparkCrownBE, CrownMotion> MOTIONS = new WeakHashMap<>();

    private final CrownMesh mesh = new CrownMesh();
    private final CrownFx fx = new CrownFx();
    private final CrownAnchors anchors = new CrownAnchors();
    private final CrownPose pose = new CrownPose();
    private final CrownMatterSink matter = new CrownMatterSink();
    private final CrownLightSink light = new CrownLightSink();
    private final int[] layers = new int[3];
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Vector3f eye = new Vector3f();
    private final Vector3f scratch = new Vector3f();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public SparkCrownRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SparkCrownBE be, float partial, PoseStack ps, MultiBufferSource buffers, int packedLight,
                       int overlay) {
        final Level level = be.getLevel();
        if (level == null) {
            return;
        }
        final CrownState state = be.crown();
        final CrownMotion motion = MOTIONS.computeIfAbsent(be, k -> new CrownMotion());

        motion.advanceTo(com.createstats.client.ponder.SceneClock.now(level), state);
        motion.pose(partial, pose);

        final BlockPos origin = be.getBlockPos();
        final Direction front = be.facing();
        final int turns = CrownStuds.turnsFor(front.getName());
        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        final Vec3 cam = camera.getPosition();
        final double dx = cam.x - (origin.getX() + 0.5);
        final double dy = cam.y - (origin.getY() + 1.5);
        final double dz = cam.z - (origin.getZ() + 0.5);
        final int lod = dx * dx + dy * dy + dz * dz > NEAR * NEAR ? CrownMesh.LOD_FAR : CrownMesh.LOD_FULL;

        drawWires(be, front, ps, buffers, packedLight);

        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-90.0f * turns));
        ps.translate(-0.5, 0.0, -0.5);
        ps.scale(1f / 16f, 1f / 16f, 1f / 16f);

        layers[0] = packedLight;
        layers[1] = LevelRenderer.getLightColor(level, cursor.set(origin.getX(), origin.getY() + 1, origin.getZ()));
        layers[2] = LevelRenderer.getLightColor(level, cursor.set(origin.getX(), origin.getY() + 2, origin.getZ()));
        matter.beginWorld(buffers.getBuffer(CrownRenderTypes.MATTER), ps.last(), turns, layers);
        mesh.build(pose, anchors, matter, lod);
        matter.end();

        toCrown(camera.getLeftVector(), turns, right).negate();
        toCrown(camera.getUpVector(), turns, up);
        toCrown(scratch.set((float) dx, (float) (cam.y - origin.getY()), (float) dz), turns, eye);
        eye.mul(16f).add(8f, 0f, 8f);
        floor(level, origin, turns);
        light.begin(buffers.getBuffer(CrownRenderTypes.FLOOR), ps.last().pose(), CrownLightSink.MODE_FLOOR, right,
                up, eye, false);
        fx.emit(pose, anchors, light, lod);
        light.begin(buffers.getBuffer(HoloRenderTypes.ADDITIVE), ps.last().pose(), CrownLightSink.MODE_LIGHT, right,
                up, eye, motion.advanced());
        fx.emit(pose, anchors, light, lod);
        light.end();
        ps.popPose();

        if (motion.advanced()) {
            sparks(level, origin, turns);
        }
    }

    private static Vector3f toCrown(Vector3f world, int turns, Vector3f out) {
        float x = world.x;
        float z = world.z;
        for (int t = 0; t < turns; t++) {
            final float nx = z;
            z = -x;
            x = nx;
        }
        return out.set(x, world.y, z);
    }

    private void floor(Level level, BlockPos origin, int turns) {
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                int wx = i;
                int wz = j;
                for (int t = 0; t < turns; t++) {
                    final int nx = -wz;
                    wz = wx;
                    wx = nx;
                }
                cursor.set(origin.getX() + wx, origin.getY(), origin.getZ() + wz);
                final BlockState in = level.getBlockState(cursor);
                boolean ok = !in.isSolidRender(level, cursor);
                if (ok) {
                    cursor.move(Direction.DOWN);
                    ok = level.getBlockState(cursor).isFaceSturdy(level, cursor, Direction.UP);
                }
                light.floor[(i + 2) * 5 + (j + 2)] = ok;
            }
        }
    }

    private void sparks(Level level, BlockPos origin, int turns) {
        for (int i = 0; i < light.sparkCount; i++) {
            float x = (float) (light.sparks[i * 3] / 16.0 - 0.5);
            final double y = light.sparks[i * 3 + 1] / 16.0;
            float z = (float) (light.sparks[i * 3 + 2] / 16.0 - 0.5);
            for (int t = 0; t < turns; t++) {
                final float nx = -z;
                z = x;
                x = nx;
            }
            for (int k = 0; k < 2; k++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, origin.getX() + 0.5 + x, origin.getY() + y,
                        origin.getZ() + 0.5 + z, (level.random.nextDouble() - 0.5) * 0.5,
                        0.05 + level.random.nextDouble() * 0.3, (level.random.nextDouble() - 0.5) * 0.5);
            }
        }
    }

    private static void drawWires(SparkCrownBE be, Direction front, PoseStack ps, MultiBufferSource buffers,
                                  int light) {
        final List<BlockPos> links = be.links();
        if (links.isEmpty() || be.getLevel() == null) {
            return;
        }
        final int turns = CrownStuds.turnsFor(front.getName());
        final BlockPos origin = be.getBlockPos();
        final com.mojang.blaze3d.vertex.VertexConsumer vc = PowerConnectorRenderer.wireBuffer(buffers);
        final org.joml.Matrix4f mat = ps.last().pose();
        for (BlockPos link : links) {
            final int stud = be.studOf(link);
            final double[] from = stud >= 0 ? CrownStuds.attachPoint(turns, stud) : CrownStuds.bayCentre(turns);
            final Vec3 to = PowerConnectorRenderer.attachPoint(be.getLevel(), link, origin);
            PowerConnectorRenderer.drawCable(vc, mat, be.getLevel(), origin, link,
                    (float) from[0], (float) from[1], (float) from[2],
                    (float) (link.getX() - origin.getX() + to.x),
                    (float) (link.getY() - origin.getY() + to.y),
                    (float) (link.getZ() - origin.getZ() + to.z),
                    light, be.tierOf(link));
        }
    }

    @Override
    public AABB getRenderBoundingBox(SparkCrownBE be) {
        final BlockPos p = be.getBlockPos();
        final AABB crown = new AABB(p.getX() - 2.5, p.getY() - 0.1, p.getZ() - 2.5, p.getX() + 3.5, p.getY() + 3.1,
                p.getZ() + 3.5);
        if (be.links().isEmpty()) {
            return crown;
        }
        final PowerConnectorRenderer.WireBounds bounds = new PowerConnectorRenderer.WireBounds(p);
        for (BlockPos link : be.links()) {
            bounds.include(link);
        }
        return crown.minmax(bounds.build());
    }

    @Override
    public boolean shouldRenderOffScreen(SparkCrownBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(160);
    }
}
