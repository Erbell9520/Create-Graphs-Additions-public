package com.createstats.network;

import com.createstats.blockentity.TableAlarmClockBE;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TableAlarmClockStatePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TableAlarmClockStatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "table_alarm_clock_state"));
    public static final StreamCodec<FriendlyByteBuf, TableAlarmClockStatePacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), TableAlarmClockStatePacket::new);
    private final BlockPos pos;
    private final CompoundTag stateTag;

    public TableAlarmClockStatePacket(BlockPos pos, CompoundTag stateTag) {
        this.pos = pos;
        this.stateTag = stateTag;
    }

    public TableAlarmClockStatePacket(FriendlyByteBuf buf) {
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

    public static void handle(TableAlarmClockStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null || packet.stateTag == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (be instanceof TableAlarmClockBE clock) {
                clock.applyClientState(packet.stateTag);
            }
        });
    }
}
