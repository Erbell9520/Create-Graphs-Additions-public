package com.createstats.train.manifest;

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class ManifestAwareFluidHandler implements IFluidHandler {
    private final IFluidHandler wrapped;
    private final BlockEntity pfi;
    private final AbstractContraptionEntity contraptionEntity;

    private boolean resolved;
    private ServerLevel stationLevel;
    private UUID stationId;
    private Train train;

    private ManifestAllowance cachedUnload;
    private ManifestAllowance cachedLoad;
    private long cachedAtTick = Long.MIN_VALUE;

    public ManifestAwareFluidHandler(IFluidHandler wrapped,
                                     BlockEntity pfi,
                                     AbstractContraptionEntity contraptionEntity) {
        this.wrapped = wrapped;
        this.pfi = pfi;
        this.contraptionEntity = contraptionEntity;
    }

    private boolean tryResolve() {
        if (this.resolved) {
            return this.stationId != null;
        }

        if (!(this.contraptionEntity instanceof CarriageContraptionEntity carriageEntity)) {
            return false;
        }

        Carriage carriage = carriageEntity.getCarriage();
        if (carriage == null || carriage.train == null) {
            return false;
        }

        Train resolvedTrain = carriage.train;
        GlobalStation station = resolvedTrain.getCurrentStation();
        if (station == null) {
            return false;
        }

        Level level = this.pfi.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            this.resolved = true;
            return false;
        }

        this.stationLevel = serverLevel;
        this.stationId = station.getId();
        this.train = resolvedTrain;
        this.resolved = true;

        ManifestUnloadTracker.registerInterface(this.pfi.getBlockPos(), this.stationId);
        return true;
    }

    @Nullable
    private ManifestAllowance allowance(ManifestOperation operation) {
        if (this.stationLevel == null || this.stationId == null) {
            return null;
        }
        long now = this.stationLevel.getGameTime();
        if (now != this.cachedAtTick) {
            this.cachedAtTick = now;
            this.cachedUnload = null;
            this.cachedLoad = null;
        }
        if (operation == ManifestOperation.UNLOAD) {
            if (this.cachedUnload == null) {
                this.cachedUnload = ManifestAllowance.capture(this.stationLevel, this.stationId, this.train,
                    ManifestOperation.UNLOAD, ManifestCargoKind.FLUID);
            }
            return this.cachedUnload;
        }
        if (this.cachedLoad == null) {
            this.cachedLoad = ManifestAllowance.capture(this.stationLevel, this.stationId, this.train,
                ManifestOperation.LOAD, ManifestCargoKind.FLUID);
        }
        return this.cachedLoad;
    }

    private void recordMovement(ManifestAllowance allowance, FreightManifestResourceKey key,
                               ManifestOperation operation, int amount) {
        if (this.stationId == null || this.train == null || amount <= 0) {
            return;
        }
        ManifestUnloadTracker.getOrCreate(this.stationId, this.train.id).record(key, operation, amount);
        allowance.spend(key, amount);
    }

    @Nullable
    private static FreightManifestResourceKey keyOf(FluidStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return fluidId == null ? null : new FreightManifestResourceKey(ManifestCargoKind.FLUID, fluidId);
    }

    @Override
    public int getTanks() {
        return this.wrapped.getTanks();
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank) {
        return this.wrapped.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return this.wrapped.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        if (!this.wrapped.isFluidValid(tank, stack)) {
            return false;
        }
        if (stack.isEmpty() || !this.tryResolve()) {
            return true;
        }
        ManifestAllowance allowance = this.allowance(ManifestOperation.LOAD);
        if (allowance == null || !allowance.isRestricted()) {
            return true;
        }
        FreightManifestResourceKey key = keyOf(stack);
        return key != null && allowance.allowanceFor(key) > 0;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !this.tryResolve()) {
            return this.wrapped.fill(resource, action);
        }
        ManifestAllowance allowance = this.allowance(ManifestOperation.LOAD);
        if (allowance == null || !allowance.isRestricted()) {
            return this.wrapped.fill(resource, action);
        }

        FreightManifestResourceKey key = keyOf(resource);
        if (key == null) {
            return 0;
        }
        int permitted = allowance.allowanceFor(key);
        if (permitted <= 0) {
            return 0;
        }

        int filled = this.wrapped.fill(resource.copyWithAmount(Math.min(resource.getAmount(), permitted)), action);
        if (action.execute() && filled > 0) {
            this.recordMovement(allowance, key, ManifestOperation.LOAD, filled);
        }
        return filled;
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !this.tryResolve()) {
            return this.wrapped.drain(resource, action);
        }
        ManifestAllowance allowance = this.allowance(ManifestOperation.UNLOAD);
        if (allowance == null || !allowance.isRestricted()) {
            return this.wrapped.drain(resource, action);
        }

        FreightManifestResourceKey key = keyOf(resource);
        if (key == null) {
            return FluidStack.EMPTY;
        }
        int permitted = allowance.allowanceFor(key);
        if (permitted <= 0) {
            return FluidStack.EMPTY;
        }

        FluidStack result = this.wrapped.drain(resource.copyWithAmount(Math.min(resource.getAmount(), permitted)), action);
        if (action.execute() && !result.isEmpty()) {
            this.recordMovement(allowance, key, ManifestOperation.UNLOAD, result.getAmount());
        }
        return result;
    }

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0 || !this.tryResolve()) {
            return this.wrapped.drain(maxDrain, action);
        }
        ManifestAllowance allowance = this.allowance(ManifestOperation.UNLOAD);
        if (allowance == null || !allowance.isRestricted()) {
            return this.wrapped.drain(maxDrain, action);
        }

        FluidStack peek = this.wrapped.drain(maxDrain, FluidAction.SIMULATE);
        FreightManifestResourceKey key = keyOf(peek);
        if (key == null) {
            return FluidStack.EMPTY;
        }
        int permitted = allowance.allowanceFor(key);
        if (permitted <= 0) {
            return FluidStack.EMPTY;
        }

        FluidStack result = this.wrapped.drain(Math.min(maxDrain, permitted), action);

        if (action.execute() && !result.isEmpty()) {
            FreightManifestResourceKey drained = keyOf(result);
            if (drained != null) {
                this.recordMovement(allowance, drained, ManifestOperation.UNLOAD, result.getAmount());
            }
        }
        return result;
    }
}
