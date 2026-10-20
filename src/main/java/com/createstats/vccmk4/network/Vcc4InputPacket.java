package com.createstats.vccmk4.network;

import com.createstats.CreateStats;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.HelmConsole;
import com.createstats.vccmk4.core.HeldKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record Vcc4InputPacket(BlockPos pos, int[] held) implements CustomPacketPayload {
    public static final Type<Vcc4InputPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vcc4_input"));

    public static final StreamCodec<FriendlyByteBuf, Vcc4InputPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeVarIntArray(HeldKeys.of(packet.held()).toArray());
            },
            buf -> new Vcc4InputPacket(buf.readBlockPos(), buf.readVarIntArray(HeldKeys.MAX)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Vcc4InputPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(player.level(), packet.pos());
            if (be instanceof HelmConsole console) console.acceptInput(player, HeldKeys.of(packet.held()));
        });
    }
}
