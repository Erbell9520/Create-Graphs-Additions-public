package com.createstats.network;

import com.createstats.gui.battery.BatteryHistory;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// never on the update tag, that goes out several times a second
public record BatteryControllerHistoryPacket(BlockPos controllerPos, int range, boolean full,
                                             byte[] soc, int[] in, int[] out)
        implements CustomPacketPayload {
    public static BatteryControllerHistoryPacket full(BlockPos pos, int range, byte[] soc,
                                                      int[] in, int[] out) {
        return new BatteryControllerHistoryPacket(pos, range, true, soc, in, out);
    }

    public static BatteryControllerHistoryPacket delta(BlockPos pos, int range, int soc,
                                                       int in, int out) {
        return new BatteryControllerHistoryPacket(pos, range, false,
                new byte[]{(byte) soc}, new int[]{in}, new int[]{out});
    }

    public static final CustomPacketPayload.Type<BatteryControllerHistoryPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    "create_stats", "battery_controller_history"));

    public static final StreamCodec<FriendlyByteBuf, BatteryControllerHistoryPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.controllerPos());
                        buf.writeVarInt(pkt.range());
                        buf.writeBoolean(pkt.full());
                        buf.writeVarInt(pkt.soc().length);
                        buf.writeBytes(pkt.soc());
                        for (final int sample : pkt.in()) {
                            buf.writeInt(sample);
                        }
                        for (final int sample : pkt.out()) {
                            buf.writeInt(sample);
                        }
                    },
                    buf -> {
                        final BlockPos pos = buf.readBlockPos();
                        final int range = buf.readVarInt();
                        final boolean full = buf.readBoolean();
                        final int n = buf.readVarInt();
                        if (n < 0 || n > BatteryHistory.SIZE) {
                            throw new DecoderException(
                                    "Battery Controller history sample count out of range: " + n);
                        }
                        final byte[] soc = new byte[n];
                        buf.readBytes(soc);
                        final int[] in = new int[n];
                        for (int i = 0; i < n; i++) {
                            in[i] = buf.readInt();
                        }
                        final int[] out = new int[n];
                        for (int i = 0; i < n; i++) {
                            out[i] = buf.readInt();
                        }
                        return new BatteryControllerHistoryPacket(pos, range, full, soc, in, out);
                    });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BatteryControllerHistoryPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> StatsClientHooks.handleBatteryControllerHistory(
                packet.controllerPos(), packet.range(), packet.full(),
                packet.soc(), packet.in(), packet.out()));
    }
}
