package com.createstats.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** [1] */
public class KineticFlywheelSettings {

    private boolean dischargeArmed;
    private float lowReservePct;

    public KineticFlywheelSettings() {
        this.dischargeArmed = true;
        this.lowReservePct = 15.0f;
    }

    public boolean isDischargeArmed() {
        return this.dischargeArmed;
    }

    public void setDischargeArmed(boolean armed) {
        this.dischargeArmed = armed;
    }

    public float getLowReservePct() {
        return this.lowReservePct;
    }

    public void setLowReservePct(float pct) {
        this.lowReservePct = Math.max(0.0f, Math.min(90.0f, pct));
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("DischargeArmed", this.dischargeArmed);
        tag.putFloat("LowReservePct", this.lowReservePct);
        return tag;
    }

    public static KineticFlywheelSettings load(CompoundTag tag) {
        final KineticFlywheelSettings s = new KineticFlywheelSettings();
        if (tag.contains("DischargeArmed")) s.dischargeArmed = tag.getBoolean("DischargeArmed");
        if (tag.contains("LowReservePct")) s.setLowReservePct(tag.getFloat("LowReservePct"));
        return s;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.save());
    }

    public static KineticFlywheelSettings decode(FriendlyByteBuf buf) {
        final CompoundTag tag = buf.readNbt();
        return (tag != null) ? load(tag) : new KineticFlywheelSettings();
    }
}
