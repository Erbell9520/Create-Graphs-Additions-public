package com.createstats.renderer;

import com.createstats.block.DoorEase;
import com.createstats.block.GarageDoorCurl;
import com.createstats.block.GarageDoorHeaderBlock;
import com.createstats.block.GarageDoorSite;
import com.createstats.blockentity.GarageDoorBE;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GarageDoorRenderer implements BlockEntityRenderer<GarageDoorBE> {
    private static final float PX = 1.0f / 16.0f;

    public GarageDoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(GarageDoorBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof GarageDoorHeaderBlock) || !be.formed()) {
            return;
        }
        final int w = be.width();
        final int h = be.height();

        final float openness = Mth.clamp(
                DoorEase.DRUM.curve(be.progress(partialTick), be.isOpen()), 0.0f, 1.0f);

        final Direction facing = state.getValue(GarageDoorHeaderBlock.FACING);
        final Direction axis = GarageDoorSite.widthAxis(facing);

        final int origin = be.origin();
        final BlockPos pos = be.getBlockPos().relative(axis, -origin);
        final float lintel = GarageDoorCurl.lintelPx(h);
        final int slats = GarageDoorCurl.slatCount(h);

        ms.pushPose();
        orient(ms, facing);

        ms.translate(-origin, 0.0f, 0.0f);

        drawBarrelEnds(ms, buffer, state, be, w, h, openness, pos, axis, light);

        if (openness >= 1.0f) {
            ms.popPose();
            return;
        }

        final int vision = GarageDoorCurl.visionCourse(h);

        for (int i = 0; i < slats; i++) {
            final float base = GarageDoorCurl.slatBasePx(i, openness, h);
            if (base >= lintel) {
                continue;
            }

            final PartialModel part = i == 0 ? ModPartialModels.GARAGE_DOOR_BOTTOM_BAR
                    : i == vision ? ModPartialModels.GARAGE_DOOR_SLAT_VISION
                    : ModPartialModels.GARAGE_DOOR_SLAT;

            final float y = -h + base * PX;
            for (int col = 0; col < w; col++) {
                final BlockPos at = pos.relative(axis, col)
                        .below(Math.max(1, Math.round(h - base * PX)));
                drawSlat(ms, buffer, state, part, col, y, lightAt(be, at, light));
            }
        }
        ms.popPose();
    }

    private static void drawBarrelEnds(PoseStack ms, MultiBufferSource buffer, BlockState state,
                                       GarageDoorBE be, int w, int h, float openness,
                                       BlockPos pos, Direction axis, int light) {
        final float angle = GarageDoorCurl.barrelAngle(GarageDoorCurl.woundPx(openness, h));
        drawCap(ms, buffer, state, ModPartialModels.GARAGE_DOOR_CAP_LEFT, 0, angle,
                lightAt(be, pos, light));
        if (w > 1) {
            drawCap(ms, buffer, state, ModPartialModels.GARAGE_DOOR_CAP_RIGHT, w - 1, angle,
                    lightAt(be, pos.relative(axis, w - 1), light));
        } else {
            drawCap(ms, buffer, state, ModPartialModels.GARAGE_DOOR_CAP_RIGHT, 0, angle,
                    lightAt(be, pos, light));
        }
    }

    private static void drawCap(PoseStack ms, MultiBufferSource buffer, BlockState state,
                                PartialModel part, int col, float angle, int light) {
        ms.pushPose();

        ms.translate(col + 0.5f, 0.5f, 0.5f);
        ms.mulPose(Axis.XP.rotation(angle));
        ms.translate(-0.5f, -0.5f, -0.5f);
        CachedBuffers.partial(part, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        ms.popPose();
    }

    private static void drawSlat(PoseStack ms, MultiBufferSource buffer, BlockState state,
                                 PartialModel part, float across, float y, int light) {
        ms.pushPose();
        ms.translate(across, y, 0.0f);
        CachedBuffers.partial(part, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        ms.popPose();
    }

    private static int lightAt(GarageDoorBE be, BlockPos at, int fallback) {
        return be.getLevel() == null ? fallback : LevelRenderer.getLightColor(be.getLevel(), at);
    }

    private static void orient(PoseStack ms, Direction facing) {
        ms.translate(0.5, 0.5, 0.5);
        switch (facing) {
            case SOUTH -> ms.mulPose(Axis.YP.rotationDegrees(180.0f));
            case WEST -> ms.mulPose(Axis.YP.rotationDegrees(90.0f));
            case EAST -> ms.mulPose(Axis.YP.rotationDegrees(270.0f));
            default -> {
            }
        }
        ms.translate(-0.5, -0.5, -0.5);
    }

    @Override
    public AABB getRenderBoundingBox(GarageDoorBE be) {
        final BlockPos pos = be.getBlockPos();
        final int w = Math.max(1, be.width()) + 1;
        final int h = Math.max(1, be.height());

        return new AABB(pos.getX() - w, pos.getY() - h, pos.getZ() - w,
                pos.getX() + w + 1, pos.getY() + 1, pos.getZ() + w + 1);
    }

    @Override
    public boolean shouldRenderOffScreen(GarageDoorBE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }
}
