package com.createstats.network;

import com.createstats.blockentity.CalculatorBE;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class CalculatorStatePacket implements CustomPacketPayload
{
    private final BlockPos pos;
    private final CompoundTag stateTag;
    
    public static final CustomPacketPayload.Type<CalculatorStatePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "calculator_state"));
    public static final StreamCodec<FriendlyByteBuf, CalculatorStatePacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), CalculatorStatePacket::new);
    
    public CalculatorStatePacket(BlockPos pos, CompoundTag stateTag) {
        this.pos = pos;
        this.stateTag = stateTag;
    }
    
    public CalculatorStatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.stateTag = buf.readNbt();
    }
    
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeNbt(this.stateTag);
    }
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(CalculatorStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (be instanceof CalculatorBE calculator && packet.stateTag != null) {
                calculator.getControllerBE().applyClientState(packet.stateTag);
            }
        });
    }
}
