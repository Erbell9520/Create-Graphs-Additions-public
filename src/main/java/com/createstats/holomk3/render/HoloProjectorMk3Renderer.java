package com.createstats.holomk3.render;

import com.createstats.client.AnimationClock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.createstats.holomk3.HoloMk3Settings;
import com.createstats.holomk3.HoloProjectorMk3BE;
import com.createstats.holomk3.HoloProjectorMk3Block;
import com.createstats.holomk3.scene.HoloScene;
import com.createstats.holomk3.scene.SceneBuilder;
import com.createstats.renderer.AntennaRendererHelper;

@OnlyIn(Dist.CLIENT)
public class HoloProjectorMk3Renderer implements BlockEntityRenderer<HoloProjectorMk3BE> {
    public HoloProjectorMk3Renderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HoloProjectorMk3BE be, float partialTick, PoseStack ps,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        AntennaRendererHelper.render(be, ps, buffers, packedLight, packedOverlay);

        final Minecraft mc = Minecraft.getInstance();
        final float seconds = clientSeconds();
        HoloModelCache.evictIdle(seconds);

        // ACTIVE is the server's call. powered off projectors keep their channels and would keep drawing

        final BlockState state = be.getBlockState();
        final boolean active = !state.hasProperty(HoloProjectorMk3Block.ACTIVE)
                || state.getValue(HoloProjectorMk3Block.ACTIVE);

        final float brightness = com.createstats.power.client.FePowerVisuals
                .screenBrightness(be.getBlockPos(), be.feConsumer());
        if (brightness <= 0.01f) {
            return;
        }

        final HoloModelCache.State cache =
                HoloModelCache.advance(be.getBlockPos(), active, be.getDataArrivalPulse(), seconds);

        final HoloMk3Settings cfg = be.mk3Settings();
        final float floorY = 1.0f + cfg.getLift();

        final Vector3f camRight = new Vector3f(mc.gameRenderer.getMainCamera().getLeftVector()).negate();
        final Vector3f camUp = new Vector3f(mc.gameRenderer.getMainCamera().getUpVector());
        final float fov = fov(mc);
        final int screenH = Math.max(1, mc.getWindow().getHeight());

        final WorldHoloSink sink = new WorldHoloSink(buffers, mc.font, FULL_BRIGHT, cfg.isShaderPath());

        HoloShaders.frame(seconds, 0.55f, cache.boot);

        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        sink.frame(ps, camRight, camUp);

        sink.volume(cfg.getDepthCue() * 0.35f, 1.0f, floorY, 1.0f, fov, screenH);

        HoloEmitter.paint(sink, cfg.getVolumeWidth() * 0.5f, cfg.getVolumeDepth() * 0.5f, floorY,
                active, cache.pulse, cfg.getGlow() * brightness, seconds);
        ps.popPose();

        if (!active) {
            sink.finish();
            return;
        }

        ps.pushPose();
        ps.translate(0.5, floorY, 0.5);
        motion(ps, be, cfg, partialTick);
        sink.frame(ps, camRight, camUp);
        sink.volume(cfg.getDepthCue(), cfg.getVolumeWidth(), cfg.getVolumeHeight(),
                cfg.getVolumeDepth(), fov, screenH);

        final HoloScene scene = SceneBuilder.build(
                be.getAllChannels().values(),
                be.getHologramSettings().getHiddenChannelIds(),
                cfg,
                be.getLevel() == null ? 0L : be.getLevel().getGameTime(),
                seconds,
                cache.boot,
                budget(sink.viewDistance()));
        HoloPainter.paint(sink, scene, cfg, seconds);
        ps.popPose();

        sink.finish();
    }

    private static void motion(PoseStack ps, HoloProjectorMk3BE be, HoloMk3Settings cfg,
                               float partialTick) {
        switch (cfg.getMotion()) {
            case BILLBOARD -> ps.mulPose(Axis.YP.rotationDegrees(
                    -Minecraft.getInstance().gameRenderer.getMainCamera().getYRot()));
            case SPIN -> {
                final long t = be.getLevel() == null ? 0L : be.getLevel().getGameTime();

                ps.mulPose(Axis.YP.rotationDegrees((float) ((AnimationClock.time(t, partialTick) * cfg.getSpinSpeed()) % 360.0)));
            }

            default -> ps.mulPose(Axis.YP.rotationDegrees(yawFor(be.getBlockState())));
        }
    }

    private static float yawFor(BlockState state) {
        if (!state.hasProperty(HoloProjectorMk3Block.FACING)) {
            return 180.0f;
        }
        return switch (state.getValue(HoloProjectorMk3Block.FACING)) {
            case SOUTH -> 0.0f;
            case EAST -> 90.0f;
            case WEST -> 270.0f;
            default -> 180.0f;
        };
    }

    private static int budget(float distance) {
        if (distance < 9.0f) {
            return 160;
        }
        if (distance < 20.0f) {
            return 96;
        }
        return distance < 40.0f ? 48 : 24;
    }

    private static float fov(Minecraft mc) {
        try {
            return mc.options.fov().get();
        } catch (RuntimeException ignored) {
            return 70.0f;
        }
    }

    private static float clientSeconds() {
        return (System.nanoTime() % 3_600_000_000_000L) / 1.0e9f;
    }

    private static final int FULL_BRIGHT = 15728880;

    @Override
    public boolean shouldRenderOffScreen(HoloProjectorMk3BE be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(HoloProjectorMk3BE be) {
        return be.getRenderBoundingBox();
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(96);
    }

    public static double distanceTo(HoloProjectorMk3BE be) {
        final Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return camera.distanceTo(Vec3.atCenterOf(be.getBlockPos()));
    }
}
