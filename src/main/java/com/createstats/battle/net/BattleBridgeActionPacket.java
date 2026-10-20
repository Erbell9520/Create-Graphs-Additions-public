package com.createstats.battle.net;

import com.createstats.CreateStats;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BattleBridgeActionPacket(BlockPos bridge, int action, int a, int b, String s) implements CustomPacketPayload {
    public static final int WATCH = 0;

    public static final int REQUEST_SHAPE = 1;

    public static final int REQUEST_DETAIL = 2;
    public static final int ACCEPT_SHAPE = 3;

    public static final int ACCEPT_PLACE = 4;

    public static final int ACCEPT_REFIT = 5;
    public static final int TURN_BOW = 6;

    public static final int SET_GROUND_SIZE = 7;

    public static final int SET_OPTION = 8;

    public static final int SHOW_PLACE = 9;

    public static final int DESIGNATE = 10;
    public static final int CLEAR_TARGET = 11;

    public static final int SET_MODE = 12;

    public static final int SET_WEAPONS = 13;
    public static final int FIRE_READY = 14;

    public static final int RENAME_GUN = 15;

    public static final int RELEARN_AIM = 16;

    public static final int SET_ARC = 17;

    public static final int MAX_STRING = 48;

    public static final Type<BattleBridgeActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "battle_action"));

    public static final StreamCodec<FriendlyByteBuf, BattleBridgeActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.bridge());
                buf.writeVarInt(p.action());
                buf.writeVarInt(p.a());
                buf.writeVarInt(p.b());
                buf.writeUtf(p.s() == null ? "" : p.s(), MAX_STRING);
            },
            buf -> new BattleBridgeActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_STRING)));

    public static BattleBridgeActionPacket of(BlockPos bridge, int action, int a, int b) {
        return new BattleBridgeActionPacket(bridge, action, a, b, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BattleBridgeActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(player.level(), packet.bridge());
            if (be instanceof BattleScreenDisplayBE bridge) bridge.handleAction(player, packet);
        });
    }
}
