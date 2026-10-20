package com.createstats.network;

import java.util.ArrayList;
import java.util.List;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.pa.Utterance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PaSpeakPacket(BlockPos pos, List<String> clips, int severity, boolean stopFirst,
                            float volume, float pitch, int range)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PaSpeakPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "pa_speak"));

    public static final StreamCodec<FriendlyByteBuf, PaSpeakPacket> STREAM_CODEC =
            StreamCodec.of(PaSpeakPacket::encode, PaSpeakPacket::decode);

    public static PaSpeakPacket of(BlockPos pos, Utterance utterance, boolean stopFirst,
                                   float volume, float pitch, int range) {
        return new PaSpeakPacket(pos, utterance.clips(), utterance.severity().ordinal(), stopFirst,
                volume, pitch, range);
    }

    public static PaSpeakPacket silence(BlockPos pos) {
        return new PaSpeakPacket(pos, List.of(), 0, true, 1.0f, 1.0f, 16);
    }

    private static void encode(FriendlyByteBuf buf, PaSpeakPacket pkt) {
        buf.writeBlockPos(pkt.pos);
        buf.writeVarInt(pkt.clips.size());
        for (String clip : pkt.clips) {
            buf.writeUtf(clip, 48);
        }
        buf.writeVarInt(pkt.severity);
        buf.writeBoolean(pkt.stopFirst);
        buf.writeFloat(pkt.volume);
        buf.writeFloat(pkt.pitch);
        buf.writeVarInt(pkt.range);
    }

    private static PaSpeakPacket decode(FriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final int size = buf.readVarInt();

        final List<String> clips = new ArrayList<>(Math.min(size, 64));
        for (int i = 0; i < size; i++) {
            final String clip = buf.readUtf(48);
            if (i < 64) {
                clips.add(clip);
            }
        }
        final int severity = buf.readVarInt();
        final boolean stopFirst = buf.readBoolean();

        final float volume = Math.max(0.0f, Math.min(1.0f, buf.readFloat()));
        final float pitch = Math.max(0.5f, Math.min(2.0f, buf.readFloat()));
        final int range = Math.max(1, Math.min(256, buf.readVarInt()));
        return new PaSpeakPacket(pos, clips, severity, stopFirst, volume, pitch, range);
    }

    public AlarmSeverity severityValue() {
        final AlarmSeverity[] values = AlarmSeverity.values();
        return values[Math.floorMod(this.severity, values.length)];
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PaSpeakPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> com.createstats.client.PaVoicePlayer.accept(pkt));
    }
}
