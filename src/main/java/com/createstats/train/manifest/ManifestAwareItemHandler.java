package com.createstats.train.manifest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.createstats.CreateStats;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Wraps a PSI item handler to enforce manifest unload limits.
 * Resolves the manifest lazily on first extraction attempt,
 * since the train's station may not be set when PSI first connects.
 */
public class ManifestAwareItemHandler implements IItemHandlerModifiable {

    private final IItemHandlerModifiable wrapped;
    private final BlockEntity psi;
    private final AbstractContraptionEntity contraptionEntity;

    // Lazy-resolved state
    private boolean resolved = false;
    private Map<ResourceLocation, Integer> limits;
    private UUID stationId;
    private UUID trainId;

    public ManifestAwareItemHandler(IItemHandlerModifiable wrapped,
                                    BlockEntity psi,
                                    AbstractContraptionEntity contraptionEntity) {
        this.wrapped = wrapped;
        this.psi = psi;
        this.contraptionEntity = contraptionEntity;
    }

    private boolean tryResolve() {
        if (this.resolved) {
            return this.limits != null;
        }

        if (!(this.contraptionEntity instanceof CarriageContraptionEntity carriageEntity)) {
            return false; // Not a train, don't resolve, let vanilla behavior through
        }

        Carriage carriage = carriageEntity.getCarriage();
        if (carriage == null || carriage.train == null) {
            return false; // Keep retrying
        }

        Train train = carriage.train;
        GlobalStation station = train.getCurrentStation();
        if (station == null) {
            return false; // Keep retrying, station not set yet
        }

        Level level = this.psi.getLevel();
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
            CreateStats.LOGGER.error("[ManifestHandler] Error getting manifest", e);
            this.resolved = true;
            return false;
        }

        if (manifest == null || manifest.isEmpty()) {
            CreateStats.LOGGER.info("[ManifestHandler] No manifest for station {}, passthrough mode", station.name);
            this.resolved = true;
            return false;
        }

        // Build limits from UNLOAD + ITEM lines
        Map<ResourceLocation, Integer> lim = new HashMap<>();
        for (FreightManifestLine line : manifest.getLines()) {
            if (line.getOperation() == ManifestOperation.UNLOAD
                && line.getCargoKind() == ManifestCargoKind.ITEM
                && line.getResourceId() != null
                && line.getAmount() > 0) {
                lim.merge(line.getResourceId(), line.getAmount(), Integer::sum);
            }
        }

        if (lim.isEmpty()) {
            CreateStats.LOGGER.info("[ManifestHandler] No UNLOAD+ITEM lines, passthrough mode");
            this.resolved = true;
            return false;
        }

        this.limits = lim;
        this.stationId = sid;
        this.trainId = tid;
        this.resolved = true;

        ManifestUnloadTracker.registerInterface(this.psi.getBlockPos(), sid);
        CreateStats.LOGGER.info("[ManifestHandler] Resolved manifest for train '{}' at '{}': {}",
            train.name.getString(), station.name, lim);
        return true;
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
        return this.wrapped.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        // If not resolved yet or no manifest, pass through to original
        if (!tryResolve()) {
            return this.wrapped.extractItem(slot, amount, simulate);
        }

        ItemStack inSlot = this.wrapped.getStackInSlot(slot);
        if (inSlot.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(inSlot.getItem());
        if (itemId == null) {
            return ItemStack.EMPTY;
        }

        Integer limit = this.limits.get(itemId);
        if (limit == null) {
            // Item not in manifest UNLOAD list, block extraction
            return ItemStack.EMPTY;
        }

        ManifestUnloadTracker.StationVisitState state =
            ManifestUnloadTracker.getOrCreate(this.stationId, this.trainId);
        int alreadyExtracted = state.getExtractedItems(itemId);
        int remaining = limit - alreadyExtracted;

        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        int toExtract = Math.min(amount, remaining);
        ItemStack result = this.wrapped.extractItem(slot, toExtract, simulate);

        if (!simulate && !result.isEmpty()) {
            state.recordItemExtraction(itemId, result.getCount());
        }

        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.wrapped.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return this.wrapped.isItemValid(slot, stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        this.wrapped.setStackInSlot(slot, stack);
    }
}
