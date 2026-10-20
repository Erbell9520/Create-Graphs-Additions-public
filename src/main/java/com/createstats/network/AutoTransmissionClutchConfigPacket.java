package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.AutoTransmissionClutchBE;
import com.createstats.blockentity.AutoTransmissionClutchSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class AutoTransmissionClutchConfigPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AutoTransmissionClutchConfigPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "auto_transmission_clutch_config"));

    public static final StreamCodec<FriendlyByteBuf, AutoTransmissionClutchConfigPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), AutoTransmissionClutchConfigPacket::new);

    public enum Action { UPDATE_SETTINGS }

    private final BlockPos pos;
    private final Action action;
    private final AutoTransmissionClutchSettings settings;

    public AutoTransmissionClutchConfigPacket(BlockPos pos, AutoTransmissionClutchSettings settings) {
        this.pos = pos;
        this.action = Action.UPDATE_SETTINGS;
        this.settings = settings;
    }

    public AutoTransmissionClutchConfigPacket(BlockPos pos, Action action) {
        this.pos = pos;
        this.action = action;
        this.settings = null;
    }

    public AutoTransmissionClutchConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.action = buf.readEnum(Action.class);
        if (this.action == Action.UPDATE_SETTINGS) {
            this.settings = AutoTransmissionClutchSettings.decode(buf);
        } else {
            this.settings = null;
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeEnum(this.action);
        if (this.action == Action.UPDATE_SETTINGS && this.settings != null) {
            this.settings.encode(buf);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AutoTransmissionClutchConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) return;

            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof AutoTransmissionClutchBE transmission)) return;

            switch (packet.action) {
                case UPDATE_SETTINGS:
                    if (packet.settings != null) transmission.setSettings(packet.settings);
                    break;
            }
        });
    }
}
