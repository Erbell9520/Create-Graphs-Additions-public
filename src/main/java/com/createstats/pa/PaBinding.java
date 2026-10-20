package com.createstats.pa;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

// CHANNEL is the normal way, inherits label history and threshold
public record PaBinding(Kind kind, BlockPos pos, String metricId) {
    public enum Kind {
        METRIC,

        DEVICE,

        CHANNEL,
    }

    public static PaBinding metric(BlockPos pos, String metricId) {
        return new PaBinding(Kind.METRIC, pos.immutable(), metricId);
    }

    public static PaBinding device(BlockPos pos) {
        return new PaBinding(Kind.DEVICE, pos.immutable(), "");
    }

    public static PaBinding channel(BlockPos panel, String channelId) {
        return new PaBinding(Kind.CHANNEL, panel.immutable(), channelId);
    }

    public String key() {
        return this.kind.name().charAt(0) + "@" + this.pos.asLong()
                + (this.metricId.isEmpty() ? "" : ":" + this.metricId);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putByte("K", (byte) this.kind.ordinal());
        tag.putLong("P", this.pos.asLong());
        if (!this.metricId.isEmpty()) {
            tag.putString("M", this.metricId);
        }
        return tag;
    }

    public static PaBinding load(CompoundTag tag) {
        final Kind[] kinds = Kind.values();
        final Kind kind = kinds[Math.floorMod(tag.getByte("K"), kinds.length)];
        return new PaBinding(kind, BlockPos.of(tag.getLong("P")), tag.getString("M"));
    }
}
