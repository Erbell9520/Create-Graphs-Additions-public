package com.createstats.driverhelmet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record HelmetVisor(boolean open, long strokeTick, boolean nightVision, int theme, double odometer) {
    public static final HelmetVisor DEFAULT = new HelmetVisor(false, 0L, false, 0, 0.0);

    public static final Codec<HelmetVisor> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("open", false).forGetter(HelmetVisor::open),
            Codec.LONG.optionalFieldOf("stroke", 0L).forGetter(HelmetVisor::strokeTick),
            Codec.BOOL.optionalFieldOf("night_vision", false).forGetter(HelmetVisor::nightVision),
            Codec.INT.optionalFieldOf("theme", 0).forGetter(HelmetVisor::theme),
            Codec.DOUBLE.optionalFieldOf("odometer", 0.0).forGetter(HelmetVisor::odometer)
    ).apply(i, HelmetVisor::new));

    public static final StreamCodec<ByteBuf, HelmetVisor> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, HelmetVisor::open,
            ByteBufCodecs.VAR_LONG, HelmetVisor::strokeTick,
            ByteBufCodecs.BOOL, HelmetVisor::nightVision,
            ByteBufCodecs.VAR_INT, HelmetVisor::theme,
            ByteBufCodecs.DOUBLE, HelmetVisor::odometer,
            HelmetVisor::new);

    public HelmetVisor flicked(long nowTick) {
        return new HelmetVisor(!open, nowTick, nightVision, theme, odometer);
    }

    public HelmetVisor withNightVision(boolean on) {
        return new HelmetVisor(open, strokeTick, on, theme, odometer);
    }

    public HelmetVisor nextTheme() {
        return new HelmetVisor(open, strokeTick, nightVision, HudTheme.next(theme).ordinal(), odometer);
    }

    public HelmetVisor withOdometer(double metres) {
        return new HelmetVisor(open, strokeTick, nightVision, theme, metres);
    }
}
