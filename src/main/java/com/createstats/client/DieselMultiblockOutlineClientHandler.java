package com.createstats.client;

import com.createstats.compat.DieselCompatHelper;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DieselMultiblockOutlineClientHandler {
    private static final Object GROUP_SLOT = new Object();
    private static final float LINE_WIDTH = 1 / 14f;
    private static int forcedPreviewTicks;
    private static AABB forcedBounds;
    private static boolean previewVisible;

    private DieselMultiblockOutlineClientHandler() {
    }

    public static void showFor(BlockEntity sourceBE, int ticks) {
        final AABB bounds = DieselCompatHelper.getDieselGroupBounds(sourceBE);
        if (bounds == null) {
            return;
        }
        forcedBounds = bounds.inflate(0.03);
        forcedPreviewTicks = Math.max(forcedPreviewTicks, ticks);
        show(forcedBounds, 0x42E3D4);
    }

    public static void tick() {
        if (forcedPreviewTicks > 0 && forcedBounds != null) {
            forcedPreviewTicks--;
            show(forcedBounds, 0x42E3D4);
            return;
        }
        forcedBounds = null;

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult blockHit)) {
            clearPreview();
            return;
        }
        final Level level = minecraft.level;
        final BlockPos hoveredPos = blockHit.getBlockPos();
        if (!(level.getBlockEntity(hoveredPos) instanceof DisplayLinkBlockEntity displayLink)) {
            clearPreview();
            return;
        }
        final BlockEntity sourceBE = level.getBlockEntity(displayLink.getSourcePosition());
        if (!DieselCompatHelper.isDieselSourceBlockEntity(sourceBE)
                || !DieselCompatHelper.isMultiStructureMode(displayLink.getSourceConfig(), sourceBE)) {
            clearPreview();
            return;
        }
        final AABB bounds = DieselCompatHelper.getDieselGroupBounds(sourceBE);
        if (bounds == null) {
            clearPreview();
            return;
        }
        show(bounds.inflate(0.03), 0xFFC15E);
    }

    public static void clearPreview() {
        forcedPreviewTicks = 0;
        forcedBounds = null;
        if (!previewVisible) {
            return;
        }
        Outliner.getInstance().remove(GROUP_SLOT);
        previewVisible = false;
    }

    private static void show(AABB bounds, int color) {
        Outliner.getInstance().showAABB(GROUP_SLOT, bounds)
                .colored(color)
                .disableLineNormals()
                .lineWidth(LINE_WIDTH);
        previewVisible = true;
    }
}
