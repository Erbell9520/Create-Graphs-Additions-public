package com.createstats.vcu.network;

import com.createstats.CreateStats;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vcu.VehicleControlUnitBE;
import com.createstats.vcu.core.PilotInput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record VcuInputPacket(BlockPos pos, int bits) implements CustomPacketPayload {
    public static final Type<VcuInputPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vcu_input"));

    public static final StreamCodec<FriendlyByteBuf, VcuInputPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeVarInt(packet.bits() & PilotInput.ALL);
            },
            buf -> new VcuInputPacket(buf.readBlockPos(), buf.readVarInt() & PilotInput.ALL));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VcuInputPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(player.level(), packet.pos());
            if (be instanceof VehicleControlUnitBE vcu) vcu.acceptInput(player, packet.bits());
        });
    }
}
