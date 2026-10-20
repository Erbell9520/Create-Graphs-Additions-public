package com.createstats.battle.net;

import com.createstats.CreateStats;
import com.createstats.battle.world.BattleCrew;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BattleKeyPacket(int action, int entityId, String shipId, boolean spyglass) implements CustomPacketPayload {
    public static final int FIRE = 0;
    public static final int TOGGLE_WEAPONS = 1;

    public static final int DESIGNATE = 2;

    public static final Type<BattleKeyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "battle_key"));

    public static final StreamCodec<FriendlyByteBuf, BattleKeyPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.action());
                buf.writeVarInt(p.entityId());
                buf.writeUtf(p.shipId() == null ? "" : p.shipId(), 40);
                buf.writeBoolean(p.spyglass());
            },
            buf -> new BattleKeyPacket(buf.readVarInt(), buf.readVarInt(), buf.readUtf(40), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BattleKeyPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) BattleCrew.handleKey(player, packet);
        });
    }
}
