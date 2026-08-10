package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PanelActivityPacket implements CustomPacketPayload
{
    private final BlockPos pos;
    private final boolean trainMapActive;
    private final boolean topologyActive;
    
    public static final CustomPacketPayload.Type<PanelActivityPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "panel_activity"));
    public static final StreamCodec<FriendlyByteBuf, PanelActivityPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), PanelActivityPacket::new);
    
    public PanelActivityPacket(BlockPos pos, boolean trainMapActive, boolean topologyActive) {
        this.pos = pos;
        this.trainMapActive = trainMapActive;
        this.topologyActive = topologyActive;
    }
    
    public PanelActivityPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.trainMapActive = buf.readBoolean();
        this.topologyActive = buf.readBoolean();
    }
    
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.trainMapActive);
        buf.writeBoolean(this.topologyActive);
    }
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(PanelActivityPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (be instanceof StatsDisplayPanelBE panel) {
                final StatsDisplayPanelBE controller = panel.getControllerBE();
                if (controller.isPlayerInInteractionRange(player)) {
                    if (packet.trainMapActive) {
                        controller.markTrainMapActive();
                    }
                    if (packet.topologyActive) {
                        controller.markTopologyActive();
                    }
                }
            }
        });
    }
}
