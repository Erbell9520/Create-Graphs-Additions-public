package com.createstats.block;

import com.createstats.blockentity.CapacitorBankBE;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class CapacitorBankPlacementHelper implements IPlacementHelper {
    private static final int SCAN_LIMIT = 512;

    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof CapacitorBankBlock;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> state.getBlock() instanceof CapacitorBankBlock;
    }

    @Override
    public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos,
                                     BlockHitResult ray) {
        final List<BlockPos> cluster = cluster(world, pos);
        if (cluster.isEmpty()) {
            return PlacementOffset.fail();
        }
        for (Direction dir : IPlacementHelper.orderedByDistance(pos, ray.getLocation())) {
            final BlockPos target = firstFree(world, pos, dir);
            if (target == null || !world.getBlockState(target).canBeReplaced()) {
                continue;
            }
            if (!merges(cluster, target)) {
                continue;
            }
            return PlacementOffset.success((Vec3i) target);
        }
        return PlacementOffset.fail();
    }

    private static BlockPos firstFree(Level world, BlockPos from, Direction dir) {
        final int limit = Math.max(CapacitorBankBE.maxFootprint(), CapacitorBankBE.maxHeight());
        BlockPos check = from.relative(dir);
        for (int i = 0; i < limit; i++) {
            if (!(world.getBlockState(check).getBlock() instanceof CapacitorBankBlock)) {
                return check;
            }
            check = check.relative(dir);
        }
        return null;
    }

    private static boolean merges(List<BlockPos> cluster, BlockPos target) {
        int minX = target.getX();
        int maxX = target.getX();
        int minY = target.getY();
        int maxY = target.getY();
        int minZ = target.getZ();
        int maxZ = target.getZ();
        for (BlockPos pos : cluster) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        final int footprint = CapacitorBankBE.maxFootprint();
        return maxX - minX + 1 <= footprint
                && maxZ - minZ + 1 <= footprint
                && maxY - minY + 1 <= CapacitorBankBE.maxHeight();
    }

    private static List<BlockPos> cluster(Level world, BlockPos from) {
        final List<BlockPos> found = new ArrayList<>();
        final Set<BlockPos> seen = new HashSet<>();
        final Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(from);
        queue.add(from);
        while (!queue.isEmpty() && found.size() < SCAN_LIMIT) {
            final BlockPos pos = queue.removeFirst();
            found.add(pos);
            for (Direction dir : Direction.values()) {
                final BlockPos next = pos.relative(dir);
                if (!seen.add(next)) {
                    continue;
                }
                if (world.getBlockState(next).getBlock() instanceof CapacitorBankBlock) {
                    queue.add(next);
                }
            }
        }
        return found;
    }
}
