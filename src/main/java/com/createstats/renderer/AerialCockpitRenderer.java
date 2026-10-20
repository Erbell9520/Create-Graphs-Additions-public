package com.createstats.renderer;

import com.createstats.ModConfiguration;
import com.createstats.client.ModPartialModels;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.air.AerialBE;
import com.createstats.vccmk4.air.AerialDeckBlock;
import com.createstats.vccmk4.air.core.AerialCockpitMotion;
import com.createstats.vccmk4.air.core.AerialCockpitMotion.Frame;
import com.createstats.vccmk4.air.core.AerialCockpitMotion.Lamp;
import com.createstats.vccmk4.air.core.AerialCockpitState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaterniond;

@OnlyIn(Dist.CLIENT)
public class AerialCockpitRenderer implements BlockEntityRenderer<AerialBE> {
    static final float UPPER_ANGLE = -22.5f;
    static final float UPPER_Y = 9.681981f / 16f, UPPER_Z = 6.818019f / 16f;
    static final float LOWER_ANGLE = -45.0f;
    static final float LOWER_Y = 6.500000f / 16f, LOWER_Z = 10.000000f / 16f;
    static final float ASI_X = 3.0000f / 16f, ASI_Y = 12.431981f / 16f, ASI_Z = 6.818019f / 16f;
    static final float ATT_X = 8.0000f / 16f, ATT_Y = 12.431981f / 16f, ATT_Z = 6.818019f / 16f;
    static final float ALT_X = 13.0000f / 16f, ALT_Y = 12.431981f / 16f, ALT_Z = 6.818019f / 16f;
    static final float TURN_X = 3.0000f / 16f, TURN_Y = 8.750000f / 16f, TURN_Z = 10.000000f / 16f;
    static final float HDG_X = 8.0000f / 16f, HDG_Y = 8.750000f / 16f, HDG_Z = 10.000000f / 16f;
    static final float VSI_X = 13.0000f / 16f, VSI_Y = 8.750000f / 16f, VSI_Z = 10.000000f / 16f;
    static final float YOKE_X = 8.0000f / 16f, YOKE_Y = 9.0000f / 16f, YOKE_Z = 13.2500f / 16f;
    static final float LEVER_L_X = 13.1500f / 16f, LEVER_R_X = 14.3500f / 16f, LEVER_Y = 7.0000f / 16f, LEVER_Z = 12.0000f / 16f;
    static final float COMPASS_X = 8.0000f / 16f, COMPASS_Y = 16f / 16f, COMPASS_Z = 3.75f / 16f;
    static final float LEVER_SWING = 35.0f, YOKE_SLIDE = 0.75f / 16f;
    static final float EARTH_R = 1.925f / 16f;

    static final float[][] HALO = {
        {1.75f, 14.681981f, 6.968019f, 0.75f, 0.5f, 1},
        {4.0f, 15.0f, 5.4f, 0.5f, 0.5f, 0},
        {6.5f, 15.0f, 5.4f, 0.5f, 0.5f, 0},
        {6.5f, 15.0f, 5.4f, 0.5f, 0.5f, 0},
        {8.0f, 15.0f, 5.4f, 0.5f, 0.5f, 0},
        {8.0f, 15.0f, 5.4f, 0.5f, 0.5f, 0},
        {9.5f, 15.0f, 5.4f, 0.5f, 0.5f, 0},
        {9.5f, 15.0f, 5.4f, 0.5f, 0.5f, 0},
        {12.0f, 15.0f, 5.4f, 0.5f, 0.5f, 0},
    };

    static final int[] HALO_RGB = {
        0xFFB030, 0x5CF09C, 0xF4F6FF, 0x5CF09C, 0xF4F6FF, 0x5CF09C, 0xF4F6FF, 0x5CF09C, 0xFFB62E,
    };

    static final float DECK_FIRST_OFFICER = 16.0f / 16f, DECK_THROTTLES = 2.25f / 16f, DECK_CENTRE = 8.0f / 16f;
    static final float DECK_PANEL_X1 = 31.5f;
    static final float TRIM_X = 13.95f / 16f, TRIM_Y = 4.0f / 16f, TRIM_Z = 13.2f / 16f;

    static final float PANEL_X0 = 0.5f, PANEL_X1 = 15.5f, UPPER_H = 5.5f, LOWER_H = 4.5f, LUME_Z = 0.1f;

    static final float HALO_REACH = 1.5f;

    private static final ResourceLocation HALO_CARD =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/lamp_halo.png");
    private static final ResourceLocation LUME_UPPER =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/vcc_mk4_aerial_cockpit/lume_upper.png");
    private static final ResourceLocation LUME_LOWER =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/vcc_mk4_aerial_cockpit/lume_lower.png");
    private static final ResourceLocation LUME_UPPER_DECK =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/vcc_mk4_aerial_flight_deck/lume_upper.png");
    private static final ResourceLocation LUME_LOWER_DECK =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/vcc_mk4_aerial_flight_deck/lume_lower.png");

    public AerialCockpitRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AerialBE be, float partialTick, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) return;
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        boolean deck = state.getBlock() instanceof AerialDeckBlock;
        Frame f = be.cockpitFrame(partialTick);
        double[] att = attitude(be, facing, partialTick, f);
        VertexConsumer cutout = buffer.getBuffer(RenderType.cutoutMipped());

        int needleLight = SuperByteBuffer.maxLight(light, LightTexture.pack(Math.round((float) f.lights * 15.0f), 0));

        ms.pushPose();
        applyFacing(ms, facing);
        pilot(state, f, att, light, needleLight, ms, cutout);
        if (deck) {
            ms.pushPose();
            ms.translate(DECK_FIRST_OFFICER, 0.0f, 0.0f);
            pilot(state, f, att, light, needleLight, ms, cutout);
            ms.popPose();
        }

        ms.pushPose();
        if (deck) ms.translate(DECK_THROTTLES, 0.0f, 0.0f);
        lever(ModPartialModels.AERIAL_THROTTLE_L, state, LEVER_L_X, (float) f.lever, light, ms, cutout);
        lever(ModPartialModels.AERIAL_THROTTLE_R, state, LEVER_R_X, (float) f.lever, light, ms, cutout);
        ms.popPose();

        ms.pushPose();
        if (deck) ms.translate(DECK_CENTRE, 0.0f, 0.0f);
        CachedBuffers.partial(ModPartialModels.AERIAL_COMPASS, state)
                .translate(COMPASS_X, COMPASS_Y, COMPASS_Z)
                .rotateDegrees((float) att[0], Direction.UP)
                .translate(-COMPASS_X, -COMPASS_Y, -COMPASS_Z)
                .light(light)
                .renderInto(ms, cutout);
        for (Lamp lamp : Lamp.values()) {
            if (lamp != Lamp.CAUTION) glow(modelOf(lamp), false, f.shown(lamp), state, ms, cutout);
        }
        ms.popPose();
        if (deck) {
            CachedBuffers.partial(ModPartialModels.AERIAL_DECK_TRIM_WHEEL, state)
                    .translate(TRIM_X, TRIM_Y, TRIM_Z)
                    .rotateDegrees((float) f.trim, Direction.EAST)
                    .translate(-TRIM_X, -TRIM_Y, -TRIM_Z)
                    .light(light)
                    .renderInto(ms, cutout);
        }
        lume(f.lights, deck, ms, buffer);
        halos(f, deck, ms, buffer);
        ms.popPose();
    }

    private static void pilot(BlockState state, Frame f, double[] att, int light, int needleLight, PoseStack ms,
                              VertexConsumer cutout) {
        CachedBuffers.partial(ModPartialModels.AERIAL_YOKE, state)
                .translate(0.0f, 0.0f, (float) f.yokeSlide * YOKE_SLIDE)
                .translate(YOKE_X, YOKE_Y, YOKE_Z)
                .rotateDegrees((float) -f.yokeRoll, Direction.SOUTH)
                .translate(-YOKE_X, -YOKE_Y, -YOKE_Z)
                .light(light)
                .renderInto(ms, cutout);
        double heading = att[0];
        needle(ModPartialModels.AERIAL_NEEDLE_ASI, state, true, ASI_X, ASI_Y, ASI_Z, f.asi, needleLight, ms, cutout);
        needle(ModPartialModels.AERIAL_NEEDLE_ALT, state, true, ALT_X, ALT_Y, ALT_Z, f.alt, needleLight, ms, cutout);

        if (f.altBugShown) {
            needle(ModPartialModels.AERIAL_BUG_ALT, state, true, ALT_X, ALT_Y, ALT_Z, f.altBug, needleLight, ms, cutout);
        }
        needle(ModPartialModels.AERIAL_NEEDLE_TURN, state, false, TURN_X, TURN_Y, TURN_Z, f.turn, needleLight, ms, cutout);
        needle(ModPartialModels.AERIAL_NEEDLE_HDG, state, false, HDG_X, HDG_Y, HDG_Z, -heading, needleLight, ms, cutout);
        if (Double.isFinite(f.heldHeading)) {
            needle(ModPartialModels.AERIAL_BUG_HDG, state, false, HDG_X, HDG_Y, HDG_Z, f.heldHeading - heading, needleLight, ms,
                    cutout);
        }
        needle(ModPartialModels.AERIAL_NEEDLE_VSI, state, false, VSI_X, VSI_Y, VSI_Z, f.vsi, needleLight, ms, cutout);
        horizon(state, att[1], att[2], needleLight, ms, cutout);
        onUpper(CachedBuffers.partial(ModPartialModels.AERIAL_SYMBOL, state)).light(needleLight).renderInto(ms, cutout);
        glow(modelOf(Lamp.CAUTION), true, f.shown(Lamp.CAUTION), state, ms, cutout);
    }

    private static double[] attitude(AerialBE be, Direction facing, float partialTick, Frame f) {
        Object craft = be.cockpitCraft();
        Quaterniond q = craft == null ? null : SableBridge.renderOrientation(craft, partialTick);
        if (q != null && Double.isFinite(q.x) && Double.isFinite(q.w)) {
            return AerialCockpitMotion.attitude(q.x, q.y, q.z, q.w, facing.getStepX(), facing.getStepZ());
        }
        AerialCockpitState s = be.cockpitState();
        if (s.live() && s.onCraft()) return new double[] {f.heading, f.pitch, f.roll};
        return new double[] {AerialCockpitMotion.facingHeading(facing.getStepX(), facing.getStepZ()), 0.0, 0.0};
    }

    private static void horizon(BlockState state, double pitch, double roll, int light, PoseStack ms, VertexConsumer out) {
        double[] s = AerialCockpitMotion.earthStrips(AerialCockpitMotion.horizonDrop(pitch), EARTH_R * 16.0,
                AerialCockpitMotion.EARTH_STRIP);
        for (int i = 0; i + 3 < s.length; i += 4) {
            float w = (float) (s[i + 2] - s[i]);
            float h = (float) (s[i + 3] - s[i + 1]);
            onUpper(CachedBuffers.partial(ModPartialModels.AERIAL_HORIZON_STRIP, state))
                    .translate(ATT_X, ATT_Y, ATT_Z)
                    .rotateDegrees((float) roll, Direction.SOUTH)
                    .translate((float) (s[i] + s[i + 2]) / 32.0f, (float) s[i + 3] / 16.0f, 0.0f)
                    .scale(w, h, 1.0f)
                    .translate(-ATT_X, -ATT_Y, -ATT_Z)
                    .light(light)
                    .renderInto(ms, out);
        }
    }

    private static void needle(PartialModel model, BlockState state, boolean upper, float x, float y, float z, double deg,
                               int light, PoseStack ms, VertexConsumer out) {
        SuperByteBuffer part = CachedBuffers.partial(model, state);
        (upper ? onUpper(part) : onLower(part))
                .translate(x, y, z)
                .rotateDegrees((float) -deg, Direction.SOUTH)
                .translate(-x, -y, -z)
                .light(light)
                .renderInto(ms, out);
    }

    private static void lever(PartialModel model, BlockState state, float x, float deg, int light, PoseStack ms,
                              VertexConsumer out) {
        CachedBuffers.partial(model, state)
                .translate(x, LEVER_Y, LEVER_Z)
                .rotateDegrees(-deg, Direction.EAST)
                .translate(-x, -LEVER_Y, -LEVER_Z)
                .light(light)
                .renderInto(ms, out);
    }

    private static SuperByteBuffer onUpper(SuperByteBuffer part) {
        return part.translate(0.5f, UPPER_Y, UPPER_Z).rotateDegrees(UPPER_ANGLE, Direction.EAST).translate(-0.5f, -UPPER_Y, -UPPER_Z);
    }

    private static SuperByteBuffer onLower(SuperByteBuffer part) {
        return part.translate(0.5f, LOWER_Y, LOWER_Z).rotateDegrees(LOWER_ANGLE, Direction.EAST).translate(-0.5f, -LOWER_Y, -LOWER_Z);
    }

    private static void glow(PartialModel model, boolean upper, double level, BlockState state, PoseStack ms,
                             VertexConsumer out) {
        if (level <= 0.004) return;
        int c = Math.round((float) Math.min(1.0, level) * 255.0f);
        SuperByteBuffer part = CachedBuffers.partial(model, state);
        if (upper) onUpper(part);
        part.disableDiffuse().light(LightTexture.FULL_BRIGHT).color(c, c, c, 255).renderInto(ms, out);
    }

    private static void lume(double level, boolean deck, PoseStack ms, MultiBufferSource buffer) {
        int alpha = Math.round((float) Math.min(1.0, level) * 255.0f);
        if (alpha <= 2) return;
        int argb = (alpha << 24) | 0xFFFFFF;
        float u = 1.0f / 16.0f;
        float x1 = deck ? DECK_PANEL_X1 : PANEL_X1;
        ms.pushPose();
        ms.translate(0.5f, UPPER_Y, UPPER_Z);
        ms.mulPose(Axis.XP.rotationDegrees(UPPER_ANGLE));
        ms.translate(-0.5f, -UPPER_Y, -UPPER_Z);
        panelQuad(buffer.getBuffer(RenderType.entityTranslucentEmissive(deck ? LUME_UPPER_DECK : LUME_UPPER)), ms.last(),
                PANEL_X0 * u, x1 * u,
                UPPER_Y, UPPER_Y + UPPER_H * u, UPPER_Z + LUME_Z * u, argb);
        ms.popPose();
        ms.pushPose();
        ms.translate(0.5f, LOWER_Y, LOWER_Z);
        ms.mulPose(Axis.XP.rotationDegrees(LOWER_ANGLE));
        ms.translate(-0.5f, -LOWER_Y, -LOWER_Z);
        panelQuad(buffer.getBuffer(RenderType.entityTranslucentEmissive(deck ? LUME_LOWER_DECK : LUME_LOWER)), ms.last(),
                PANEL_X0 * u, x1 * u,
                LOWER_Y, LOWER_Y + LOWER_H * u, LOWER_Z + LUME_Z * u, argb);
        ms.popPose();
    }

    private static void halos(Frame f, boolean deck, PoseStack ms, MultiBufferSource buffer) {
        VertexConsumer vc = null;
        for (Lamp lamp : Lamp.values()) {
            int alpha = AerialCockpitMotion.haloAlpha(f.shown(lamp));
            if (alpha <= 2) continue;
            if (vc == null) vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(HALO_CARD));
            if (lamp == Lamp.CAUTION) {
                halo(vc, ms, lamp, alpha, 0.0f);
                if (deck) halo(vc, ms, lamp, alpha, DECK_FIRST_OFFICER);
            } else {
                halo(vc, ms, lamp, alpha, deck ? DECK_CENTRE : 0.0f);
            }
        }
    }

    private static void halo(VertexConsumer vc, PoseStack ms, Lamp lamp, int alpha, float dx) {
        float u = 1.0f / 16.0f;
        float[] h = HALO[lamp.ordinal()];
        ms.pushPose();
        ms.translate(dx, 0.0f, 0.0f);
        if (h[5] > 0.5f) {
            ms.translate(0.5f, UPPER_Y, UPPER_Z);
            ms.mulPose(Axis.XP.rotationDegrees(UPPER_ANGLE));
            ms.translate(-0.5f, -UPPER_Y, -UPPER_Z);
        }
        int argb = (alpha << 24) | HALO_RGB[lamp.ordinal()];
        float hw = h[3] + HALO_REACH, hh = h[4] + HALO_REACH;
        panelQuad(vc, ms.last(), (h[0] - hw) * u, (h[0] + hw) * u, (h[1] - hh) * u, (h[1] + hh) * u, h[2] * u, argb);
        ms.popPose();
    }

    private static void panelQuad(VertexConsumer vc, PoseStack.Pose pose, float x0, float x1, float y0, float y1, float z,
                                  int argb) {
        corner(vc, pose, x0, y0, z, 0.0f, 1.0f, argb);
        corner(vc, pose, x1, y0, z, 1.0f, 1.0f, argb);
        corner(vc, pose, x1, y1, z, 1.0f, 0.0f, argb);
        corner(vc, pose, x0, y1, z, 0.0f, 0.0f, argb);
    }

    private static void corner(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb) {
        vc.addVertex(pose, x, y, z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0f, 0.0f, 1.0f);
    }

    private static PartialModel modelOf(Lamp lamp) {
        return switch (lamp) {
            case CAUTION -> ModPartialModels.AERIAL_LAMP_CAUTION;
            case HELM -> ModPartialModels.AERIAL_LAMP_HELM;
            case ANCHOR_ARMED -> ModPartialModels.AERIAL_LAMP_ANCHOR_ARMED;
            case ANCHOR_ENGAGED -> ModPartialModels.AERIAL_LAMP_ANCHOR_ENGAGED;
            case KEEP_ARMED -> ModPartialModels.AERIAL_LAMP_KEEP_ARMED;
            case KEEP_ENGAGED -> ModPartialModels.AERIAL_LAMP_KEEP_ENGAGED;
            case GOTO_ARMED -> ModPartialModels.AERIAL_LAMP_GOTO_ARMED;
            case GOTO_ENGAGED -> ModPartialModels.AERIAL_LAMP_GOTO_ENGAGED;
            case MOORED -> ModPartialModels.AERIAL_LAMP_MOORED;
        };
    }

    private static void applyFacing(PoseStack ms, Direction facing) {
        final float degrees = switch (facing) {
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
        if (degrees == 0.0f) return;
        ms.translate(0.5f, 0.5f, 0.5f);
        ms.mulPose(Axis.YP.rotationDegrees(-degrees));
        ms.translate(-0.5f, -0.5f, -0.5f);
    }

    @Override
    public AABB getRenderBoundingBox(AerialBE be) {
        BlockPos pos = be.getBlockPos();
        AABB box = new AABB(pos);
        BlockState state = be.getBlockState();
        if (state.getBlock() instanceof AerialDeckBlock && state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            box = box.minmax(new AABB(pos.relative(state.getValue(HorizontalDirectionalBlock.FACING).getClockWise())));
        }
        return box.inflate(0.125);
    }

    @Override
    public boolean shouldRenderOffScreen(AerialBE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return ModConfiguration.cappedViewDistance(64);
    }
}
