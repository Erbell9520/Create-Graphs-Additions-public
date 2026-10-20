package com.createstats.driverhelmet;

import com.createstats.CreateStats;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HelmetGlowPacket(int throttle, int state) implements CustomPacketPayload {
    public static final int SPIN = 1;
    public static final int LOCK = 2;
    public static final int WARNING_MASK = 3;
    public static final int WELCOME = 4;

    private static final long MIN_GAP_NANOS = 150_000_000L;
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    public static final Type<HelmetGlowPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "driver_helmet_glow"));

    public static final StreamCodec<ByteBuf, HelmetGlowPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HelmetGlowPacket::throttle,
            ByteBufCodecs.VAR_INT, HelmetGlowPacket::state,
            HelmetGlowPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HelmetGlowPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }
            final ItemStack helmet = DriverHelmets.worn(sender);
            if (helmet.isEmpty()) {
                return;
            }
            final HelmetVisor visor = DriverHelmetItem.visor(helmet);
            if (visor.open() || !HudTheme.of(visor.theme()).glows()) {
                return;
            }
            final long now = System.nanoTime();
            final Long last = LAST.get(sender.getUUID());
            if (last != null && now - last < MIN_GAP_NANOS) {
                return;
            }
            LAST.put(sender.getUUID(), now);
            final int throttle = Math.max(-100, Math.min(100, packet.throttle()));
            final int state = packet.state() & (WARNING_MASK | WELCOME);
            PacketDistributor.sendToPlayersTrackingEntity(sender, new HelmetGlowSync(sender.getId(), throttle, state));
        });
    }
}
