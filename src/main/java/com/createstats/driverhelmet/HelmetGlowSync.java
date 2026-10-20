package com.createstats.driverhelmet;

import com.createstats.CreateStats;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HelmetGlowSync(int entity, int throttle, int state) implements CustomPacketPayload {
    private static final long STALE_NANOS = 1_500_000_000L;

    public static final Type<HelmetGlowSync> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "driver_helmet_glow_sync"));

    public static final StreamCodec<ByteBuf, HelmetGlowSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HelmetGlowSync::entity,
            ByteBufCodecs.VAR_INT, HelmetGlowSync::throttle,
            ByteBufCodecs.VAR_INT, HelmetGlowSync::state,
            HelmetGlowSync::new);

    private record Heard(HelmetGlowSync glow, long nanos) {
    }

    private static final Map<Integer, Heard> BY_ENTITY = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HelmetGlowSync packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (BY_ENTITY.size() > 256) {
                BY_ENTITY.clear();
            }
            BY_ENTITY.put(packet.entity(), new Heard(packet, System.nanoTime()));
        });
    }

    @Nullable
    public static HelmetGlowSync of(int entity) {
        final Heard h = BY_ENTITY.get(entity);
        return h != null && System.nanoTime() - h.nanos() < STALE_NANOS ? h.glow() : null;
    }

    public LedBar.Frame frame(double seconds) {
        if ((state & HelmetGlowPacket.WELCOME) != 0) {
            return LedBar.sweep(seconds);
        }
        final int w = state & HelmetGlowPacket.WARNING_MASK;
        final LedBar.Warning warning = w == HelmetGlowPacket.SPIN ? LedBar.Warning.SPIN
                : w == HelmetGlowPacket.LOCK ? LedBar.Warning.LOCK : LedBar.Warning.NONE;
        return LedBar.live(throttle / 100.0, warning, seconds);
    }
}
