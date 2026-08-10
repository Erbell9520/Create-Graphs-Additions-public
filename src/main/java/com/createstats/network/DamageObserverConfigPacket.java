package com.createstats.network;

import com.createstats.blockentity.DamageObserverBE;
import com.createstats.blockentity.DamageObserverSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class DamageObserverConfigPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DamageObserverConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "damage_observer_config"));

    public static final StreamCodec<FriendlyByteBuf, DamageObserverConfigPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), DamageObserverConfigPacket::new);

    private final BlockPos pos;
    private final DamageObserverSettings settings;

    public DamageObserverConfigPacket(BlockPos pos, DamageObserverSettings settings) {
        this.pos = pos;
        this.settings = settings.copy();
    }

    public DamageObserverConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.settings = DamageObserverSettings.decode(buf);
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        this.settings.encode(buf);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DamageObserverConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (be instanceof DamageObserverBE observer) {
                observer.setSettings(packet.settings);
            }
        });
    }
}