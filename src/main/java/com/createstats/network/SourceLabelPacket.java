package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SourceLabelPacket implements CustomPacketPayload
{
    private final BlockPos panelPos;
    private final String sourceId;
    private final String newLabel;
    
    public static final CustomPacketPayload.Type<SourceLabelPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "source_label"));
    public static final StreamCodec<FriendlyByteBuf, SourceLabelPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), SourceLabelPacket::new);
    
    public SourceLabelPacket(BlockPos panelPos, String sourceId, String newLabel) {
        this.panelPos = panelPos;
        this.sourceId = sourceId;
        this.newLabel = newLabel;
    }
    
    public SourceLabelPacket(FriendlyByteBuf buf) {
        this.panelPos = buf.readBlockPos();
        this.sourceId = buf.readUtf(256);
        this.newLabel = buf.readUtf(256);
    }
    
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.panelPos);
        buf.writeUtf(this.sourceId, 256);
        buf.writeUtf(this.newLabel, 256);
    }
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(SourceLabelPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            else {
                final BlockEntity be = player.level().getBlockEntity(packet.panelPos);
                if (be instanceof StatsDisplayPanelBE panel) {
                    final SourceConfig config = panel.getSourceRegistry().get(packet.sourceId);
                    if (config != null) {
                        final String label = packet.newLabel.trim();
                        config.setUserLabel(label.isEmpty() ? null : label);
                        panel.setChanged();
                        panel.getLevel().sendBlockUpdated(packet.panelPos, panel.getBlockState(), panel.getBlockState(), 3);
                    }
                }
                return;
            }
        });
    }
}
