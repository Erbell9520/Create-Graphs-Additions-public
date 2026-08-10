package com.createstats.train.manifest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import com.createstats.CreateStats;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Wraps a PFI fluid handler to enforce manifest unload limits.
 * Resolves the manifest lazily on first drain attempt.
 */
public class ManifestAwareFluidHandler implements IFluidHandler {

    private final IFluidHandler wrapped;
    private final BlockEntity pfi;
    private final AbstractContraptionEntity contraptionEntity;

    private boolean resolved = false;
    private Map<ResourceLocation, Integer> limits;
    private UUID stationId;
    private UUID trainId;

    public ManifestAwareFluidHandler(IFluidHandler wrapped,
                                     BlockEntity pfi,
                                     AbstractContraptionEntity contraptionEntity) {
        this.wrapped = wrapped;
        this.pfi = pfi;
        this.contraptionEntity = contraptionEntity;
    }

    private boolean tryResolve() {
        if (this.resolved) {
            return this.limits != null;
        }

        if (!(this.contraptionEntity instanceof CarriageContraptionEntity carriageEntity)) {
            return false;
        }

        Carriage carriage = carriageEntity.getCarriage();
        if (carriage == null || carriage.train == null) {
            return false;
        }

        Train train = carriage.train;
        GlobalStation station = train.getCurrentStation();
        if (station == null) {
            return false;
        }

        Level level = this.pfi.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            this.resolved = true;
            return false;
        }

        UUID sid = station.getId();
        UUID tid = train.id;

        FreightManifest manifest;
        try {
            FreightManifestSavedData savedData = FreightManifestSavedData.get(serverLevel);
            manifest = savedData.getManifest(sid);
        } catch (Exception e) {
            this.resolved = true;
            return false;
        }

        if (manifest == null || manifest.isEmpty()) {
            this.resolved = true;
            return false;
        }

        Map<ResourceLocation, Integer> lim = new HashMap<>();
        for (FreightManifestLine line : manifest.getLines()) {
            if (line.getOperation() == ManifestOperation.UNLOAD
                && line.getCargoKind() == ManifestCargoKind.FLUID
                && line.getResourceId() != null
                && line.getAmount() > 0) {
                lim.merge(line.getResourceId(), line.getAmount(), Integer::sum);
            }
        }

        if (lim.isEmpty()) {
            this.resolved = true;
            return false;
        }

        this.limits = lim;
        this.stationId = sid;
        this.trainId = tid;
        this.resolved = true;

        ManifestUnloadTracker.registerInterface(this.pfi.getBlockPos(), sid);
        CreateStats.LOGGER.info("[ManifestFluidHandler] Resolved manifest for train '{}' at '{}': {}",
            train.name.getString(), station.name, lim);
        return true;
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
        return this.wrapped.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return this.wrapped.fill(resource, action);
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (!tryResolve()) {
            return this.wrapped.drain(resource, action);
        }

        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }

        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(resource.getFluid());
        if (fluidId == null) {
            return FluidStack.EMPTY;
        }

        Integer limit = this.limits.get(fluidId);
        if (limit == null) {
            return FluidStack.EMPTY;
        }

        ManifestUnloadTracker.StationVisitState state =
            ManifestUnloadTracker.getOrCreate(this.stationId, this.trainId);
        int alreadyDrained = state.getDrainedFluids(fluidId);
        int remaining = limit - alreadyDrained;

        if (remaining <= 0) {
            return FluidStack.EMPTY;
        }

        int toDrain = Math.min(resource.getAmount(), remaining);
        FluidStack limited = resource.copyWithAmount(toDrain);
        FluidStack result = this.wrapped.drain(limited, action);

        if (action.execute() && !result.isEmpty()) {
            state.recordFluidDrain(fluidId, result.getAmount());
        }

        return result;
    }

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (!tryResolve()) {
            return this.wrapped.drain(maxDrain, action);
        }

        FluidStack peek = this.wrapped.drain(maxDrain, FluidAction.SIMULATE);
        if (peek.isEmpty()) {
            return FluidStack.EMPTY;
        }

        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(peek.getFluid());
        if (fluidId == null) {
            return FluidStack.EMPTY;
        }

        Integer limit = this.limits.get(fluidId);
        if (limit == null) {
            return FluidStack.EMPTY;
        }

        ManifestUnloadTracker.StationVisitState state =
            ManifestUnloadTracker.getOrCreate(this.stationId, this.trainId);
        int alreadyDrained = state.getDrainedFluids(fluidId);
        int remaining = limit - alreadyDrained;

        if (remaining <= 0) {
            return FluidStack.EMPTY;
        }

        int toDrain = Math.min(maxDrain, remaining);
        FluidStack result = this.wrapped.drain(toDrain, action);

        if (action.execute() && !result.isEmpty()) {
            state.recordFluidDrain(fluidId, result.getAmount());
        }

        return result;
    }
}
