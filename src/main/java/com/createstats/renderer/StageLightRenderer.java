package com.createstats.renderer;

import com.createstats.block.StageLightBlock;
import com.createstats.blockentity.StageLightBE;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class StageLightRenderer implements BlockEntityRenderer<StageLightBE> {
    private static final float SMOOTH_TIME = 0.30f;

    private static final float HUB_Y = 10.0f / 16.0f;

    private static final int BEAM_SEGMENTS = 18;

    private static final float LENS_Y = 12.6f / 16.0f;

    private static final float MK2_LENS_Y = 13.5f / 16.0f;

    private static final float COG_RATIO = 3.0f;

    public StageLightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StageLightBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof StageLightBlock)) {
            return;
        }
        final Direction facing = state.getValue(StageLightBlock.FACING);

        // critically damped spring, real movers don't snap

        final float targetPan = be.getPan();
        final float targetTilt = be.getTilt();
        final long now = System.nanoTime();
        if (!be.clientAnimInit) {
            be.clientPan = targetPan;
            be.clientTilt = targetTilt;
            be.clientPanVel = 0.0f;
            be.clientTiltVel = 0.0f;
            be.clientAnimInit = true;
        }
        float dt = (now - be.clientLastNanos) / 1.0e9f;
        be.clientLastNanos = now;
        dt = Mth.clamp(dt, 0.0f, 0.1f);
        final float[] panState = smoothDamp(be.clientPan, targetPan, be.clientPanVel, SMOOTH_TIME, dt);
        be.clientPan = panState[0];
        be.clientPanVel = panState[1];
        final float[] tiltState = smoothDamp(be.clientTilt, targetTilt, be.clientTiltVel, SMOOTH_TIME, dt);
        be.clientTilt = tiltState[0];
        be.clientTiltVel = tiltState[1];

        final Vector3f beamLocal = StageLightBE.computeBeamDirection(facing, be.clientPan, be.clientTilt);
        final Vector3f aim = StageLightBE.rotateFacingToUpFrame(facing, beamLocal);
        final float elevDeg = (float) Math.toDegrees(Math.acos(Mth.clamp(aim.y, -1.0f, 1.0f)));

        final float aziDeg = (float) Math.toDegrees(Math.atan2(aim.x, aim.z));

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        applyFacingRotation(ms, facing);
        ms.translate(-0.5, -0.5, -0.5);

        final boolean mk2 = be instanceof com.createstats.blockentity.StageLightMk2BE;
        final PartialModel baseModel = mk2 ? ModPartialModels.STAGE_LIGHT_MK2_BASE : ModPartialModels.STAGE_LIGHT_BASE;
        final PartialModel yokeModel = mk2 ? ModPartialModels.STAGE_LIGHT_MK2_YOKE : ModPartialModels.STAGE_LIGHT_YOKE;
        final PartialModel headModel = mk2 ? ModPartialModels.STAGE_LIGHT_MK2_HEAD : ModPartialModels.STAGE_LIGHT_HEAD;
        final PartialModel lensModel = mk2 ? ModPartialModels.STAGE_LIGHT_MK2_HEAD_LENS : ModPartialModels.STAGE_LIGHT_HEAD_LENS;

        renderPart(baseModel, state, ms, buffer, light);

        ms.pushPose();
        ms.translate(0.5, 0.0, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(be.clientPan * COG_RATIO));
        ms.translate(-0.5, 0.0, -0.5);
        renderPart(mk2 ? ModPartialModels.STAGE_LIGHT_MK2_COG : ModPartialModels.STAGE_LIGHT_COG, state, ms, buffer, light);
        ms.popPose();

        ms.translate(0.5, 0.0, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(aziDeg));
        ms.translate(-0.5, 0.0, -0.5);
        renderPart(yokeModel, state, ms, buffer, light);

        ms.translate(0.5, HUB_Y, 0.5);
        ms.mulPose(Axis.XP.rotationDegrees(elevDeg));
        ms.translate(-0.5, -HUB_Y, -0.5);
        renderPart(headModel, state, ms, buffer, light);

        final Level level = be.getLevel();
        final long gameTime = level != null ? level.getGameTime() : 0L;
        final float fxFactor = StageLightBE.effectBrightnessFactor(be.getEffect(), be.getEffectSpeed(), gameTime, partialTick);
        final int fxTint = StageLightBE.effectTint(be.getEffect(), be.getEffectSpeed(), be.getTint(), gameTime, partialTick);
        renderLens(lensModel, state, ms, buffer, be.isLit() && fxFactor > 0.0f ? LightTexture.FULL_BRIGHT : light, fxTint);

        if (be.isLit() && fxFactor > 0.0f) {
            renderBeam(be, facing, ms, buffer, fxFactor, fxTint);
        }

        ms.popPose();
    }

    private static void renderPart(PartialModel model, BlockState state, PoseStack ms,
                                   MultiBufferSource buffer, int light) {
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private static void renderLens(PartialModel lensModel, BlockState state, PoseStack ms, MultiBufferSource buffer, int light, int tint) {
        final int r = (tint >> 16) & 0xFF;
        final int g = (tint >> 8) & 0xFF;
        final int b = tint & 0xFF;
        CachedBuffers.partial(lensModel, state)
                .light(light)
                .color(r, g, b, 255)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private static void renderBeam(StageLightBE be, Direction facing, PoseStack ms, MultiBufferSource buffer,
                                   float fxFactor, int fxTint) {
        if (!be.isBeamVisible()) {
            return;
        }
        final Level level = be.getLevel();
        final Minecraft mc = Minecraft.getInstance();
        if (level == null || mc.player == null) {
            return;
        }

        final Vector3f a = StageLightBE.computeBeamDirection(facing, be.clientPan, be.clientTilt);
        final Vec3 dir = new Vec3(a.x, a.y, a.z);
        if (dir.lengthSqr() < 1.0e-6) {
            return;
        }
        final Vec3 ndir = dir.normalize();
        final Vec3 start = Vec3.atCenterOf(be.getBlockPos()).add(ndir.scale(0.6));
        final int range = be.getRange();

        final long nowNanos = System.nanoTime();
        if (nowNanos - be.clientBeamClipNanos > 120_000_000L) {
            be.clientBeamClipNanos = nowNanos;
            final Vec3 end = start.add(ndir.scale(range));
            final BlockHitResult hit = level.clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            be.clientBeamBlocked = hit.getType() == HitResult.Type.BLOCK;
            be.clientBeamLen = be.clientBeamBlocked ? (float) hit.getLocation().distanceTo(start) + 0.6f : range;
        }
        final boolean blocked = be.clientBeamBlocked;
        final float len = be.clientBeamLen;
        if (len < 0.2f) {
            return;
        }

        final float cr = ((fxTint >> 16) & 0xFF) / 255.0f;
        final float cg = ((fxTint >> 8) & 0xFF) / 255.0f;
        final float cb = (fxTint & 0xFF) / 255.0f;
        final float bright = Mth.clamp(be.getBrightness() / 15.0f, 0.15f, 1.0f) * fxFactor;

        final float baseR = Math.min(1.0f + len * be.getFocus() / 26.0f,
                (float) be.getBeamWidth());
        final float lensY = be instanceof com.createstats.blockentity.StageLightMk2BE ? MK2_LENS_Y : LENS_Y;
        final float y0 = lensY;
        final float y1 = lensY + len;

        final VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        final Matrix4f mat = ms.last().pose();

        if (be.isVolumetric()) {
            coneShell(vc, mat, 0.14f, baseR * 1.08f, y0, y1, cr, cg, cb, 0.055f * bright, 0.0f);
            coneShell(vc, mat, 0.11f, baseR * 0.78f, y0, y1, cr, cg, cb, 0.055f * bright, 0.0f);
            coneShell(vc, mat, 0.07f, baseR * 0.48f, y0, y1, cr, cg, cb, 0.065f * bright, 0.0f);
        } else {
            coneShell(vc, mat, 0.12f, baseR, y0, y1, cr, cg, cb, 0.09f * bright, 0.0f);
            coneShell(vc, mat, 0.06f, baseR * 0.55f, y0, y1, cr, cg, cb, 0.15f * bright, 0.0f);
        }

        if (blocked) {
            splashDisc(vc, mat, baseR, y1, cr, cg, cb, (be.isVolumetric() ? 0.22f : 0.30f) * bright);
        }
    }

    private static void coneShell(VertexConsumer vc, Matrix4f mat, float apexR, float baseR, float y0, float y1,
                                  float r, float g, float b, float apexA, float baseA) {
        for (int i = 0; i < BEAM_SEGMENTS; i++) {
            final double a0 = (i / (double) BEAM_SEGMENTS) * Math.PI * 2.0;
            final double a1 = ((i + 1) / (double) BEAM_SEGMENTS) * Math.PI * 2.0;
            final float ax0 = 0.5f + apexR * (float) Math.cos(a0);
            final float az0 = 0.5f + apexR * (float) Math.sin(a0);
            final float ax1 = 0.5f + apexR * (float) Math.cos(a1);
            final float az1 = 0.5f + apexR * (float) Math.sin(a1);
            final float bx0 = 0.5f + baseR * (float) Math.cos(a0);
            final float bz0 = 0.5f + baseR * (float) Math.sin(a0);
            final float bx1 = 0.5f + baseR * (float) Math.cos(a1);
            final float bz1 = 0.5f + baseR * (float) Math.sin(a1);
            vertex(vc, mat, ax0, y0, az0, r, g, b, apexA);
            vertex(vc, mat, bx0, y1, bz0, r, g, b, baseA);
            vertex(vc, mat, bx1, y1, bz1, r, g, b, baseA);
            vertex(vc, mat, ax1, y0, az1, r, g, b, apexA);
        }
    }

    private static void splashDisc(VertexConsumer vc, Matrix4f mat, float radius, float y,
                                   float r, float g, float b, float centerA) {
        for (int i = 0; i < BEAM_SEGMENTS; i++) {
            final double a0 = (i / (double) BEAM_SEGMENTS) * Math.PI * 2.0;
            final double a1 = ((i + 1) / (double) BEAM_SEGMENTS) * Math.PI * 2.0;
            final float rx0 = 0.5f + radius * (float) Math.cos(a0);
            final float rz0 = 0.5f + radius * (float) Math.sin(a0);
            final float rx1 = 0.5f + radius * (float) Math.cos(a1);
            final float rz1 = 0.5f + radius * (float) Math.sin(a1);
            vertex(vc, mat, 0.5f, y, 0.5f, r, g, b, centerA);
            vertex(vc, mat, rx0, y, rz0, r, g, b, 0.0f);
            vertex(vc, mat, rx1, y, rz1, r, g, b, 0.0f);
            vertex(vc, mat, 0.5f, y, 0.5f, r, g, b, centerA);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                               float r, float g, float b, float a) {
        vc.addVertex(mat, x, y, z).setColor(r, g, b, a);
    }

    private static float[] smoothDamp(float current, float target, float velocity, float smoothTime, float dt) {
        smoothTime = Math.max(0.0001f, smoothTime);
        final float omega = 2.0f / smoothTime;
        final float x = omega * dt;
        final float exp = 1.0f / (1.0f + x + 0.48f * x * x + 0.235f * x * x * x);
        final float change = current - target;
        final float temp = (velocity + omega * change) * dt;
        final float newVel = (velocity - omega * temp) * exp;
        final float result = target + (change + temp) * exp;
        return new float[]{result, newVel};
    }

    private static void applyFacingRotation(PoseStack ms, Direction facing) {
        switch (facing) {
            case DOWN -> ms.mulPose(Axis.XP.rotationDegrees(180.0f));
            case NORTH -> ms.mulPose(Axis.XP.rotationDegrees(-90.0f));
            case SOUTH -> ms.mulPose(Axis.XP.rotationDegrees(90.0f));
            case EAST -> ms.mulPose(Axis.ZP.rotationDegrees(-90.0f));
            case WEST -> ms.mulPose(Axis.ZP.rotationDegrees(90.0f));
            default -> {
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(StageLightBE be) {
        return new AABB(be.getBlockPos()).inflate(Math.max(1.0, be.getRange()));
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(96);
    }
}
