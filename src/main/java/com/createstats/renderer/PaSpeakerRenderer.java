package com.createstats.renderer;

import com.createstats.block.PaSpeakerBlock;
import com.createstats.blockentity.PaSpeakerBE;
import com.createstats.pa.SpeakerKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PaSpeakerRenderer implements BlockEntityRenderer<PaSpeakerBE> {
    private static final ResourceLocation HALO =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/lamp_halo.png");

    private static final float PX = 1.0f / 16.0f;

    private static final float DARK = 0.02f;

    private static final float[] LENS_SPEAKER = {12.00f, 10.80f, 6.00f, 13.00f, 10.95f, 8.00f};
    private static final float[] LENS_MEGAPHONE = {7.20f, 11.40f, 7.20f, 8.80f, 11.60f, 8.80f};
    private static final float[] VOICE_SPEAKER = {4.20f, 10.62f, 4.20f, 11.80f, 10.70f, 11.80f};
    private static final float[] VOICE_MEGAPHONE = {4.30f, 11.75f, 4.30f, 11.70f, 11.85f, 11.70f};

    private static final float CONE_CHAMFER = 1.3f;

    private static final int LAMP_GREEN = 0x54E0A8;
    private static final int LAMP_AMBER = 0xE8A13C;

    private static final int VOICE_WARM = 0xFFD9A0;

    private static final float[] HALO_INFLATE = {0.55f, 1.45f};
    private static final float[] HALO_ALPHA = {0.50f, 0.20f};

    public PaSpeakerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PaSpeakerBE be, float partialTick, PoseStack ps, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof PaSpeakerBlock)) {
            return;
        }

        renderWires(be, ps, buffers, packedLight);
        final float status = be.statusLevel(partialTick);
        final float voice = be.voiceLevel(partialTick);
        if (status <= DARK && voice <= DARK) {
            return;
        }
        final boolean horn = be.kind() == SpeakerKind.MEGAPHONE;

        ps.pushPose();
        orient(ps, state.getValue(PaSpeakerBlock.FACING));

        final VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(HALO));
        if (status > DARK) {
            lamp(vc, ps, horn ? LENS_MEGAPHONE : LENS_SPEAKER,
                    be.statusIsHealthy() ? LAMP_GREEN : LAMP_AMBER, status);
        }
        if (voice > DARK) {
            voiceGlow(vc, ps, horn ? VOICE_MEGAPHONE : VOICE_SPEAKER, voice, horn);
        }
        ps.popPose();
    }

    private static void lamp(VertexConsumer vc, PoseStack ps, float[] b, int rgb, float level) {
        box(vc, ps, b[0], b[1], b[2], b[3], b[4], b[5], argb(rgb, 255, level),
                LightTexture.FULL_BRIGHT);
        for (int shell = 0; shell < HALO_INFLATE.length; shell++) {
            final int alpha = Math.round(255.0f * HALO_ALPHA[shell] * level * level);
            if (alpha <= 2) {
                continue;
            }
            final float g = HALO_INFLATE[shell];

            box(vc, ps, b[0] - g, b[1], b[2] - g, b[3] + g, b[4] + g, b[5] + g,
                    argb(rgb, alpha, level), LightTexture.FULL_BRIGHT);
        }
    }

    private static void voiceGlow(VertexConsumer vc, PoseStack ps, float[] b, float level,
                                  boolean horn) {
        final int colour = argb(VOICE_WARM, Math.round(160.0f * level), level);
        if (horn) {
            box(vc, ps, b[0], b[1], b[2], b[3], b[4], b[5], colour, LightTexture.FULL_BRIGHT);
            return;
        }
        box(vc, ps, b[0], b[1], b[2] + CONE_CHAMFER, b[3], b[4], b[5] - CONE_CHAMFER, colour,
                LightTexture.FULL_BRIGHT);
        box(vc, ps, b[0] + CONE_CHAMFER, b[1], b[2], b[3] - CONE_CHAMFER, b[4], b[5], colour,
                LightTexture.FULL_BRIGHT);
    }

    private static int argb(int rgb, int alpha, float level) {
        final float scale = 0.30f + 0.70f * Math.max(0.0f, Math.min(1.0f, level));
        final int r = Math.round(((rgb >> 16) & 0xFF) * scale);
        final int g = Math.round(((rgb >> 8) & 0xFF) * scale);
        final int b = Math.round((rgb & 0xFF) * scale);
        return (Math.max(0, Math.min(255, alpha)) << 24) | (r << 16) | (g << 8) | b;
    }

    private static void orient(PoseStack ps, Direction facing) {
        final float[] rot = com.createstats.block.MountedPart.rotationDegrees(facing);
        ps.translate(0.5, 0.5, 0.5);
        if (rot[0] != 0.0f) {
            ps.mulPose(Axis.YP.rotationDegrees(rot[0]));
        }
        if (rot[1] != 0.0f) {
            ps.mulPose(Axis.XP.rotationDegrees(rot[1]));
        }
        ps.translate(-0.5, -0.5, -0.5);
    }

    private static void box(VertexConsumer vc, PoseStack ps, float x0, float y0, float z0,
                            float x1, float y1, float z1, int argb, int light) {
        final float ax = x0 * PX, ay = y0 * PX, az = z0 * PX;
        final float bx = x1 * PX, by = y1 * PX, bz = z1 * PX;
        face(vc, ps, argb, light, Direction.SOUTH, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
        face(vc, ps, argb, light, Direction.NORTH, bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
        face(vc, ps, argb, light, Direction.WEST, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
        face(vc, ps, argb, light, Direction.EAST, bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
        face(vc, ps, argb, light, Direction.UP, ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
        face(vc, ps, argb, light, Direction.DOWN, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
    }

    private static void face(VertexConsumer vc, PoseStack ps, int argb, int light, Direction normal,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        final PoseStack.Pose pose = ps.last();
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        final float[][] p = {{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
        final float[][] uv = {{0.0f, 1.0f}, {1.0f, 1.0f}, {1.0f, 0.0f}, {0.0f, 0.0f}};
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, p[i][0], p[i][1], p[i][2])
                    .setColor(r, g, b, a)
                    .setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, normal.getStepX(), normal.getStepY(), normal.getStepZ());
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(PaSpeakerBE be) {
        return PowerConnectorRenderer.wireBounds(be.getBlockPos(), be.links()).inflate(0.25);
    }

    private static void renderWires(PaSpeakerBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        if (be.links().isEmpty()) {
            return;
        }
        final BlockPos origin = be.getBlockPos();
        final com.mojang.blaze3d.vertex.VertexConsumer consumer = PowerConnectorRenderer.wireBuffer(buffer);
        final org.joml.Matrix4f matrix = ms.last().pose();
        for (BlockPos link : be.links()) {
            if (!PowerConnectorRenderer.drawsSpan(be.getLevel(), origin, link)) {
                continue;
            }
            final net.minecraft.world.phys.Vec3 to =
                    PowerConnectorRenderer.attachPoint(be.getLevel(), link, origin);
            PowerConnectorRenderer.drawCable(consumer, matrix, be.getLevel(), origin, link, 0.5f, 0.5f, 0.5f,
                    (float) (link.getX() - origin.getX() + to.x),
                    (float) (link.getY() - origin.getY() + to.y),
                    (float) (link.getZ() - origin.getZ() + to.z),
                    light, be.tierOf(link));
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PaSpeakerBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
    }
}
