package com.createstats.powertrack.client;

import com.createstats.blockentity.TrackFeederBE;
import com.createstats.powertrack.core.FeederMeter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class TrackFeederRenderer extends SafeBlockEntityRenderer<TrackFeederBE> {
    public static final float[] NEEDLE_PIVOT = FeederMeter.NEEDLE_PIVOT;
    public static final float FULL_SCALE_DEG = FeederMeter.FULL_SCALE_DEG;

    public TrackFeederRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(TrackFeederBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
                              int overlay) {
        final BlockState state = be.getBlockState();
        final Direction facing = be.facing();
        final VertexConsumer solid = buffer.getBuffer(RenderType.cutoutMipped());

        final SuperByteBuffer needle = CachedBuffers.partial(PowerTrackPartials.FEEDER_NEEDLE, state);
        face(needle, facing);
        needle.translate(NEEDLE_PIVOT[0] / 16f, NEEDLE_PIVOT[1] / 16f, NEEDLE_PIVOT[2] / 16f)
                .rotateZDegrees(FULL_SCALE_DEG * be.needle(partialTicks))
                .translate(-NEEDLE_PIVOT[0] / 16f, -NEEDLE_PIVOT[1] / 16f, -NEEDLE_PIVOT[2] / 16f);
        needle.light(light).renderInto(ms, solid);

        final int lamp = be.lamp();
        final float glow = be.lampGlow(partialTicks);
        if (lamp != 0 && glow > GLOW_VISIBLE) {
            final SuperByteBuffer lit = CachedBuffers.partial(lamp > 0 ? PowerTrackPartials.FEEDER_LAMP_GREEN
                    : PowerTrackPartials.FEEDER_LAMP_AMBER, state);
            face(lit, facing);
            glowing(lit, glow, light).renderInto(ms, solid);
        }
    }

    static final float GLOW_VISIBLE = 0.04f;

    static SuperByteBuffer glowing(SuperByteBuffer lit, float glow, int worldLight) {
        final float g = Math.max(0f, Math.min(1f, glow));
        final int c = Math.round(255 * (0.3f + 0.7f * g));
        final int own = Math.round(15 * g);
        final int block = Math.max(LightTexture.block(worldLight), own);
        final int sky = Math.max(LightTexture.sky(worldLight), own);
        return lit.color(c, c, c, 255).light(LightTexture.pack(block, sky));
    }

    static void face(SuperByteBuffer sbb, Direction facing) {
        sbb.center().rotateYDegrees(-yRot(facing)).uncenter();
    }

    static float yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }
}
