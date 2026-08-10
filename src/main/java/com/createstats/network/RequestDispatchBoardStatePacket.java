package com.createstats.network;

import com.createstats.blockentity.DispatchBoardBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RequestDispatchBoardStatePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestDispatchBoardStatePacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "request_dispatch_board_state"));
    
    public static final StreamCodec<FriendlyByteBuf, RequestDispatchBoardStatePacket> STREAM_CODEC = 
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), RequestDispatchBoardStatePacket::new);

    private final BlockPos pos;

    public RequestDispatchBoardStatePacket(BlockPos pos) {
        this.pos = pos;
    }

    public RequestDispatchBoardStatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestDispatchBoardStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof DispatchBoardBE clickedBoard)) {
                return;
            }
            // Resolve to controller for data refresh and response
            DispatchBoardBE controller = clickedBoard.getControllerBE();
            if (!controller.isPlayerInInteractionRange(player)) {
                return;
            }
            controller.refreshSummary();
            PacketDistributor.sendToPlayer(player,
                new DispatchBoardStatePacket(controller.getBlockPos(), controller.getManifest(), controller.getCargoView(), controller.getDeliveryBoard()));
        });
    }
}
