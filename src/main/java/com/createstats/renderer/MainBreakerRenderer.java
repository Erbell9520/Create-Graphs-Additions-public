package com.createstats.renderer;

import com.createstats.block.MainBreakerBlock;
import com.createstats.block.MountedPart;
import com.createstats.blockentity.MainBreakerBE;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MainBreakerRenderer implements BlockEntityRenderer<MainBreakerBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float PIVOT_X = 8.0f;
    private static final float PIVOT_Z = 8.0f;
    private static final float SWEEP_DEG = 90.0f;
    private static final float CRANK_R = 2.4f;
    private static final float ANCHOR_X = 3.9f;
    private static final float ANCHOR_Z = 3.9f;

    private static final float THROW_SECONDS = 0.30f;

    private static final float DWELL = 0.20f;

    private static final float RING_AMPLITUDE = 0.055f;
    private static final float RING_SECONDS = 0.16f;
    private static final float RING_HZ = 13.0f;

    private static final float FLASH_SECONDS = 0.30f;

    private static final int PILOT_OPEN = 0xFF33403A;
    private static final int PILOT_CLOSED = 0xFF6BE39A;

    private static final int PILOT_STRIKE = 0xFFEFFFF4;

    private static final int MARK_DARK = 0xFF3A4046;
    private static final int MARK_LIT = 0xFFFFF3DA;

    private static final int PILOT_HOT = 0xFFFFB347;

    private static final int PILOT_FAULT = 0xFFFF4433;

    private static final float FAULT_PULSE_HZ = 1.1f;

    private static final int BAR_DARK = 0xFF23302B;

    private static final int[] BAR_LIT = {0xFF4FD08C, 0xFF9BDD5A, 0xFFEFC24A, 0xFFFF5A3C};

    private static final int BYPASS_LIT = 0xFF6BE4FF;

    private static final float BYPASS_SWEEP_HZ = 0.55f;

    private static final float SELECT_X = 11.6f;
    private static final float SELECT_Z = 4.4f;
    private static final float SELECT_PIVOT_Y = 5.55f;
    private static final float SELECT_LEAN_DEG = 26.0f;

    private static final float SELECT_SECONDS = 0.18f;

    private static final int SELECT_METERED = 0xFFF2E8D2;

    private static final int SELECT_BYPASS = 0xFF6BE4FF;

    private record Parts(PartialModel handle, PartialModel tube, PartialModel rod,
                         PartialModel pilot, PartialModel markO, PartialModel markI,
                         PartialModel padlock, PartialModel selector, PartialModel[] bar) {
    }

    private static final Parts NORMAL = new Parts(
            ModPartialModels.MAIN_BREAKER_HANDLE, ModPartialModels.MAIN_BREAKER_STRUT_TUBE,
            ModPartialModels.MAIN_BREAKER_STRUT_ROD, ModPartialModels.MAIN_BREAKER_PILOT,
            ModPartialModels.MAIN_BREAKER_MARK_O, ModPartialModels.MAIN_BREAKER_MARK_I,
            ModPartialModels.MAIN_BREAKER_PADLOCK, ModPartialModels.MAIN_BREAKER_SELECTOR,
            ModPartialModels.MAIN_BREAKER_BAR);

    private static final Parts CREATIVE = new Parts(
            ModPartialModels.CREATIVE_MAIN_BREAKER_HANDLE,
            ModPartialModels.CREATIVE_MAIN_BREAKER_STRUT_TUBE,
            ModPartialModels.CREATIVE_MAIN_BREAKER_STRUT_ROD,
            ModPartialModels.CREATIVE_MAIN_BREAKER_PILOT,
            ModPartialModels.CREATIVE_MAIN_BREAKER_MARK_O,
            ModPartialModels.CREATIVE_MAIN_BREAKER_MARK_I,
            ModPartialModels.CREATIVE_MAIN_BREAKER_PADLOCK,
            ModPartialModels.CREATIVE_MAIN_BREAKER_SELECTOR,
            ModPartialModels.CREATIVE_MAIN_BREAKER_BAR);

    public MainBreakerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MainBreakerBE be, float partialTick, PoseStack ps, MultiBufferSource buffers,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof MainBreakerBlock block)) {
            return;
        }
        final Parts parts = block.isCreative() ? CREATIVE : NORMAL;
        final boolean closed = be.isClosed();
        final boolean tripped = be.isTripped();

        drawStudWires(be, state, ps, buffers, light);

        final float follower = be.advanceThrow(be.throwTarget(), THROW_SECONDS);
        final float fraction = handleFraction(follower, be);
        final float handleDeg = SWEEP_DEG * fraction;

        ps.pushPose();
        orient(ps, state.getValue(MainBreakerBlock.FACING));

        spunAbout(CachedBuffers.partial(parts.handle(), state), PIVOT_X, PIVOT_Z, handleDeg)
                .light(light)
                .renderInto(ps, buffers.getBuffer(RenderType.solid()));

        drawStrut(ps, buffers, state, parts, light, handleDeg);

        final float strike = closed ? decay(be.sinceImpact(), FLASH_SECONDS) * onlyAfterClosing(be) : 0.0f;
        lamp(ps, buffers, state, parts.pilot(), pilotColour(be, closed, strike));
        lamp(ps, buffers, state, parts.markO(), !closed && !tripped ? MARK_LIT : MARK_DARK);
        lamp(ps, buffers, state, parts.markI(), closed ? MARK_LIT : MARK_DARK);

        drawLoadBar(ps, buffers, state, parts, be);
        drawSelector(ps, buffers, state, parts, be, light);

        if (state.getValue(MainBreakerBlock.LOCKED)) {
            CachedBuffers.partial(parts.padlock(), state)
                    .light(light)
                    .renderInto(ps, buffers.getBuffer(RenderType.solid()));
        }

        ps.popPose();
    }

    private static void drawLoadBar(PoseStack ps, MultiBufferSource buffers, BlockState state,
                                    Parts parts, MainBreakerBE be) {
        final PartialModel[] segments = parts.bar();
        if (be.isClosed() && be.isPassthrough()) {
            drawBypassSweep(ps, buffers, state, segments);
            return;
        }
        final float fill = loadFraction(be) * segments.length;
        final float heat = be.thermalHeat();
        for (int i = 0; i < segments.length; i++) {
            final float level = Mth.clamp(fill - i, 0.0f, 1.0f);
            int argb = level <= 0.0f ? BAR_DARK : mixArgb(BAR_DARK, BAR_LIT[i], level);
            if (heat > 0.0f && i == segments.length - 1) {
                final float phase = (System.nanoTime() % 1_000_000_000L) / 1.0e9f;
                final float pulse = 0.5f + 0.5f * Mth.abs(Mth.sin(phase * FAULT_PULSE_HZ * Mth.PI));
                argb = mixArgb(BAR_LIT[segments.length - 2], BAR_LIT[segments.length - 1],
                        Math.min(1.0f, heat + pulse * 0.35f));
            }
            lamp(ps, buffers, state, segments[i], argb);
        }
    }

    // moving sweep in passthrough, nothing crosses the contacts so there's nothing to measure
    private static void drawBypassSweep(PoseStack ps, MultiBufferSource buffers, BlockState state,
                                        PartialModel[] segments) {
        final float phase = (System.nanoTime() % 4_000_000_000L) / 1.0e9f * BYPASS_SWEEP_HZ;
        for (int i = 0; i < segments.length; i++) {
            final float head = (phase % 1.0f) * (segments.length + 1.0f) - 0.5f;
            final float near = Math.abs(i - head);
            final float level = Math.max(0.18f, 1.0f - near * 0.55f);
            lamp(ps, buffers, state, segments[i], mixArgb(BAR_DARK, BYPASS_LIT, level));
        }
    }

    private static void drawSelector(PoseStack ps, MultiBufferSource buffers, BlockState state,
                                     Parts parts, MainBreakerBE be, int light) {
        final boolean bypass = state.getValue(MainBreakerBlock.PASSTHROUGH);
        final float lean = be.advanceSelector(bypass ? 1.0f : -1.0f, SELECT_SECONDS)
                * SELECT_LEAN_DEG;
        CachedBuffers.partial(parts.selector(), state)
                .translate(SELECT_X * PX, SELECT_PIVOT_Y * PX, SELECT_Z * PX)
                .rotate((float) Math.toRadians(lean), Direction.SOUTH)
                .translate(-SELECT_X * PX, -SELECT_PIVOT_Y * PX, -SELECT_Z * PX)
                .color(bypass ? SELECT_BYPASS : SELECT_METERED)
                .light(light)
                .renderInto(ps, buffers.getBuffer(RenderType.solid()));
    }

    private static float loadFraction(MainBreakerBE be) {
        if (!be.isClosed() || be.isLooped()) {
            return 0.0f;
        }
        final int cap = be.isCreative()
                ? com.createstats.power.FeRates.plus(
                        be.studRate(com.createstats.power.MainBreakerStuds.Stud.INPUT.index()),
                        be.studRate(com.createstats.power.MainBreakerStuds.Stud.OUTPUT.index()))
                : be.ratingFePerTick();
        if (cap <= 0) {
            return 0.0f;
        }
        return Mth.clamp(be.getRateFePerTick() / (float) cap, 0.0f, 1.0f);
    }

    private static int pilotColour(MainBreakerBE be, boolean closed, float strike) {
        if (be.isTripped() || be.isLooped()) {
            final float phase = (System.nanoTime() % 1_000_000_000L) / 1.0e9f;
            final float pulse = 0.55f + 0.45f * Mth.abs(Mth.sin(phase * FAULT_PULSE_HZ * Mth.PI));
            return mixArgb(PILOT_OPEN, PILOT_FAULT, pulse);
        }
        if (!closed) {
            return PILOT_OPEN;
        }
        final int hot = mixArgb(PILOT_CLOSED, PILOT_HOT, be.thermalHeat());
        return mixArgb(hot, PILOT_STRIKE, strike);
    }

    private static void drawStudWires(MainBreakerBE be, BlockState state, PoseStack ps,
                                      MultiBufferSource buffers, int light) {
        if (be.wiresOnStuds() <= 0 || be.getLevel() == null) {
            return;
        }
        final Direction facing = state.getValue(MainBreakerBlock.FACING);
        final com.mojang.blaze3d.vertex.VertexConsumer vc = PowerConnectorRenderer.wireBuffer(buffers);
        final org.joml.Matrix4f mat = ps.last().pose();
        final net.minecraft.core.BlockPos origin = be.getBlockPos();
        for (com.createstats.power.MainBreakerStuds.Stud stud
                : com.createstats.power.MainBreakerStuds.Stud.values()) {
            final java.util.List<MainBreakerBE.StudLink> links = be.studLinks(stud.index());
            if (links.isEmpty()) {
                continue;
            }
            final net.minecraft.world.phys.Vec3 from =
                    com.createstats.power.MainBreakerStuds.anchor(facing, stud);
            for (MainBreakerBE.StudLink link : links) {
                final net.minecraft.world.phys.Vec3 to = PowerConnectorRenderer.attachPoint(
                        be.getLevel(), link.to(), origin);
                PowerConnectorRenderer.drawCable(vc, mat, be.getLevel(), origin, link.to(),
                        (float) from.x, (float) from.y, (float) from.z,
                        (float) (link.to().getX() - origin.getX() + to.x),
                        (float) (link.to().getY() - origin.getY() + to.y),
                        (float) (link.to().getZ() - origin.getZ() + to.z),
                        light, link.tier());
            }
        }
    }

    private static void drawStrut(PoseStack ps, MultiBufferSource buffers, BlockState state,
                                  Parts parts, int light, float handleDeg) {
        final float pinX = PIVOT_X + CRANK_R * Mth.cos(handleDeg * Mth.DEG_TO_RAD);
        final float pinZ = PIVOT_Z + CRANK_R * Mth.sin(handleDeg * Mth.DEG_TO_RAD);
        final float yaw = (float) Math.toDegrees(Math.atan2(pinZ - ANCHOR_Z, pinX - ANCHOR_X));

        placeYawed(CachedBuffers.partial(parts.tube(), state), ANCHOR_X, ANCHOR_Z, yaw)
                .light(light)
                .renderInto(ps, buffers.getBuffer(RenderType.solid()));
        placeYawed(CachedBuffers.partial(parts.rod(), state), pinX, pinZ, yaw + 180.0f)
                .light(light)
                .renderInto(ps, buffers.getBuffer(RenderType.solid()));
    }

    private static void lamp(PoseStack ps, MultiBufferSource buffers, BlockState state,
                             PartialModel model, int argb) {
        CachedBuffers.partial(model, state)
                .color(argb)
                .light(LightTexture.FULL_BRIGHT)
                .renderInto(ps, buffers.getBuffer(RenderType.solid()));
    }

    private static net.createmod.catnip.render.SuperByteBuffer spunAbout(
            net.createmod.catnip.render.SuperByteBuffer part, float px, float pz, float degrees) {
        return part.translate(px * PX, 0.0f, pz * PX)
                .rotate((float) Math.toRadians(-degrees), Direction.UP)
                .translate(-px * PX, 0.0f, -pz * PX);
    }

    private static net.createmod.catnip.render.SuperByteBuffer placeYawed(
            net.createmod.catnip.render.SuperByteBuffer part, float px, float pz, float degrees) {
        return part.translate(px * PX, 0.0f, pz * PX)
                .rotate((float) Math.toRadians(-degrees), Direction.UP);
    }

    private static float handleFraction(float follower, MainBreakerBE be) {
        final float u = Mth.clamp((follower - DWELL) / (1.0f - 2.0f * DWELL), 0.0f, 1.0f);
        final float base = u * u * u * (u * (u * 6.0f - 15.0f) + 10.0f);

        final float since = be.sinceImpact();
        if (since >= RING_SECONDS || be.impactDirection() == 0) {
            return base;
        }
        final float ring = RING_AMPLITUDE * decay(since, RING_SECONDS)
                * Mth.sin(since * RING_HZ * Mth.TWO_PI) * be.impactDirection();
        return base + ring;
    }

    private static float decay(float t, float span) {
        if (t <= 0.0f || t >= span) {
            return 0.0f;
        }
        final float k = 1.0f - t / span;
        return k * k;
    }

    private static float onlyAfterClosing(MainBreakerBE be) {
        return be.impactDirection() > 0 ? 1.0f : 0.0f;
    }

    private static int mixArgb(int from, int to, float t) {
        if (t <= 0.0f) {
            return from;
        }
        final float k = Math.min(1.0f, t);
        final int r = Mth.lerpInt(k, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        final int g = Mth.lerpInt(k, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        final int b = Mth.lerpInt(k, from & 0xFF, to & 0xFF);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void orient(PoseStack ps, Direction facing) {
        final float[] rot = MountedPart.rotationDegrees(facing);
        ps.translate(0.5, 0.5, 0.5);
        if (rot[0] != 0.0f) {
            ps.mulPose(Axis.YP.rotationDegrees(rot[0]));
        }
        if (rot[1] != 0.0f) {
            ps.mulPose(Axis.XP.rotationDegrees(rot[1]));
        }
        ps.translate(-0.5, -0.5, -0.5);
    }

    @Override
    public AABB getRenderBoundingBox(MainBreakerBE be) {
        if (be.wiresOnStuds() <= 0) {
            return new AABB(be.getBlockPos());
        }
        final PowerConnectorRenderer.WireBounds bounds =
                new PowerConnectorRenderer.WireBounds(be.getBlockPos());
        for (com.createstats.power.MainBreakerStuds.Stud stud
                : com.createstats.power.MainBreakerStuds.Stud.values()) {
            for (MainBreakerBE.StudLink link : be.studLinks(stud.index())) {
                bounds.include(link.to());
            }
        }
        return bounds.build();
    }

    @Override
    public boolean shouldRenderOffScreen(MainBreakerBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
    }
}
