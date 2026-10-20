package com.createstats.powertrack.client;

import com.createstats.powertrack.SectionInsulatorBE;
import com.createstats.powertrack.SectionView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

// a hair bigger than the dark lens so they never share a face
public class SectionInsulatorRenderer extends SafeBlockEntityRenderer<SectionInsulatorBE> {
    public SectionInsulatorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SectionInsulatorBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        final BlockState state = be.getBlockState();
        final VertexConsumer vc = buffer.getBuffer(RenderType.cutoutMipped());
        for (int i = 0; i < 2; i++) {
            final int s = be.lampColour(i);
            final float glow = be.lampGlow(i, partialTicks);
            final PartialModel lens = s == SectionView.LIVE ? PowerTrackPartials.INSULATOR_LAMP_GREEN
                    : s == SectionView.COLLECTING ? PowerTrackPartials.INSULATOR_LAMP_AMBER : null;
            if (lens == null || glow <= TrackFeederRenderer.GLOW_VISIBLE) {
                continue;
            }
            final SuperByteBuffer lit = CachedBuffers.partial(lens, state);
            TrackFeederRenderer.face(lit, be.facing());
            lit.translate((float) ((SectionInsulatorBE.LAMP_X[i] - SectionInsulatorBE.LAMP_X[0]) / 16.0), 0, 0);
            TrackFeederRenderer.glowing(lit, glow, light).renderInto(ms, vc);
        }
    }
}
