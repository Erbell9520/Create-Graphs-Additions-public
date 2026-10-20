package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.SafetyClutchBE;
import com.createstats.blockentity.SafetyClutchSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SafetyClutchConfigPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SafetyClutchConfigPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "safety_clutch_config"));

    public static final StreamCodec<FriendlyByteBuf, SafetyClutchConfigPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), SafetyClutchConfigPacket::new);

    public enum Action { UPDATE_SETTINGS, MANUAL_RESET, MANUAL_TRIP }

    private final BlockPos pos;
    private final Action action;
    private final SafetyClutchSettings settings;

    public SafetyClutchConfigPacket(BlockPos pos, SafetyClutchSettings settings) {
        this.pos = pos;
        this.action = Action.UPDATE_SETTINGS;
        this.settings = settings;
    }

    public SafetyClutchConfigPacket(BlockPos pos, Action action) {
        this.pos = pos;
        this.action = action;
        this.settings = null;
    }

    public SafetyClutchConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.action = buf.readEnum(Action.class);
        if (this.action == Action.UPDATE_SETTINGS) {
            this.settings = SafetyClutchSettings.decode(buf);
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

    public static void handle(SafetyClutchConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) return;

            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof SafetyClutchBE clutch)) return;

            switch (packet.action) {
                case UPDATE_SETTINGS:
                    if (packet.settings != null) clutch.setSettings(packet.settings);
                    break;
                case MANUAL_RESET:
                    clutch.manualReset();
                    break;
                case MANUAL_TRIP:
                    clutch.manualTrip();
                    break;
            }
        });
    }
}
