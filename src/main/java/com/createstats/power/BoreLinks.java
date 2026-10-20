package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// minecraft holds one block per position, so a bore is the only way through
public final class BoreLinks {
    private BoreLinks() {
    }

    private static final String TAG = "Bores";

    public interface Socket extends Host {
    }

    public interface Host {
        BlockPos borePosition();

        default BlockPos boreAnchorFor(BlockPos far) {
            return borePosition();
        }

        List<BlockPos> bores();

        boolean addBore(BlockPos far);

        boolean removeBore(BlockPos far);

        void invalidate(Level level);
    }

    public static void save(CompoundTag tag, List<BlockPos> bores) {
        if (bores.isEmpty()) {
            return;
        }
        final ListTag list = new ListTag();
        for (BlockPos far : bores) {
            final CompoundTag entry = new CompoundTag();
            entry.putLong("At", far.asLong());
            list.add(entry);
        }
        tag.put(TAG, list);
    }

    public static List<BlockPos> load(CompoundTag tag) {
        if (!tag.contains(TAG)) {
            return new ArrayList<>(0);
        }
        final ListTag list = tag.getList(TAG, net.minecraft.nbt.Tag.TAG_COMPOUND);
        final List<BlockPos> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(BlockPos.of(list.getCompound(i).getLong("At")));
        }
        return out;
    }

    @Nullable
    public static Direction axisBetween(BlockPos from, BlockPos to) {
        final int dx = to.getX() - from.getX();
        final int dy = to.getY() - from.getY();
        final int dz = to.getZ() - from.getZ();
        final int nonZero = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
        if (nonZero != 1) {
            return null;
        }
        final int span = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        if (span < 2 || span > IndoorCable.MAX_BORE + 1) {
            return null;
        }
        return Direction.fromDelta(Integer.signum(dx), Integer.signum(dy), Integer.signum(dz));
    }

    public static List<BlockPos> crossings(BlockPos from, BlockPos to) {
        final Direction dir = axisBetween(from, to);
        if (dir == null) {
            return List.of();
        }
        final int span = Math.abs(to.getX() - from.getX()) + Math.abs(to.getY() - from.getY())
                + Math.abs(to.getZ() - from.getZ());
        final List<BlockPos> out = new ArrayList<>(span - 1);
        BlockPos at = from.relative(dir);
        for (int i = 1; i < span; i++) {
            out.add(at);
            at = at.relative(dir);
        }
        return out;
    }

    public static BlockPos anchorFor(BlockPos far, BlockPos primary, BlockPos secondary) {
        final boolean fromPrimary = axisBetween(primary, far) != null;
        final boolean fromSecondary = axisBetween(secondary, far) != null;
        if (fromPrimary && fromSecondary) {
            return far.distSqr(secondary) < far.distSqr(primary) ? secondary : primary;
        }
        return fromSecondary ? secondary : primary;
    }

    public static boolean valid(LevelReader level, BlockPos from, BlockPos to) {
        if (axisBetween(from, to) == null) {
            return false;
        }
        if (!level.hasChunkAt(from) || !level.hasChunkAt(to)) {
            return true;
        }
        if (!samePair(level, from, to)) {
            return false;
        }
        boolean throughSomething = false;
        for (BlockPos at : crossings(from, to)) {
            if (!level.hasChunkAt(at)) {
                return true;
            }
            final BlockState state = level.getBlockState(at);
            if (state.isAir() || state.canBeReplaced()) {
                continue;
            }
            if (level instanceof Level lvl && state.getDestroySpeed(lvl, at) < 0) {
                return false;
            }
            throughSomething = true;
        }
        return throughSomething;
    }

    private static boolean samePair(LevelReader level, BlockPos from, BlockPos to) {
        final Host near = hostAt(level, from);
        final Host far = hostAt(level, to);
        if (near == null || far == null) {
            return false;
        }
        final boolean nearSocket = near instanceof Socket;
        final boolean farSocket = far instanceof Socket;
        if (nearSocket && farSocket) {
            return false;
        }
        if (nearSocket || farSocket) {
            return true;
        }
        return level.getBlockState(from).getBlock() == level.getBlockState(to).getBlock();
    }

    @Nullable
    public static Host hostAt(LevelReader level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Host host) {
            return host;
        }
        if (be != null) {
            return null;
        }

        final Host door = com.createstats.block.PoweredDoorBlock.boreHostAt(level, pos);
        if (door != null) {
            return door;
        }
        final Host shutter = com.createstats.block.BlastShutterBlock.boreHostAt(level, pos);
        return shutter != null ? shutter
                : com.createstats.block.GarageDoorHeaderBlock.frameHostAt(
                        level, pos, level.getBlockState(pos));
    }

    @Nullable
    public static BlockPos findPartner(LevelReader level, BlockPos from, Direction dir) {
        BlockPos at = from.relative(dir);
        for (int i = 0; i <= IndoorCable.MAX_BORE; i++) {
            if (!level.hasChunkAt(at)) {
                return null;
            }
            if (i > 0 && samePair(level, from, at) && valid(level, from, at)) {
                return at;
            }
            at = at.relative(dir);
        }
        return null;
    }

    public static List<BlockPos> ends(LevelReader level, BlockPos a, BlockPos b) {
        final Host near = hostAt(level, a);
        final Host far = hostAt(level, b);
        if (near == null || far == null) {
            return List.of();
        }
        final BlockPos nearEnd = near.boreAnchorFor(b).immutable();
        return List.of(nearEnd, far.boreAnchorFor(nearEnd).immutable());
    }

    public static boolean join(Level level, BlockPos a, BlockPos b) {
        final List<BlockPos> ends = ends(level, a, b);
        if (ends.isEmpty() || !valid(level, ends.get(0), ends.get(1))) {
            return false;
        }
        final Host near = hostAt(level, ends.get(0));
        final Host far = hostAt(level, ends.get(1));
        if (near == null || far == null) {
            return false;
        }
        final boolean changed = near.addBore(ends.get(1)) | far.addBore(ends.get(0));
        if (changed) {
            near.invalidate(level);
            far.invalidate(level);
        }
        return changed;
    }

    public static boolean part(Level level, BlockPos a, BlockPos b) {
        final Host near = hostAt(level, a);
        final Host far = hostAt(level, b);
        final BlockPos nearEnd = near == null ? a : near.boreAnchorFor(b);
        boolean changed = false;
        if (near != null) {
            changed |= near.removeBore(b);
            near.invalidate(level);
        }
        if (far != null) {
            changed |= far.removeBore(nearEnd);
            far.invalidate(level);
        }
        return changed;
    }

    public static void severAll(Level level, Host host) {
        for (BlockPos far : new ArrayList<>(host.bores())) {
            final Host other = hostAt(level, far);
            if (other != null) {
                other.removeBore(host.boreAnchorFor(far));
                other.invalidate(level);
            }
            host.removeBore(far);
        }
    }

    public static boolean prune(Level level, Host host) {
        boolean changed = false;
        for (BlockPos far : new ArrayList<>(host.bores())) {
            if (!level.isLoaded(far)) {
                continue;
            }
            if (!valid(level, host.boreAnchorFor(far), far)) {
                changed |= host.removeBore(far);
                final Host other = hostAt(level, far);
                if (other != null) {
                    other.removeBore(host.boreAnchorFor(far));
                    other.invalidate(level);
                }
            }
        }
        if (changed) {
            host.invalidate(level);
        }
        return changed;
    }
}
