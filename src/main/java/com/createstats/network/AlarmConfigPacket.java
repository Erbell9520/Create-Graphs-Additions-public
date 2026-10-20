package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.StatsAlarmBE;
import net.minecraft.network.FriendlyByteBuf;
import com.createstats.blockentity.AlarmSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class AlarmConfigPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AlarmConfigPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "alarm_config"));

    public static final StreamCodec<FriendlyByteBuf, AlarmConfigPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), AlarmConfigPacket::new);

    private final BlockPos pos;
    private final AlarmSettings settings;

    public AlarmConfigPacket(BlockPos pos, AlarmSettings settings) {
        this.pos = pos;
        this.settings = settings;
    }

    public AlarmConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.settings = AlarmSettings.decode(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        this.settings.encode(buf);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AlarmConfigPacket packet, IPayloadContext ctx) {
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
                if (be instanceof StatsAlarmBE alarm) {
                    alarm.setAlarmSettings(packet.settings);
                    return;
                }
                else {
                    return;
                }
            }
        });
    }
}
