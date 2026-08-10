package com.createstats.client;

import javax.annotation.Nullable;

import com.createstats.factorylogic.MachineSelectionCardData;
import com.createstats.item.MachineSelectionCardItem;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MachineSelectionCardClientHandler {

    private static final Object PREVIEW_SLOT = new Object();
    private static final int VALID_HOVER_COLOR = 0x5B8CFF;
    private static final int VALID_AREA_COLOR = 0x68C586;
    private static final int INVALID_COLOR = 0xFF5A5A;
    private static final float HOVER_LINE_WIDTH = 1 / 32f;
    private static final float AREA_LINE_WIDTH = 1 / 16f;

    private static boolean previewVisible;

    private MachineSelectionCardClientHandler() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            clearPreview();
            return;
        }

        ItemStack stack = getHeldCard(minecraft.player);
        if (stack.isEmpty()) {
            clearPreview();
            return;
        }

        if (!(minecraft.hitResult instanceof BlockHitResult blockHit)) {
            clearPreview();
            return;
        }

        Level level = minecraft.level;
        BlockPos hoveredPos = blockHit.getBlockPos();
        MachineSelectionCardData data = MachineSelectionCardItem.getData(stack);

        AABB previewBounds;
        int previewColor;
        float lineWidth;

        if (data.getFirstCorner() != null && data.getSecondCorner() == null) {
            lineWidth = AREA_LINE_WIDTH;
            if (data.hasDimensionMismatch(level.dimension().location())) {
                previewBounds = getHoveredBounds(level, hoveredPos);
                previewColor = INVALID_COLOR;
            } else {
                previewBounds = data.getSelectionBounds(hoveredPos);
                previewColor = data.isAreaTooLarge(MachineSelectionCardData.MAX_AREA_BLOCKS, hoveredPos)
                        ? INVALID_COLOR
                        : VALID_AREA_COLOR;
            }
        } else {
            previewBounds = getHoveredBounds(level, hoveredPos);
            previewColor = VALID_HOVER_COLOR;
            lineWidth = HOVER_LINE_WIDTH;
        }

        if (previewBounds == null) {
            clearPreview();
            return;
        }

        Outliner.getInstance().showAABB(PREVIEW_SLOT, previewBounds)
                .colored(previewColor)
                .disableLineNormals()
                .lineWidth(lineWidth);
        previewVisible = true;
    }

    public static void clearPreview() {
        if (!previewVisible) {
            return;
        }
        Outliner.getInstance().remove(PREVIEW_SLOT);
        previewVisible = false;
    }

    private static ItemStack getHeldCard(LocalPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof MachineSelectionCardItem) {
            return mainHand;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof MachineSelectionCardItem) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    private static AABB getHoveredBounds(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            return new AABB(pos);
        }
        return shape.bounds().move(pos);
    }
}
