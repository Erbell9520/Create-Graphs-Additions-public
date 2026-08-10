package com.createstats.network;

import com.createstats.blockentity.StatusTowerLightBE;
import com.createstats.blockentity.StatusTowerLightSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class StatusTowerLightConfigPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StatusTowerLightConfigPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "status_tower_light_config"));
    
    public static final StreamCodec<FriendlyByteBuf, StatusTowerLightConfigPacket> STREAM_CODEC = 
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), StatusTowerLightConfigPacket::new);

    private final BlockPos pos;
    private final StatusTowerLightSettings settings;

    public StatusTowerLightConfigPacket(BlockPos pos, StatusTowerLightSettings settings) {
        this.pos = pos;
        this.settings = settings;
    }

    public StatusTowerLightConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.settings = StatusTowerLightSettings.decode(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        this.settings.encode(buf);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StatusTowerLightConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (be instanceof StatusTowerLightBE towerLight) {
                towerLight.setSettings(packet.settings);
            }
        });
    }
}
