package com.createstats.flight.network;

import com.createstats.CreateStats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FccOverrideCommandPacket(
    int containerId,
    int[] bindingRowIndices,
    int[] overrideRpmCommands,
    boolean[] overrideActive
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FccOverrideCommandPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "fcc_override_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FccOverrideCommandPacket> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.containerId);

            buf.writeVarInt(p.bindingRowIndices.length);

            for (int row : p.bindingRowIndices) {
                buf.writeVarInt(row);
            }
            for (int rpm : p.overrideRpmCommands) {
                buf.writeVarInt(rpm);
            }
            for (boolean active : p.overrideActive) {
                buf.writeBoolean(active);
            }
        }, (buf) -> {
            int containerId = buf.readVarInt();
            int len = buf.readVarInt();
            int[] rows = new int[len];
            int[] rpms = new int[len];
            boolean[] active = new boolean[len];
            for (int i = 0; i < len; i++) {
                rows[i] = buf.readVarInt();
            }
            for (int i = 0; i < len; i++) {
                rpms[i] = buf.readVarInt();
            }
            for (int i = 0; i < len; i++) {
                active[i] = buf.readBoolean();
            }
            return new FccOverrideCommandPacket(containerId, rows, rpms, active);
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FccOverrideCommandPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var serverPlayer = ctx.player();
            if (serverPlayer == null) return;

            var container = serverPlayer.containerMenu;
            if (container instanceof com.createstats.flight.menu.FlightControlComputerMenu menu) {
                if (menu.containerId != packet.containerId()) {
                    return;
                }
                var be = menu.getController();
                be.applyOverrideCommands(packet);
            }
        });
    }
}
