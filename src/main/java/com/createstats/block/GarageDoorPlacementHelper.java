package com.createstats.block;

import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Predicate;

// the hood is round, the face under the crosshair is usually a 45 degree facet. hence the ghost
public class GarageDoorPlacementHelper implements IPlacementHelper {
    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof GarageDoorHeaderBlock;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> state.getBlock() instanceof GarageDoorHeaderBlock;
    }

    @Override
    public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos,
                                     BlockHitResult ray) {
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return PlacementOffset.fail();
        }
        final Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        final Direction axis = GarageDoorSite.widthAxis(facing);

        for (Direction dir : IPlacementHelper.orderedByDistanceOnlyAxis(pos, ray.getLocation(),
                axis.getAxis())) {
            final BlockPos target = firstFree(world, pos, dir, facing);
            if (target == null || !world.getBlockState(target).canBeReplaced()) {
                continue;
            }

            return PlacementOffset.success((Vec3i) target,
                    ghost -> ghost.hasProperty(HorizontalDirectionalBlock.FACING)
                            ? ghost.setValue(HorizontalDirectionalBlock.FACING, facing)
                            : ghost);
        }
        return PlacementOffset.fail();
    }

    private static BlockPos firstFree(Level world, BlockPos from, Direction dir, Direction facing) {
        int run = runLength(world, from, facing);
        if (run >= GarageDoorFrame.MAX_WIDTH) {
            return null;
        }
        BlockPos check = from.relative(dir);
        for (int i = 0; i < GarageDoorFrame.MAX_WIDTH; i++) {
            if (!isHood(world, check, facing)) {
                return check;
            }
            check = check.relative(dir);
        }
        return null;
    }

    private static int runLength(Level world, BlockPos from, Direction facing) {
        final Direction axis = GarageDoorSite.widthAxis(facing);
        int run = 1;
        for (int sign = -1; sign <= 1; sign += 2) {
            for (int step = 1; step <= GarageDoorFrame.MAX_WIDTH; step++) {
                if (!isHood(world, from.relative(axis, sign * step), facing)) {
                    break;
                }
                run++;
            }
        }
        return run;
    }

    private static boolean isHood(Level world, BlockPos pos, Direction facing) {
        final BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof GarageDoorHeaderBlock
                && state.hasProperty(HorizontalDirectionalBlock.FACING)
                && state.getValue(HorizontalDirectionalBlock.FACING) == facing;
    }
}
