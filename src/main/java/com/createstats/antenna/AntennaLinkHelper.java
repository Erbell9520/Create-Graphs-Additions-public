package com.createstats.antenna;

import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

public final class AntennaLinkHelper {
    private AntennaLinkHelper() {
    }

    public static AntennaTier getAttachedTier(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof StatsDisplayPanelBE panel) {
            return panel.getControllerBE().getAntennaTier();
        }
        if (blockEntity instanceof DispatchBoardBE board) {
            return board.getControllerBE().getAntennaTier();
        }
        if (blockEntity instanceof RangeAttachment attachment) {
            return attachment.getAntennaTier();
        }
        return AntennaTier.NONE;
    }

    public static AntennaTier getAttachedTier(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return AntennaTier.NONE;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                blockEntity = level.getBlockEntity(pos.below());
            }
        }
        return getAttachedTier(blockEntity);
    }

    public static boolean canUseDisplayLinkAntenna(Level linkLevel, BlockPos linkPos, BlockPos targetPos,
                                                   ResourceLocation targetDimension, double baseRange) {
        return canUseDisplayLinkAntenna(linkLevel, linkPos, targetPos, targetDimension, baseRange, null);
    }

    public static boolean canUseDisplayLinkAntenna(Level linkLevel, BlockPos linkPos, BlockPos targetPos,
                                                   ResourceLocation targetDimension, double baseRange,
                                                   @Nullable AntennaTier cachedTier) {
        if (linkLevel == null || linkPos == null || targetPos == null || targetDimension == null) {
            return false;
        }
        ResourceLocation linkDimension = linkLevel.dimension().location();
        boolean crossDimensional = !targetDimension.equals(linkDimension);
        if (crossDimensional && cachedTier != null && cachedTier.isCrossDimensional()
                && AntennaRange.isOverworldNetherPair(linkDimension, targetDimension)) {
            return true;
        }
        if (linkLevel.isClientSide()) {
            AntennaTier previewTier = cachedTier;
            if (crossDimensional) {
                return previewTier == null
                        ? true
                        : previewTier.isCrossDimensional() && AntennaRange.isOverworldNetherPair(linkDimension, targetDimension);
            }
            // same reach test as the server or the preview lies. no tier yet = assume TRT

            AntennaTier effectiveTier = previewTier == null ? AntennaTier.TRT : previewTier;
            boolean isNether = Level.NETHER.location().equals(linkDimension);
            return AntennaRange.withinRangeSq(linkPos.distSqr(targetPos), baseRange, effectiveTier, isNether);
        }
        if (!(linkLevel instanceof ServerLevel sourceLevel) || sourceLevel.getServer() == null) {
            return false;
        }
        ServerLevel targetLevel = sourceLevel.getServer().getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, targetDimension));
        if (targetLevel == null) {
            return false;
        }
        boolean targetLoaded = AntennaRange.isChunkLoaded(targetLevel, targetPos);
        AntennaTier tier = targetLoaded ? getAttachedTier(targetLevel, targetPos) : cachedTier;
        if (tier == null) {
            return false;
        }
        if (tier == AntennaTier.NONE && cachedTier != null && cachedTier.isAttached()) {
            tier = cachedTier;
        }
        if (!targetLoaded && crossDimensional) {
            return tier.isCrossDimensional()
                    && AntennaRange.isOverworldNetherPair(sourceLevel.dimension(), targetLevel.dimension());
        }
        if (crossDimensional) {
            return AntennaRange.canReach(sourceLevel, linkPos, targetLevel, targetPos, tier);
        }
        return AntennaRange.canReach(sourceLevel, linkPos, targetLevel, targetPos, tier, baseRange);
    }

    public static boolean requiresAntennaForSameDimension(Level linkLevel, BlockPos linkPos, BlockPos targetPos, double baseRange) {
        if (linkLevel == null || linkPos == null || targetPos == null) {
            return false;
        }
        return linkPos.distSqr(targetPos) > baseRange * baseRange;
    }
}
