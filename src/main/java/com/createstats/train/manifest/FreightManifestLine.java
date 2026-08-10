package com.createstats.train.manifest;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class FreightManifestLine {

    private UUID lineId;
    private ManifestCargoKind cargoKind;
    private ManifestOperation operation;
    private DeliveryOrderResetMode resetMode;
    private int resetIntervalValue;
    @Nullable
    private ResourceLocation resourceId;
    private ItemStack displayStack;
    private int amount;

    public FreightManifestLine() {
        this(UUID.randomUUID(), ManifestCargoKind.ITEM, ManifestOperation.LOAD, DeliveryOrderResetMode.EVERY_ARRIVAL, 1,
            null, ItemStack.EMPTY, ManifestCargoKind.ITEM.getDefaultAmount());
    }

    public FreightManifestLine(UUID lineId, ManifestCargoKind cargoKind, ManifestOperation operation,
                               DeliveryOrderResetMode resetMode, int resetIntervalValue, @Nullable ResourceLocation resourceId,
                               ItemStack displayStack, int amount) {
        this.lineId = lineId;
        this.cargoKind = cargoKind;
        this.operation = operation;
        this.resetMode = resetMode;
        this.resetIntervalValue = Math.max(1, resetIntervalValue);
        this.resourceId = resourceId;
        this.displayStack = displayStack.copy();
        this.amount = amount;
    }

    public UUID getLineId() {
        return this.lineId;
    }

    public ManifestCargoKind getCargoKind() {
        return this.cargoKind;
    }

    public void setCargoKind(ManifestCargoKind cargoKind) {
        this.cargoKind = cargoKind;
        if (this.amount <= 0) {
            this.amount = cargoKind.getDefaultAmount();
        }
    }

    public ManifestOperation getOperation() {
        return this.operation;
    }

    public void setOperation(ManifestOperation operation) {
        this.operation = operation;
    }

    public DeliveryOrderResetMode getResetMode() {
        return this.resetMode;
    }

    public void setResetMode(DeliveryOrderResetMode resetMode) {
        this.resetMode = resetMode;
        if (!resetMode.isIntervalBased()) {
            this.resetIntervalValue = 1;
        }
    }

    public int getResetIntervalValue() {
        return this.resetIntervalValue;
    }

    public void setResetIntervalValue(int resetIntervalValue) {
        this.resetIntervalValue = Math.max(1, resetIntervalValue);
    }

    @Nullable
    public ResourceLocation getResourceId() {
        return this.resourceId;
    }

    public ItemStack getDisplayStack() {
        return this.displayStack;
    }

    public void setSelection(ManifestCargoKind cargoKind, ResourceLocation resourceId, ItemStack displayStack) {
        ManifestCargoKind previousKind = this.cargoKind;
        this.cargoKind = cargoKind;
        this.resourceId = resourceId;
        this.displayStack = displayStack.copy();
        if (this.amount <= 0 || this.amount == previousKind.getDefaultAmount()) {
            this.amount = cargoKind.getDefaultAmount();
        }
    }

    public void clearSelection() {
        this.resourceId = null;
        this.displayStack = ItemStack.EMPTY;
    }

    public boolean hasSelection() {
        return this.resourceId != null && !this.displayStack.isEmpty();
    }

    @Nullable
    public FreightManifestResourceKey getResourceKey() {
        if (this.resourceId == null) {
            return null;
        }
        return new FreightManifestResourceKey(this.cargoKind, this.resourceId);
    }

    public int getAmount() {
        return this.amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getDisplayName() {
        if (!this.displayStack.isEmpty()) {
            return this.displayStack.getHoverName().getString();
        }
        if (this.resourceId != null) {
            return this.resourceId.toString();
        }
        return "Unset";
    }

    public FreightManifestLine copy() {
        return new FreightManifestLine(this.lineId, this.cargoKind, this.operation, this.resetMode, this.resetIntervalValue,
            this.resourceId, this.displayStack, this.amount);
    }

    public FreightManifestLine duplicate() {
        return new FreightManifestLine(UUID.randomUUID(), this.cargoKind, this.operation, this.resetMode, this.resetIntervalValue,
            this.resourceId, this.displayStack, this.amount);
    }

    public boolean isCycleCompatibleWith(FreightManifestLine other) {
        return this.cargoKind == other.cargoKind
            && this.operation == other.operation
            && this.resetMode == other.resetMode
            && this.resetIntervalValue == other.resetIntervalValue
            && Objects.equals(this.resourceId, other.resourceId);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("LineId", this.lineId);
        tag.putString("CargoKind", this.cargoKind.getSerializedName());
        tag.putString("Operation", this.operation.getSerializedName());
        tag.putString("ResetMode", this.resetMode.getSerializedName());
        tag.putInt("ResetIntervalValue", this.resetIntervalValue);
        if (this.resourceId != null) {
            tag.putString("ResourceId", this.resourceId.toString());
        }
        tag.put("DisplayStack", this.displayStack.saveOptional(registries));
        tag.putInt("Amount", this.amount);
        return tag;
    }

    public static FreightManifestLine load(CompoundTag tag, HolderLookup.Provider registries) {
        UUID lineId = tag.hasUUID("LineId") ? tag.getUUID("LineId") : UUID.randomUUID();
        ManifestCargoKind cargoKind = ManifestCargoKind.byName(tag.getString("CargoKind"));
        ManifestOperation operation = ManifestOperation.byName(tag.getString("Operation"));
        DeliveryOrderResetMode resetMode = tag.contains("ResetMode")
            ? DeliveryOrderResetMode.byName(tag.getString("ResetMode"))
            : DeliveryOrderResetMode.EVERY_ARRIVAL;
        int resetIntervalValue = tag.contains("ResetIntervalValue") ? Math.max(1, tag.getInt("ResetIntervalValue")) : 1;
        ResourceLocation resourceId = null;
        if (tag.contains("ResourceId")) {
            resourceId = ResourceLocation.tryParse(tag.getString("ResourceId"));
        }
        ItemStack displayStack = ItemStack.parseOptional(registries, tag.getCompound("DisplayStack"));
        int amount = tag.contains("Amount") ? tag.getInt("Amount") : cargoKind.getDefaultAmount();
        return new FreightManifestLine(lineId, cargoKind, operation, resetMode, resetIntervalValue, resourceId, displayStack, amount);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.lineId);
        buf.writeEnum(this.cargoKind);
        buf.writeEnum(this.operation);
        buf.writeEnum(this.resetMode);
        buf.writeVarInt(this.resetIntervalValue);
        buf.writeBoolean(this.resourceId != null);
        if (this.resourceId != null) {
            buf.writeResourceLocation(this.resourceId);
        }
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, this.displayStack);
        buf.writeVarInt(this.amount);
    }

    public static FreightManifestLine decode(RegistryFriendlyByteBuf buf) {
        UUID lineId = buf.readUUID();
        ManifestCargoKind cargoKind = buf.readEnum(ManifestCargoKind.class);
        ManifestOperation operation = buf.readEnum(ManifestOperation.class);
        DeliveryOrderResetMode resetMode = buf.readEnum(DeliveryOrderResetMode.class);
        int resetIntervalValue = Math.max(1, buf.readVarInt());
        ResourceLocation resourceId = buf.readBoolean() ? buf.readResourceLocation() : null;
        ItemStack displayStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        int amount = buf.readVarInt();
        return new FreightManifestLine(lineId, cargoKind, operation, resetMode, resetIntervalValue, resourceId, displayStack, amount);
    }
}
