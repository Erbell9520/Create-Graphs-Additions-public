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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Predicate;

public class BlastShutterPlacementHelper implements IPlacementHelper {
    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof BlastShutterBlock;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> state.getBlock() instanceof BlastShutterBlock;
    }

    @Override
    public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos,
                                     BlockHitResult ray) {
        final ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BlockItem item) || item.getBlock() != state.getBlock()) {
            return PlacementOffset.fail();
        }
        final Direction dir = BlastShutterBlock.aimedEnd(pos, ray);
        if (dir == null) {
            return PlacementOffset.fail();
        }
        final BlockPos target = BlastShutterBlock.growthTarget(world, pos, state, dir);
        if (target == null) {
            return PlacementOffset.fail();
        }

        final Direction facing = state.getValue(BlastShutterBlock.FACING);
        final ShutterMount mount = state.getValue(BlastShutterBlock.MOUNT);
        final boolean sealed = state.getValue(BlastShutterBlock.SEALED);
        final ShutterSegment segment =
                dir == Direction.UP ? ShutterSegment.UPPER : ShutterSegment.LOWER;
        return PlacementOffset.success((Vec3i) target, ghost ->
                ghost.getBlock() == state.getBlock()
                        ? ghost.setValue(BlastShutterBlock.FACING, facing)
                                .setValue(BlastShutterBlock.HALF, segment)
                                .setValue(BlastShutterBlock.MOUNT, mount)
                                .setValue(BlastShutterBlock.SEALED, sealed)
                        : ghost);
    }
}
