package com.createstats.battle.net;

import com.createstats.CreateStats;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BattleShapePacket(String key, int version, boolean ground, int bow, long origin, int[] frame,
                                byte[] plan, byte[] profile, int[] props, int[] bridges) implements CustomPacketPayload {
    public static final Type<BattleShapePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "battle_shape"));

    public static final StreamCodec<FriendlyByteBuf, BattleShapePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.key(), 128);
                buf.writeVarInt(p.version());
                buf.writeBoolean(p.ground());
                buf.writeByte(p.bow());
                buf.writeLong(p.origin());
                buf.writeVarIntArray(p.frame());
                buf.writeByteArray(p.plan());
                buf.writeByteArray(p.profile());
                buf.writeVarIntArray(p.props());
                buf.writeVarIntArray(p.bridges());
            },
            buf -> new BattleShapePacket(buf.readUtf(128), buf.readVarInt(), buf.readBoolean(), buf.readByte(), buf.readLong(),
                    buf.readVarIntArray(16), buf.readByteArray(16384), buf.readByteArray(16384), buf.readVarIntArray(256),
                    buf.readVarIntArray(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BattleShapePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> BattleClientBridge.shape(packet));
    }
}
