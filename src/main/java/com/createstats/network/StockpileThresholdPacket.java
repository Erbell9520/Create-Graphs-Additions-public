package com.createstats.network;

import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** [1] */
public class StockpileThresholdPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StockpileThresholdPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "stockpile_threshold"));

    public static final StreamCodec<FriendlyByteBuf, StockpileThresholdPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), StockpileThresholdPacket::new);

    private final BlockPos pos;
    private final int offBelow;
    private final int onAbove;

    public StockpileThresholdPacket(BlockPos pos, int offBelow, int onAbove) {
        this.pos = pos;
        this.offBelow = offBelow;
        this.onAbove = onAbove;
    }

    public StockpileThresholdPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.offBelow = buf.readVarInt();
        this.onAbove = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.offBelow);
        buf.writeVarInt(this.onAbove);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StockpileThresholdPacket packet, IPayloadContext ctx) {
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
            if (be instanceof ThresholdSwitchBlockEntity tsw) {
                tsw.offWhenBelow = Math.max(0, packet.offBelow);
                tsw.onWhenAbove = Math.max(0, packet.onAbove);
                // Recompute the redstone state against the new thresholds and persist.
                tsw.updateCurrentLevel();
                tsw.setChanged();
            }
        });
    }
}
