package com.createstats.display;

import com.createstats.blockentity.ProductionDetectorBE;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

// plain chests register no source so the link has to announce itself
public final class InventoryFeeders {
    private InventoryFeeders() {
    }

    public static boolean announce(DisplayLinkBlockEntity link) {
        if (!(link.getLevel() instanceof ServerLevel linkLevel) || link.isVirtual()) {
            return true;
        }
        BlockPos target = null;
        ResourceLocation dimension = null;
        if ((Object) link instanceof DisplayLinkDimensionExtension ext) {
            target = ext.createStats$getStoredTargetPos();
            dimension = ext.createStats$getTargetDimension();
        }
        if (target == null) {
            target = link.getTargetPosition();
        }
        if (target == null || target.equals(link.getBlockPos())) {
            return true;
        }
        ServerLevel targetLevel = linkLevel;
        if (dimension != null && !dimension.equals(linkLevel.dimension().location())) {
            targetLevel = linkLevel.getServer() == null ? null
                    : linkLevel.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        }
        if (targetLevel == null) {
            return true;
        }
        if (!targetLevel.isLoaded(target)) {
            return false;
        }
        final BlockState state = targetLevel.getBlockState(target);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            target = target.below();
        }
        if (targetLevel.getBlockEntity(target) instanceof ProductionDetectorBE display) {
            display.acceptFeeder(linkLevel.dimension(), link.getBlockPos());
        }
        return true;
    }
}
