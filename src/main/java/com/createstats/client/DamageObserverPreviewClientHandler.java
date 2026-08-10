package com.createstats.client;

import com.createstats.blockentity.DamageObserverBE;
import com.createstats.blockentity.DamageObserverSettings;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DamageObserverPreviewClientHandler {

    private static final Object OBSERVER_SLOT = new Object();
    private static final Object WATCH_VOLUME_SLOT = new Object();
    private static final Object ANCHOR_SLOT = new Object();
    private static final float OBSERVER_LINE_WIDTH = 1 / 32f;
    private static final float WATCH_LINE_WIDTH = 1 / 14f;
    private static final float ANCHOR_LINE_WIDTH = 1 / 20f;

    private static boolean previewVisible;

    private DamageObserverPreviewClientHandler() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult blockHit)) {
            clearPreview();
            return;
        }

        Level level = minecraft.level;
        BlockPos hoveredPos = blockHit.getBlockPos();
        if (!(level.getBlockEntity(hoveredPos) instanceof DamageObserverBE observer)) {
            clearPreview();
            return;
        }

        AABB observerBounds = getHoveredBounds(level, hoveredPos).inflate(0.003);
        AABB watchedBounds = getWatchedBounds(observer).inflate(0.01);
        int color = categoryColor(observer.getSettings().getCategory());

        Outliner.getInstance().showAABB(OBSERVER_SLOT, observerBounds)
                .colored(0x4AD9FF)
                .disableLineNormals()
                .lineWidth(OBSERVER_LINE_WIDTH);

        Outliner.getInstance().showAABB(WATCH_VOLUME_SLOT, watchedBounds)
                .colored(color)
                .disableLineNormals()
                .lineWidth(WATCH_LINE_WIDTH);

        if (observer.getSettings().getWatchMode() != DamageObserverSettings.WatchMode.SINGLE_BLOCK) {
            Outliner.getInstance().showAABB(ANCHOR_SLOT, new AABB(observer.getObservedAnchorPos()).inflate(0.04))
                    .colored(0xFFC15E)
                    .disableLineNormals()
                    .lineWidth(ANCHOR_LINE_WIDTH);
        } else {
            Outliner.getInstance().remove(ANCHOR_SLOT);
        }

        previewVisible = true;
    }

    public static void clearPreview() {
        if (!previewVisible) {
            return;
        }
        Outliner.getInstance().remove(OBSERVER_SLOT);
        Outliner.getInstance().remove(WATCH_VOLUME_SLOT);
        Outliner.getInstance().remove(ANCHOR_SLOT);
        previewVisible = false;
    }

    private static AABB getWatchedBounds(DamageObserverBE observer) {
        BlockPos min = observer.getObservedMinPos();
        BlockPos max = observer.getObservedMaxPos();
        return new AABB(
            min.getX(),
            min.getY(),
            min.getZ(),
            max.getX() + 1,
            max.getY() + 1,
            max.getZ() + 1);
    }

    private static AABB getHoveredBounds(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            return new AABB(pos);
        }
        return shape.bounds().move(pos);
    }

    private static int categoryColor(DamageObserverSettings.ObserverCategory category) {
        return switch (category) {
            case HULL -> 0xFF5D69;
            case LIFT -> 0x4AD9FF;
            case POWER -> 0xFFC15E;
            case WEAPONS -> 0xFF7F50;
            case CONTROL -> 0x62E8A3;
            case CARGO -> 0xA985FF;
        };
    }
}