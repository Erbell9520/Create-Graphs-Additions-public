package com.createstats.renderer;

import com.createstats.blockentity.KineticFlywheelBE;
import com.createstats.client.ModPartialModels;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.PoseStack;

/** [1] */
public class KineticFlywheelRenderer extends KineticBlockEntityRenderer<KineticFlywheelBE> {

    public KineticFlywheelRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(KineticFlywheelBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof IRotate)) {
            return;
        }
        Direction facing = facingOf(be);
        Direction.Axis axis = facing.getAxis();

        renderConnectionShaft(be, state, facing, axis, ms, buffer, light);
        renderWheel(be, state, facing, axis, partialTicks, ms, buffer, light);
    }

    private void renderConnectionShaft(KineticFlywheelBE be, BlockState state, Direction facing, Direction.Axis axis,
                                       PoseStack ms, MultiBufferSource buffer, int light) {
        BlockPos pos = be.getBlockPos();
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, pos, axis);
        float angle = (time * be.getSpeed() * 3.0f / 10.0f) % 360.0f;
        angle += offset;
        angle = angle / 180.0f * (float) Math.PI;

        SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, facing);
        kineticRotationTransform(shaft, be, axis, angle, light);
        shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private void renderWheel(KineticFlywheelBE be, BlockState state, Direction facing, Direction.Axis axis,
                             float partialTicks, PoseStack ms, MultiBufferSource buffer, int light) {
        float angleDegrees = be.getRenderAngle(partialTicks) % 360.0f;
        float angle = angleDegrees / 180.0f * (float) Math.PI;

        int tier = be.getTier();
        int maxTier = Math.max(1, be.getMaxTier());

        // [2]
        int count = Math.max(1, be.getFlywheelCount());
        final float band = 0.5f;          // depth the original disc occupies along its axle
        final float bandCenter = 0.5f;    // model is centred at 0.5 along the axle
        float slot = band / count;        // axle width allotted to each disc
        // Flatten so each disc is comfortably thinner than its slot (full thickness when alone).
        float thickness = count == 1 ? 1.0f : Math.min(1.0f, 0.78f / count);
        // [3]
        float grow = 1.0f + tier * 0.025f;
        float chargeFrac = be.getChargePercent() / 100f;
        // Gentle pulse (energy flow) so the active glow catches the eye without flickering.
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float pulse = 0.84f + 0.16f * (0.5f + 0.5f * (float) Math.sin(time * 0.16f));
        int emissive = stateLight(tierLight(light, tier), be.getMode(), chargeFrac, pulse);
        int tint = stateTint(tierTint(tier, maxTier), be.getMode(), chargeFrac, pulse);

        boolean xAxis = axis == Direction.Axis.X;
        boolean yAxis = axis == Direction.Axis.Y;
        boolean zAxis = axis == Direction.Axis.Z;

        for (int i = 0; i < count; i++) {
            // Centre of this disc along the axle, within the 0.5 band.
            float discCenter = (bandCenter - band / 2f) + (i + 0.5f) * slot;
            float axisDelta = discCenter - bandCenter;

            ms.pushPose();
            // Shift into the slot along the axle...
            ms.translate(xAxis ? axisDelta : 0.0, yAxis ? axisDelta : 0.0, zAxis ? axisDelta : 0.0);
            // ...then flatten along the axle and grow the diameter, about the block centre.
            ms.translate(0.5, 0.5, 0.5);
            ms.scale(
                xAxis ? thickness : grow,
                yAxis ? thickness : grow,
                zAxis ? thickness : grow);
            ms.translate(-0.5, -0.5, -0.5);

            SuperByteBuffer wheel = CachedBuffers.partialFacing(ModPartialModels.FLYWHEEL_WHEEL, state, facing);
            kineticRotationTransform(wheel, be, axis, angle, light);
            if (tint != 0xFFFFFFFF) {
                wheel.color(tint);
            }
            if (emissive != light) {
                wheel.light(emissive);
            }
            wheel.renderInto(ms, buffer.getBuffer(RenderType.solid()));
            ms.popPose();
        }
    }

    /** Boost the block-light component with tier so upgraded flywheels glow progressively brighter. */
    private static int tierLight(int packedLight, int tier) {
        if (tier <= 0) {
            return packedLight;
        }
        int block = net.minecraft.client.renderer.LightTexture.block(packedLight);
        int sky = net.minecraft.client.renderer.LightTexture.sky(packedLight);
        block = Math.min(15, block + tier * 3);
        return net.minecraft.client.renderer.LightTexture.pack(block, sky);
    }

    /** Subtle white -> warm-gold ARGB tint that deepens with tier (premium "heavier metal" look). */
    private static int tierTint(int tier, int maxTier) {
        if (tier <= 0) {
            return 0xFFFFFFFF;
        }
        float t = Math.min(1.0f, tier / (float) maxTier);
        int r = 255;
        int g = (int) (255 - 32 * t);
        int b = (int) (255 - 88 * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** [4] */
    private static int stateLight(int packed, KineticFlywheelBE.Mode mode, float frac, float pulse) {
        if (mode == KineticFlywheelBE.Mode.IDLE) {
            return packed;
        }
        // [5]
        int block = net.minecraft.client.renderer.LightTexture.block(packed);
        int sky = net.minecraft.client.renderer.LightTexture.sky(packed);
        float f = Math.max(0f, Math.min(1f, frac));
        int floor = Math.round((13f + 2f * f) * (0.82f + 0.18f * pulse));
        block = Math.min(15, Math.max(block, floor));
        return net.minecraft.client.renderer.LightTexture.pack(block, sky);
    }

    /** Blend a clearly-visible-but-metal-readable state hue (blue charging / amber discharging) over the tier tint. */
    private static int stateTint(int base, KineticFlywheelBE.Mode mode, float frac, float pulse) {
        int hue = switch (mode) {
            case CHARGING -> 0x4AC6FF;
            case DISCHARGING -> 0xFFB048;
            case IDLE -> 0;
        };
        if (hue == 0) {
            return base;
        }
        frac = Math.max(0f, Math.min(1f, frac));
        float amt = 0.34f * (0.45f + 0.55f * frac);
        if (mode == KineticFlywheelBE.Mode.DISCHARGING) {
            amt *= pulse;
        }
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int hr = (hue >> 16) & 0xFF, hg = (hue >> 8) & 0xFF, hb = hue & 0xFF;
        int r = Math.round(br + (hr - br) * amt);
        int g = Math.round(bg + (hg - bg) * amt);
        int b = Math.round(bb + (hb - bb) * amt);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** ARGB (with alpha) for the translucent glow shell around an active wheel; 0 = idle (no glow). */
    private static int glowHalo(KineticFlywheelBE.Mode mode, float frac, float pulse) {
        int hue = switch (mode) {
            case CHARGING -> 0x6AD0FF;
            case DISCHARGING -> 0xFFC062;
            case IDLE -> 0;
        };
        if (hue == 0) {
            return 0;
        }
        frac = Math.max(0f, Math.min(1f, frac));
        float a = (0.32f + 0.42f * frac) * pulse;
        int alpha = Math.round(Math.max(0f, Math.min(0.78f, a)) * 255f);
        return (alpha << 24) | hue;
    }

    private static Direction facingOf(KineticFlywheelBE be) {
        BlockState state = be.getBlockState();
        if (state.hasProperty(com.createstats.block.KineticFlywheelBlock.FACING)) {
            return state.getValue(com.createstats.block.KineticFlywheelBlock.FACING);
        }
        return Direction.NORTH;
    }
}
