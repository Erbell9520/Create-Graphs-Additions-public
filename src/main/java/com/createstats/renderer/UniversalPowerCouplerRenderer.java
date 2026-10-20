package com.createstats.renderer;

import com.createstats.ModConfiguration;
import com.createstats.block.UniversalPowerCouplerBlock;
import com.createstats.blockentity.UniversalPowerCouplerBE;
import com.createstats.client.ModPartialModels;
import com.createstats.power.CouplerSockets;
import com.createstats.power.PowerCouplerMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// shuttle position IS the mode
@OnlyIn(Dist.CLIENT)
public class UniversalPowerCouplerRenderer implements BlockEntityRenderer<UniversalPowerCouplerBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float SEAM_Z = 8.0f;
    private static final float THEIR_SEAT_Z = 3.7f;
    private static final float OUR_SEAT_Z = 12.3f;
    private static final float SHUTTLE_HOME_Z = 8.0f;

    private static final float TRAVEL_TICKS = 7.0f;

    private static final float BUS_STEP = 1.0f;

    public UniversalPowerCouplerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(UniversalPowerCouplerBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        final BlockState state = be.getBlockState();
        final Direction facing = state.hasProperty(UniversalPowerCouplerBlock.FACING)
                ? state.getValue(UniversalPowerCouplerBlock.FACING) : Direction.NORTH;
        final PowerCouplerMode mode = be.mode();
        final boolean pull = mode == PowerCouplerMode.PULL;

        renderSeamWire(be, ms, buffer, packedLight);

        ms.pushPose();
        applyFacing(ms, facing);

        final float shuttleZ = easedShuttleZ(be, pull, partialTick);
        drawShuttle(ms, buffer, state, packedLight, shuttleZ);

        if (!be.isLooped()) {
            final int crossing = pull ? be.importFePerTick() : be.exportFePerTick();
            if (crossing > 0) {
                drawBus(ms, buffer, state, pull ? OUR_SEAT_Z : THEIR_SEAT_Z);
            }
        }

        ms.popPose();
    }

    private static void renderSeamWire(UniversalPowerCouplerBE be, PoseStack ms,
                                       MultiBufferSource buffer, int light) {
        if (be.links().isEmpty()) {
            return;
        }
        final net.minecraft.core.BlockPos origin = be.getBlockPos();
        final com.mojang.blaze3d.vertex.VertexConsumer consumer = PowerConnectorRenderer.wireBuffer(buffer);
        final org.joml.Matrix4f matrix = ms.last().pose();
        for (net.minecraft.core.BlockPos link : be.links()) {
            if (!PowerConnectorRenderer.drawsSpan(be.getLevel(), origin, link)) {
                continue;
            }
            final net.minecraft.world.phys.Vec3 from = CouplerSockets.anchor(be.getBlockState(),
                    CouplerSockets.endToward(be.getBlockState(), origin, link));

            final net.minecraft.world.phys.Vec3 to =
                    PowerConnectorRenderer.attachPoint(be.getLevel(), link, origin);
            PowerConnectorRenderer.drawCable(consumer, matrix, be.getLevel(), origin, link,
                    (float) from.x, (float) from.y, (float) from.z,
                    (float) (link.getX() - origin.getX() + to.x),
                    (float) (link.getY() - origin.getY() + to.y),
                    (float) (link.getZ() - origin.getZ() + to.z),
                    light, be.tierOf(link));
        }
    }

    private static float easedShuttleZ(UniversalPowerCouplerBE be, boolean pull, float partialTick) {
        final float to = pull ? OUR_SEAT_Z : THEIR_SEAT_Z;
        final float from = pull ? THEIR_SEAT_Z : OUR_SEAT_Z;
        final long changed = be.modeChangedAt();
        if (changed == Long.MIN_VALUE || be.getLevel() == null) {
            return to;
        }
        final float raw = Mth.clamp(
                (be.getLevel().getGameTime() - changed + partialTick) / TRAVEL_TICKS, 0.0f, 1.0f);
        final float smooth = raw * raw * (3.0f - 2.0f * raw);
        return from + (to - from) * smooth;
    }

    private static void drawShuttle(PoseStack ms, MultiBufferSource buffer, BlockState state,
                                    int light, float z) {
        ms.pushPose();
        ms.translate(0.0f, 0.0f, (z - SHUTTLE_HOME_Z) * PX);
        CachedBuffers.partial(ModPartialModels.UNIVERSAL_POWER_COUPLER_SHUTTLE, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
        ms.popPose();
    }

    private static void drawBus(PoseStack ms, MultiBufferSource buffer, BlockState state, float seat) {
        final float lo = Math.min(SEAM_Z, seat);
        final float hi = Math.max(SEAM_Z, seat);
        for (float z = lo; z < hi - 0.2f; z += BUS_STEP) {
            ms.pushPose();
            ms.translate(0.0f, 0.0f, z * PX);
            CachedBuffers.partial(ModPartialModels.UNIVERSAL_POWER_COUPLER_LINK, state)
                    .light(LightTexture.FULL_BRIGHT)
                    .renderInto(ms, buffer.getBuffer(RenderType.solid()));
            ms.popPose();
        }
    }

    private static void applyFacing(PoseStack ms, Direction facing) {
        final float yDeg = switch (facing) {
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
        if (yDeg == 0.0f) {
            return;
        }
        ms.translate(0.5f, 0.5f, 0.5f);
        ms.mulPose(Axis.YP.rotationDegrees(-yDeg));
        ms.translate(-0.5f, -0.5f, -0.5f);
    }

    @Override
    public boolean shouldRenderOffScreen(UniversalPowerCouplerBE be) {
        return !be.links().isEmpty();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(UniversalPowerCouplerBE be) {
        return PowerConnectorRenderer.wireBounds(be.getBlockPos(), be.links());
    }

    @Override
    public int getViewDistance() {
        return ModConfiguration.cappedViewDistance(48);
    }
}
