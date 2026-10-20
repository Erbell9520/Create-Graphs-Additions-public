package com.createstats.powertrack;

import com.createstats.power.FeGoggle;
import com.createstats.power.FeRates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public record SectionView(int state, int feeders, int cars, int flow) {
    public static final int DEAD = 0;

    public static final int IDLE = 1;

    public static final int LIVE = 2;

    public static final int COLLECTING = 3;

    public static final SectionView NONE = new SectionView(DEAD, 0, 0, 0);

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putByte("S", (byte) state);
        tag.putShort("F", (short) Math.min(Short.MAX_VALUE, feeders));
        tag.putShort("C", (short) Math.min(Short.MAX_VALUE, cars));
        tag.putInt("W", flow);
        return tag;
    }

    public static SectionView read(CompoundTag tag) {
        return new SectionView(Math.floorMod(tag.getByte("S"), 4), Math.max(0, tag.getShort("F")),
                Math.max(0, tag.getShort("C")), Math.max(0, tag.getInt("W")));
    }

    public Component stateLine(String prefix) {
        return switch (state) {
            case LIVE -> FeGoggle.ok(prefix + (flow > 0 ? "live, carrying " + FeRates.perTick(flow) : "live"));
            case COLLECTING -> FeGoggle.warn(prefix + (flow > 0
                    ? "collecting " + FeRates.perTick(flow) + " from trains" : "collecting from trains"));
            case IDLE -> FeGoggle.dim(prefix + "fed, but no feeder is working");
            default -> FeGoggle.dim(prefix + "dead: no Track Feeder on it");
        };
    }

    public Component countsLine() {
        return FeGoggle.dim("      " + feeders + (feeders == 1 ? " feeder, " : " feeders, ")
                + cars + (cars == 1 ? " car on it" : " cars on it"));
    }
}
