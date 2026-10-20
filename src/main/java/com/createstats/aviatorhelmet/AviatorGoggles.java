package com.createstats.aviatorhelmet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// on the stack so the head-slot component diff sends it to everyone, no packet needed
public record AviatorGoggles(boolean down, long strokeTick, int tone) {
    public static final AviatorGoggles DEFAULT = new AviatorGoggles(false, 0L, 0);

    public static final Codec<AviatorGoggles> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("down", false).forGetter(AviatorGoggles::down),
            Codec.LONG.optionalFieldOf("stroke", 0L).forGetter(AviatorGoggles::strokeTick),
            Codec.INT.optionalFieldOf("tone", 0).forGetter(AviatorGoggles::tone)
    ).apply(i, AviatorGoggles::new));

    public static final StreamCodec<ByteBuf, AviatorGoggles> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AviatorGoggles::down,
            ByteBufCodecs.VAR_LONG, AviatorGoggles::strokeTick,
            ByteBufCodecs.VAR_INT, AviatorGoggles::tone,
            AviatorGoggles::new);

    public AviatorGoggles flicked(long nowTick) {
        return new AviatorGoggles(!down, nowTick, tone);
    }

    public AviatorGoggles nextTone() {
        return new AviatorGoggles(down, strokeTick, HudTone.next(tone).ordinal());
    }

    public AviatorGoggles settled() {
        return new AviatorGoggles(down, 0L, tone);
    }
}
