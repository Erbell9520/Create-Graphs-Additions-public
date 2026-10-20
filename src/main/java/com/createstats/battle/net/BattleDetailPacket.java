package com.createstats.battle.net;

import com.createstats.CreateStats;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record BattleDetailPacket(String key, int lostVersion, int[] lost, int[] ageTicks, byte[] causes, int[] burning,
                                 List<PlaceLine> places) implements CustomPacketPayload {
    public static final int MAX_LOST = 4096;

    public record PlaceLine(int id, String name, int kind, int size, int anchor) {
    }

    public static final Type<BattleDetailPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "battle_detail"));

    public static final StreamCodec<FriendlyByteBuf, BattleDetailPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.key(), 128);
                buf.writeVarInt(p.lostVersion());
                buf.writeVarIntArray(p.lost());
                buf.writeVarIntArray(p.ageTicks());
                buf.writeByteArray(p.causes());
                buf.writeVarIntArray(p.burning());
                buf.writeVarInt(p.places().size());
                for (PlaceLine l : p.places()) {
                    buf.writeVarInt(l.id());
                    buf.writeUtf(l.name(), 96);
                    buf.writeByte(l.kind());
                    buf.writeVarInt(l.size());
                    buf.writeVarInt(l.anchor());
                }
            },
            buf -> {
                String key = buf.readUtf(128);
                int version = buf.readVarInt();
                int[] lost = buf.readVarIntArray(MAX_LOST);
                int[] ages = buf.readVarIntArray(MAX_LOST);
                byte[] causes = buf.readByteArray(MAX_LOST);
                int[] burning = buf.readVarIntArray(MAX_LOST);
                int n = Math.min(64, buf.readVarInt());
                List<PlaceLine> places = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    places.add(new PlaceLine(buf.readVarInt(), buf.readUtf(96), buf.readByte(), buf.readVarInt(), buf.readVarInt()));
                }
                return new BattleDetailPacket(key, version, lost, ages, causes, burning, places);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BattleDetailPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> BattleClientBridge.detail(packet));
    }
}
