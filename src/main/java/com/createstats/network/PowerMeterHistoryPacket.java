package com.createstats.network;

import com.createstats.gui.meter.MeterHistory;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PowerMeterHistoryPacket(BlockPos meterPos, int range, boolean full, int[] samples)
        implements CustomPacketPayload {
    public static PowerMeterHistoryPacket full(BlockPos pos, int range, int[] samples) {
        return new PowerMeterHistoryPacket(pos, range, true, samples);
    }

    public static PowerMeterHistoryPacket delta(BlockPos pos, int range, int sample) {
        return new PowerMeterHistoryPacket(pos, range, false, new int[]{sample});
    }

    public static final CustomPacketPayload.Type<PowerMeterHistoryPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "power_meter_history"));

    public static final StreamCodec<FriendlyByteBuf, PowerMeterHistoryPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.meterPos());
                buf.writeVarInt(pkt.range());
                buf.writeBoolean(pkt.full());
                buf.writeVarInt(pkt.samples().length);
                for (final int sample : pkt.samples()) {
                    buf.writeInt(sample);
                }
            },
            buf -> {
                final BlockPos pos = buf.readBlockPos();
                final int range = buf.readVarInt();
                final boolean full = buf.readBoolean();
                final int n = buf.readVarInt();
                if (n < 0 || n > MeterHistory.SIZE) {
                    throw new DecoderException("Power Meter history sample count out of range: " + n);
                }
                final int[] samples = new int[n];
                for (int i = 0; i < n; i++) {
                    samples[i] = buf.readInt();
                }
                return new PowerMeterHistoryPacket(pos, range, full, samples);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PowerMeterHistoryPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> StatsClientHooks.handlePowerMeterHistory(
                packet.meterPos(), packet.range(), packet.full(), packet.samples()));
    }
}
