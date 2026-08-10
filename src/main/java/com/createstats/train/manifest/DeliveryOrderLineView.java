package com.createstats.train.manifest;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public class DeliveryOrderLineView {

    private final UUID lineId;
    private final ManifestCargoKind cargoKind;
    private final ManifestOperation operation;
    private final DeliveryOrderResetMode resetMode;
    private final int resetIntervalValue;
    private final ItemStack displayStack;
    private final String displayName;
    private final int targetAmount;
    private final int deliveredAmount;
    private final int freightAmount;
    private final boolean dismissed;
    private final long ticksUntilReset;
    private final String statusText;

    public DeliveryOrderLineView(UUID lineId, ManifestCargoKind cargoKind, ManifestOperation operation,
                                 DeliveryOrderResetMode resetMode, int resetIntervalValue, ItemStack displayStack,
                                 String displayName, int targetAmount, int deliveredAmount, int freightAmount,
                                 boolean dismissed, long ticksUntilReset, String statusText) {
        this.lineId = lineId;
        this.cargoKind = cargoKind;
        this.operation = operation;
        this.resetMode = resetMode;
        this.resetIntervalValue = Math.max(1, resetIntervalValue);
        this.displayStack = displayStack.copy();
        this.displayName = displayName;
        this.targetAmount = targetAmount;
        this.deliveredAmount = Math.max(0, deliveredAmount);
        this.freightAmount = Math.max(0, freightAmount);
        this.dismissed = dismissed;
        this.ticksUntilReset = ticksUntilReset;
        this.statusText = statusText;
    }

    public UUID getLineId() {
        return this.lineId;
    }

    public ManifestCargoKind getCargoKind() {
        return this.cargoKind;
    }

    public ManifestOperation getOperation() {
        return this.operation;
    }

    public DeliveryOrderResetMode getResetMode() {
        return this.resetMode;
    }

    public int getResetIntervalValue() {
        return this.resetIntervalValue;
    }

    public ItemStack getDisplayStack() {
        return this.displayStack.copy();
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getTargetAmount() {
        return this.targetAmount;
    }

    public int getDeliveredAmount() {
        return this.deliveredAmount;
    }

    public int getFreightAmount() {
        return this.freightAmount;
    }

    public boolean isDismissed() {
        return this.dismissed;
    }

    public long getTicksUntilReset() {
        return this.ticksUntilReset;
    }

    public String getStatusText() {
        return this.statusText;
    }

    public boolean isComplete() {
        return this.targetAmount > 0 && this.deliveredAmount >= this.targetAmount;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("LineId", this.lineId);
        tag.putString("CargoKind", this.cargoKind.getSerializedName());
        tag.putString("Operation", this.operation.getSerializedName());
        tag.putString("ResetMode", this.resetMode.getSerializedName());
        tag.putInt("ResetIntervalValue", this.resetIntervalValue);
        tag.put("DisplayStack", this.displayStack.saveOptional(registries));
        tag.putString("DisplayName", this.displayName);
        tag.putInt("TargetAmount", this.targetAmount);
        tag.putInt("DeliveredAmount", this.deliveredAmount);
        tag.putInt("FreightAmount", this.freightAmount);
        tag.putBoolean("Dismissed", this.dismissed);
        tag.putLong("TicksUntilReset", this.ticksUntilReset);
        tag.putString("StatusText", this.statusText);
        return tag;
    }

    public static DeliveryOrderLineView load(CompoundTag tag, HolderLookup.Provider registries) {
        return new DeliveryOrderLineView(
            tag.getUUID("LineId"),
            ManifestCargoKind.byName(tag.getString("CargoKind")),
            ManifestOperation.byName(tag.getString("Operation")),
            DeliveryOrderResetMode.byName(tag.getString("ResetMode")),
            Math.max(1, tag.getInt("ResetIntervalValue")),
            ItemStack.parseOptional(registries, tag.getCompound("DisplayStack")),
            tag.getString("DisplayName"),
            tag.getInt("TargetAmount"),
            tag.getInt("DeliveredAmount"),
            tag.getInt("FreightAmount"),
            tag.getBoolean("Dismissed"),
            tag.getLong("TicksUntilReset"),
            tag.getString("StatusText"));
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.lineId);
        buf.writeEnum(this.cargoKind);
        buf.writeEnum(this.operation);
        buf.writeEnum(this.resetMode);
        buf.writeVarInt(this.resetIntervalValue);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, this.displayStack);
        buf.writeUtf(this.displayName);
        buf.writeVarInt(this.targetAmount);
        buf.writeVarInt(this.deliveredAmount);
        buf.writeVarInt(this.freightAmount);
        buf.writeBoolean(this.dismissed);
        buf.writeLong(this.ticksUntilReset);
        buf.writeUtf(this.statusText);
    }

    public static DeliveryOrderLineView decode(RegistryFriendlyByteBuf buf) {
        return new DeliveryOrderLineView(
            buf.readUUID(),
            buf.readEnum(ManifestCargoKind.class),
            buf.readEnum(ManifestOperation.class),
            buf.readEnum(DeliveryOrderResetMode.class),
            buf.readVarInt(),
            ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
            buf.readUtf(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readBoolean(),
            buf.readLong(),
            buf.readUtf());
    }
}
