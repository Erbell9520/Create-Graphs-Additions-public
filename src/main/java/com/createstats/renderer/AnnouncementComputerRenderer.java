package com.createstats.renderer;

import com.createstats.block.AnnouncementComputerBlock;
import com.createstats.blockentity.AnnouncementComputerBE;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AnnouncementComputerRenderer implements BlockEntityRenderer<AnnouncementComputerBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float FAN_CX = 7.0f;
    private static final float FAN_CY = 15.5f;
    private static final float FAN_CZ = 11.5f;

    private static final float PILOT_CX = 13.3f;
    private static final float PILOT_CY = 15.3f;
    private static final float PILOT_CZ = 4.1f;

    private static final float[] BAR_XS = {3.5f, 5.0f, 6.5f};
    private static final float BAR_BASE = 6.8f;
    private static final float BAR_MAX = 4.0f;
    private static final float BAR_Z = 1.68f;

    private static final float LENS_CX = 13.8f;
    private static final float[] LENS_CYS = {3.95f, 2.80f, 1.65f};
    private static final float LENS_CZ = 0.5f;

    public AnnouncementComputerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AnnouncementComputerBE be, float partialTick, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof AnnouncementComputerBlock)) {
            return;
        }

        renderWires(be, ms, buffer, light);

        ms.pushPose();
        applyFacing(ms, state.getValue(AnnouncementComputerBlock.FACING));

        renderFan(be, partialTick, ms, buffer, state, light);
        renderPilot(be, partialTick, ms, buffer, state);
        renderBars(be, partialTick, ms, buffer, state);
        renderLens(be, ms, buffer, state);

        ms.popPose();
    }

    private void renderFan(AnnouncementComputerBE be, float partialTick, PoseStack ms,
                           MultiBufferSource buffer, BlockState state, int light) {
        ms.pushPose();
        ms.translate(FAN_CX * PX, FAN_CY * PX, FAN_CZ * PX);
        ms.mulPose(Axis.YP.rotationDegrees(be.fanAngle(partialTick)));

        draw(ms, buffer, state, light, ModPartialModels.PA_BRAIN_FAN);
        ms.popPose();
    }

    private void renderPilot(AnnouncementComputerBE be, float partialTick, PoseStack ms,
                             MultiBufferSource buffer, BlockState state) {
        ms.pushPose();
        ms.translate(PILOT_CX * PX, PILOT_CY * PX, PILOT_CZ * PX);
        draw(ms, buffer, state, glowLight(be.pilotGlow(partialTick)), ModPartialModels.PA_BRAIN_PILOT_ON);
        ms.popPose();
    }

    private void renderBars(AnnouncementComputerBE be, float partialTick, PoseStack ms,
                            MultiBufferSource buffer, BlockState state) {
        for (int i = 0; i < BAR_XS.length; i++) {
            final float level = be.barLevel(i, partialTick);
            ms.pushPose();
            ms.translate(BAR_XS[i] * PX, BAR_BASE * PX, BAR_Z * PX);

            ms.scale(1.0f, level * BAR_MAX, 1.0f);
            draw(ms, buffer, state, LightTexture.FULL_BRIGHT, ModPartialModels.PA_BRAIN_BAR);
            ms.popPose();
        }
    }

    private void renderLens(AnnouncementComputerBE be, PoseStack ms, MultiBufferSource buffer,
                            BlockState state) {
        final int lit = be.litLens();
        if (lit < 0 || lit >= LENS_CYS.length) {
            return;
        }
        final PartialModel model = switch (lit) {
            case 0 -> ModPartialModels.PA_BRAIN_LENS_NOTICE;
            case 1 -> ModPartialModels.PA_BRAIN_LENS_WARNING;
            default -> ModPartialModels.PA_BRAIN_LENS_CRITICAL;
        };
        ms.pushPose();
        ms.translate(LENS_CX * PX, LENS_CYS[lit] * PX, LENS_CZ * PX);
        draw(ms, buffer, state, LightTexture.FULL_BRIGHT, model);
        ms.popPose();
    }

    private static int glowLight(float glow) {
        final int block = (int) (glow * 15.0f);
        return LightTexture.pack(block, 15);
    }

    private static void draw(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                             PartialModel model) {
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private static void applyFacing(PoseStack ms, Direction facing) {
        final float degrees = switch (facing) {
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
        if (degrees == 0.0f) {
            return;
        }
        ms.translate(0.5f, 0.5f, 0.5f);
        ms.mulPose(Axis.YP.rotationDegrees(-degrees));
        ms.translate(-0.5f, -0.5f, -0.5f);
    }

    private static void renderWires(AnnouncementComputerBE be, PoseStack ms,
                                    MultiBufferSource buffer, int light) {
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
    public boolean shouldRenderOffScreen(AnnouncementComputerBE be) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(AnnouncementComputerBE be) {
        return PowerConnectorRenderer.wireBounds(be.getBlockPos(), be.links());
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
    }
}
