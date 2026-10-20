package com.createstats.train.manifest;

import java.util.UUID;
import javax.annotation.Nullable;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

// a direction the manifest says nothing about is left alone
public class ManifestAwareItemHandler implements IItemHandlerModifiable {
    private final IItemHandlerModifiable wrapped;
    private final BlockEntity psi;
    private final AbstractContraptionEntity contraptionEntity;

    private boolean resolved;
    private ServerLevel stationLevel;
    private UUID stationId;
    private Train train;

    private ManifestAllowance cachedUnload;
    private ManifestAllowance cachedLoad;
    private long cachedAtTick = Long.MIN_VALUE;

    public ManifestAwareItemHandler(IItemHandlerModifiable wrapped,
                                    BlockEntity psi,
                                    AbstractContraptionEntity contraptionEntity) {
        this.wrapped = wrapped;
        this.psi = psi;
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

        Level level = this.psi.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            this.resolved = true;
            return false;
        }

        this.stationLevel = serverLevel;
        this.stationId = station.getId();
        this.train = resolvedTrain;
        this.resolved = true;

        ManifestUnloadTracker.registerInterface(this.psi.getBlockPos(), this.stationId);
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
                    ManifestOperation.UNLOAD, ManifestCargoKind.ITEM);
            }
            return this.cachedUnload;
        }
        if (this.cachedLoad == null) {
            this.cachedLoad = ManifestAllowance.capture(this.stationLevel, this.stationId, this.train,
                ManifestOperation.LOAD, ManifestCargoKind.ITEM);
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
    private static FreightManifestResourceKey keyOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId == null ? null : new FreightManifestResourceKey(ManifestCargoKind.ITEM, itemId);
    }

    @Override
    public int getSlots() {
        return this.wrapped.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.wrapped.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !this.tryResolve()) {
            return this.wrapped.insertItem(slot, stack, simulate);
        }
        ManifestAllowance allowance = this.allowance(ManifestOperation.LOAD);
        if (allowance == null || !allowance.isRestricted()) {
            return this.wrapped.insertItem(slot, stack, simulate);
        }

        FreightManifestResourceKey key = keyOf(stack);
        if (key == null) {
            return stack;
        }
        int permitted = allowance.allowanceFor(key);
        if (permitted <= 0) {
            return stack;
        }

        int offered = Math.min(stack.getCount(), permitted);
        int withheld = stack.getCount() - offered;
        ItemStack remainder = this.wrapped.insertItem(slot, stack.copyWithCount(offered), simulate);
        int accepted = offered - remainder.getCount();
        if (!simulate && accepted > 0) {
            this.recordMovement(allowance, key, ManifestOperation.LOAD, accepted);
        }
        int rejected = remainder.getCount() + withheld;
        return rejected <= 0 ? ItemStack.EMPTY : stack.copyWithCount(rejected);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!this.tryResolve()) {
            return this.wrapped.extractItem(slot, amount, simulate);
        }
        ManifestAllowance allowance = this.allowance(ManifestOperation.UNLOAD);
        if (allowance == null || !allowance.isRestricted()) {
            return this.wrapped.extractItem(slot, amount, simulate);
        }

        ItemStack inSlot = this.wrapped.getStackInSlot(slot);
        FreightManifestResourceKey key = keyOf(inSlot);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        int permitted = allowance.allowanceFor(key);
        if (permitted <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack result = this.wrapped.extractItem(slot, Math.min(amount, permitted), simulate);
        if (!simulate && !result.isEmpty()) {
            this.recordMovement(allowance, key, ManifestOperation.UNLOAD, result.getCount());
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.wrapped.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!this.wrapped.isItemValid(slot, stack)) {
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
    public void setStackInSlot(int slot, ItemStack stack) {
        this.wrapped.setStackInSlot(slot, stack);
    }
}
