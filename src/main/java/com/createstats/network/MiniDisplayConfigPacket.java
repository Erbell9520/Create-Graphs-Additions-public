package com.createstats.network;

import com.createstats.blockentity.MiniDisplayBE;
import com.createstats.blockentity.MiniDisplaySettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MiniDisplayConfigPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MiniDisplayConfigPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "mini_display_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MiniDisplayConfigPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), MiniDisplayConfigPacket::new);

    private final BlockPos pos;
    private final MiniDisplaySettings settings;

    public MiniDisplayConfigPacket(BlockPos pos, MiniDisplaySettings settings) {
        this.pos = pos;
        this.settings = settings;
    }

    public MiniDisplayConfigPacket(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.settings = MiniDisplaySettings.decode(buf);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        this.settings.encode(buf);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MiniDisplayConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (be instanceof MiniDisplayBE miniDisplay) {
                miniDisplay.getControllerBE().setSettings(packet.settings);
            }
        });
    }
}
