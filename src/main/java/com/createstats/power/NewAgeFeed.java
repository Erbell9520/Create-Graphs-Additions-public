package com.createstats.power;

import com.createstats.compat.NewAgeCompat;
import com.createstats.compat.NewAgeCompatHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class NewAgeFeed {
    private static volatile Set<Block> connectorBlocks;

    private NewAgeFeed() {
    }

    private static Set<Block> connectorBlocks() {
        Set<Block> resolved = connectorBlocks;
        if (resolved != null) {
            return resolved;
        }
        resolved = new HashSet<>(2);

        for (String path : new String[] {"electrical_connector", "street_light"}) {
            final Block block = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath(NewAgeCompat.MOD_ID, path));

            if (block != null && !block.defaultBlockState().isAir()) {
                resolved.add(block);
            }
        }
        connectorBlocks = resolved;
        return resolved;
    }

    public static boolean isFeedOn(@Nullable Level level, BlockPos pos, BlockPos supported) {
        if (level == null || !level.isLoaded(pos)
                || !connectorBlocks().contains(level.getBlockState(pos).getBlock())) {
            return false;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (!NewAgeCompatHelper.isElectricalConnector(be)) {
            return false;
        }
        return supported.equals(NewAgeCompatHelper.supportingPosOf(be));
    }

    public static boolean isSendOnlyBridge(@Nullable Level level, BlockPos pos) {
        if (!NewAgeCompat.isAvailable() || level == null || !level.isLoaded(pos)
                || !connectorBlocks().contains(level.getBlockState(pos).getBlock())) {
            return false;
        }
        return NewAgeCompatHelper.isElectricalConnector(level.getBlockEntity(pos));
    }

    public static boolean connectorWithoutCoupler(@Nullable Level level, BlockPos supported) {
        if (!NewAgeCompat.isAvailable() || level == null) {
            return false;
        }
        for (Direction face : Direction.values()) {
            if (isFeedOn(level, supported.relative(face), supported)) {
                return true;
            }
        }
        return false;
    }

    public static boolean surveyCouplerSeam(@Nullable Level level, BlockPos couplerPos,
                                            Direction seamFace,
                                            PduSupplySurvey.Builder builder, Set<Integer> seen) {
        if (level == null
                || !(level.getBlockEntity(couplerPos)
                        instanceof com.createstats.blockentity.UniversalPowerCouplerBE coupler)
                || coupler.mode() != PowerCouplerMode.PULL) {
            return false;
        }

        final BlockPos wired = coupler.wiredSeamPos();
        final BlockPos endpointPos = wired != null ? wired : couplerPos.relative(seamFace);
        final boolean served = wired != null
                ? com.createstats.blockentity.UniversalPowerCouplerBE.isSeamTarget(level, wired)
                : isForeignSeam(level, couplerPos, seamFace);
        if (!served) {
            return false;
        }
        final long key = couplerPos.asLong();
        final int cap = com.createstats.blockentity.UniversalPowerCouplerBE.RATE_FE_PER_TICK;
        final BlockEntity endpoint = level.isLoaded(endpointPos)
                ? level.getBlockEntity(endpointPos) : null;
        final NewAgeCompatHelper.NetworkView view =
                NewAgeCompat.isAvailable() ? NewAgeCompatHelper.surveyNetwork(endpoint) : null;
        if (view != null && seen.add(view.networkId())) {
            int theirGen = 0;
            for (NewAgeCompatHelper.Source source : view.sources()) {
                theirGen += Math.max(0, source.fePerTick());
                builder.foldSourceFingerprint(source.pos().asLong());
            }
            builder.addSource(key, Math.min(cap, theirGen));
            for (NewAgeCompatHelper.Tank tank : view.tanks()) {
                if (tank.spendable()) {
                    builder.addTank(tank.pos().asLong(), FeReserve.DEFAULT_RANK,
                            tank.stored(), tank.capacity());
                }
            }
            return true;
        }

        builder.addSource(key, Math.min(cap, Math.max(0, coupler.importFePerTick())));
        return true;
    }

    public static boolean refusesSide(@Nullable BlockEntity be, @Nullable Direction side) {
        if (be == null) {
            return false;
        }
        return refusesSideAt(be.getLevel(), be.getBlockPos(), side);
    }

    public static boolean refusesSideAt(@Nullable Level level, BlockPos pos,
                                        @Nullable Direction side) {
        if (!NewAgeCompat.isAvailable() || side == null || level == null) {
            return false;
        }
        return isFeedOn(level, pos.relative(side), pos);
    }

    public static boolean connectorOnNonForeignFace(@Nullable Level level, BlockPos couplerPos,
                                                    Direction foreignFace) {
        if (!NewAgeCompat.isAvailable() || level == null) {
            return false;
        }
        for (Direction face : Direction.values()) {
            if (face != foreignFace && isFeedOn(level, couplerPos.relative(face), couplerPos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isForeignSeam(@Nullable Level level, BlockPos couplerPos,
                                        Direction foreignFace) {
        if (level == null) {
            return false;
        }
        final BlockPos at = couplerPos.relative(foreignFace);
        if (NewAgeCompat.isAvailable() && isFeedOn(level, at, couplerPos)) {
            return true;
        }
        if (!level.isLoaded(at)) {
            return false;
        }
        return level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                        at, foreignFace.getOpposite()) != null
                || level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                        at, null) != null;
    }

    public static IEnergyStorage mouth(BlockEntity connector, IEnergyStorage handler) {
        return new Mouth(connector, handler);
    }

    public static IEnergyStorage seamWireMouth(BlockEntity connector, IEnergyStorage handler) {
        return new SeamWireMouth(connector, handler);
    }

    public static boolean isNetworkMouth(@Nullable BlockEntity be, @Nullable IEnergyStorage handler) {
        return NewAgeCompat.isAvailable() && handler != null
                && NewAgeCompatHelper.isElectricalConnector(be)
                && (NewAgeCompatHelper.isNetworkConduit(be)
                        || NewAgeCompatHelper.isNetworkMouthHandler(handler));
    }

    private record Mouth(BlockEntity connector, IEnergyStorage handler) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) {
                return 0;
            }
            if (!simulate) {
                return handler.receiveEnergy(maxReceive, false);
            }

            return Math.min(maxReceive, NewAgeCompatHelper.acceptanceFePerTick(connector, null));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return handler.canReceive();
        }
    }

    private record SeamWireMouth(BlockEntity connector, IEnergyStorage handler) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) {
                return 0;
            }
            if (!simulate) {
                return handler.receiveEnergy(maxReceive, false);
            }
            return Math.min(maxReceive, NewAgeCompatHelper.acceptanceFePerTick(connector, null));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return NewAgeCompatHelper.drainNetwork(connector, maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return handler.canReceive();
        }
    }
}
