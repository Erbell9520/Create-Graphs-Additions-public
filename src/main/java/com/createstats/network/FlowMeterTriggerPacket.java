package com.createstats.network;

import com.createstats.blockentity.FlowMeterBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** [1] */
public class FlowMeterTriggerPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FlowMeterTriggerPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "flow_meter_trigger"));

    public static final StreamCodec<FriendlyByteBuf, FlowMeterTriggerPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), FlowMeterTriggerPacket::new);

    private final BlockPos pos;
    private final boolean enabled;
    private final int mode;
    private final int threshold;
    private final boolean above;

    public FlowMeterTriggerPacket(BlockPos pos, boolean enabled, int mode, int threshold, boolean above) {
        this.pos = pos;
        this.enabled = enabled;
        this.mode = mode;
        this.threshold = threshold;
        this.above = above;
    }

    public FlowMeterTriggerPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.enabled = buf.readBoolean();
        this.mode = buf.readVarInt();
        this.threshold = buf.readVarInt();
        this.above = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.enabled);
        buf.writeVarInt(this.mode);
        buf.writeVarInt(this.threshold);
        buf.writeBoolean(this.above);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlowMeterTriggerPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 4096.0) {
                return;
            }
            if (!player.level().isLoaded(packet.pos)) {
                return;
            }
            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (be instanceof FlowMeterBE meter) {
                meter.applyTrigger(packet.enabled, packet.mode, packet.threshold, packet.above);
            }
        });
    }
}
