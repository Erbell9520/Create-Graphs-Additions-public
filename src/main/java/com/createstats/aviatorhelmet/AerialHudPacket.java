package com.createstats.aviatorhelmet;

import com.createstats.CreateStats;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AerialHudPacket(String mode, String sub, float heading, float heldHeading, float track, float windFrom,
                              float windSpeed, float targetBearing, String targetLabel, float altitude,
                              float heldAltitude, float ceiling, float terrain, float climb, float ground, float order,
                              float engine, float speed, float full, float lift, float pitch, float roll, String banner,
                              boolean alert)
        implements CustomPacketPayload {
    public static final int MAX_TEXT = 512;

    public static final Type<AerialHudPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "aviator_helmet_hud"));

    public static final StreamCodec<FriendlyByteBuf, AerialHudPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(cut(p.mode()), MAX_TEXT);
                buf.writeUtf(cut(p.sub()), MAX_TEXT);
                buf.writeFloat(p.heading());
                buf.writeFloat(p.heldHeading());
                buf.writeFloat(p.track());
                buf.writeFloat(p.windFrom());
                buf.writeFloat(p.windSpeed());
                buf.writeFloat(p.targetBearing());
                buf.writeUtf(cut(p.targetLabel()), MAX_TEXT);
                buf.writeFloat(p.altitude());
                buf.writeFloat(p.heldAltitude());
                buf.writeFloat(p.ceiling());
                buf.writeFloat(p.terrain());
                buf.writeFloat(p.climb());
                buf.writeFloat(p.ground());
                buf.writeFloat(p.order());
                buf.writeFloat(p.engine());
                buf.writeFloat(p.speed());
                buf.writeFloat(p.full());
                buf.writeFloat(p.lift());
                buf.writeFloat(p.pitch());
                buf.writeFloat(p.roll());
                buf.writeUtf(cut(p.banner()), MAX_TEXT);
                buf.writeBoolean(p.alert());
            },
            buf -> new AerialHudPacket(buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf(MAX_TEXT),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readUtf(MAX_TEXT), buf.readBoolean()));

    public static final long STALE_NANOS = 1_000_000_000L;

    @Nullable
    private static volatile AerialHudPacket latest;
    private static volatile long latestNanos;

    public AerialHudPacket {
        mode = mode == null ? "" : mode;
        sub = sub == null ? "" : sub;
        targetLabel = targetLabel == null ? "" : targetLabel;
        banner = banner == null ? "" : banner;
    }

    private static String cut(String s) {
        return s == null ? "" : s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AerialHudPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            latest = packet;
            latestNanos = System.nanoTime();
        });
    }

    @Nullable
    public static AerialHudPacket current() {
        final AerialHudPacket p = latest;
        return p != null && System.nanoTime() - latestNanos < STALE_NANOS ? p : null;
    }
}
