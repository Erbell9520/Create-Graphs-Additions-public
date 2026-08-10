package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TrainMapGroupPacket implements CustomPacketPayload
{
    private final BlockPos pos;
    private final List<String> groups;
    
    public static final CustomPacketPayload.Type<TrainMapGroupPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "train_map_group"));
    public static final StreamCodec<FriendlyByteBuf, TrainMapGroupPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), TrainMapGroupPacket::new);
    
    public TrainMapGroupPacket(BlockPos pos, List<String> groups) {
        this.pos = pos;
        this.groups = groups;
    }
    
    public TrainMapGroupPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        final int count = buf.readVarInt();
        this.groups = new ArrayList<String>(count);
        for (int i = 0; i < count; ++i) {
            this.groups.add(buf.readUtf(256));
        }
    }
    
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.groups.size());
        for (String g : this.groups) {
            buf.writeUtf(g, 256);
        }
    }
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(TrainMapGroupPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            else if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            else {
                final BlockEntity be = player.level().getBlockEntity(packet.pos);
                if (be instanceof StatsDisplayPanelBE panel) {
                    panel.setTrainMapGroups(packet.groups);
                }
                return;
            }
        });
    }
}
