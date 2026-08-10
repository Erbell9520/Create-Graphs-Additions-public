package com.createstats.train.manifest;

import java.util.HashMap;
import java.util.Map;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class TrainCargoSnapshot {

    private final Map<FreightManifestResourceKey, Integer> amounts;

    public TrainCargoSnapshot() {
        this.amounts = new HashMap<>();
    }

    public Map<FreightManifestResourceKey, Integer> getAmounts() {
        return this.amounts;
    }

    public int getAmount(FreightManifestResourceKey key) {
        return this.amounts.getOrDefault(key, 0);
    }

    public void addAmount(FreightManifestResourceKey key, int amount) {
        if (amount == 0) {
            return;
        }
        int updated = this.amounts.getOrDefault(key, 0) + amount;
        if (updated <= 0) {
            this.amounts.remove(key);
        } else {
            this.amounts.put(key, updated);
        }
    }

    public boolean isEmpty() {
        return this.amounts.isEmpty();
    }

    public TrainCargoSnapshot copy() {
        TrainCargoSnapshot copy = new TrainCargoSnapshot();
        copy.amounts.putAll(this.amounts);
        return copy;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        for (Map.Entry<FreightManifestResourceKey, Integer> entry : this.amounts.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Kind", entry.getKey().getCargoKind().getSerializedName());
            entryTag.putString("Id", entry.getKey().getResourceId().toString());
            entryTag.putInt("Amount", entry.getValue());
            entries.add(entryTag);
        }
        tag.put("Entries", entries);
        return tag;
    }

    public static TrainCargoSnapshot load(CompoundTag tag) {
        if (tag == null) {
            return new TrainCargoSnapshot();
        }
        TrainCargoSnapshot snapshot = new TrainCargoSnapshot();
        NBTHelper.iterateCompoundList(tag.getList("Entries", Tag.TAG_COMPOUND), entryTag -> {
            ManifestCargoKind kind = ManifestCargoKind.byName(entryTag.getString("Kind"));
            ResourceLocation resourceId = ResourceLocation.tryParse(entryTag.getString("Id"));
            if (resourceId == null) {
                return;
            }
            snapshot.amounts.put(new FreightManifestResourceKey(kind, resourceId), entryTag.getInt("Amount"));
        });
        return snapshot;
    }

    public static TrainCargoSnapshot capture(Train train) {
        TrainCargoSnapshot snapshot = new TrainCargoSnapshot();
        for (Carriage carriage : train.carriages) {
            IItemHandlerModifiable items = carriage.storage.getMountedItems();
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (itemId == null) {
                    continue;
                }
                snapshot.amounts.merge(new FreightManifestResourceKey(ManifestCargoKind.ITEM, itemId), stack.getCount(), Integer::sum);
            }

            IFluidHandler fluids = carriage.storage.getFluids();
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                FluidStack fluid = fluids.getFluidInTank(tank);
                if (fluid.isEmpty()) {
                    continue;
                }
                ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
                if (fluidId == null) {
                    continue;
                }
                snapshot.amounts.merge(new FreightManifestResourceKey(ManifestCargoKind.FLUID, fluidId), fluid.getAmount(), Integer::sum);
            }
        }
        return snapshot;
    }
}
