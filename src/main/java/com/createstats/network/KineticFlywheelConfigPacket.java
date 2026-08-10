package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.KineticFlywheelBE;
import com.createstats.blockentity.KineticFlywheelSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class KineticFlywheelConfigPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<KineticFlywheelConfigPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "kinetic_flywheel_config"));

    public static final StreamCodec<FriendlyByteBuf, KineticFlywheelConfigPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), KineticFlywheelConfigPacket::new);

    public enum Action { UPDATE_SETTINGS, FORCE_PARK, RELEASE }

    private final BlockPos pos;
    private final Action action;
    private final KineticFlywheelSettings settings;

    public KineticFlywheelConfigPacket(BlockPos pos, KineticFlywheelSettings settings) {
        this.pos = pos;
        this.action = Action.UPDATE_SETTINGS;
        this.settings = settings;
    }

    public KineticFlywheelConfigPacket(BlockPos pos, Action action) {
        this.pos = pos;
        this.action = action;
        this.settings = null;
    }

    public KineticFlywheelConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.action = buf.readEnum(Action.class);
        if (this.action == Action.UPDATE_SETTINGS) {
            this.settings = KineticFlywheelSettings.decode(buf);
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

    public static void handle(KineticFlywheelConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) return;

            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof KineticFlywheelBE flywheel)) return;

            switch (packet.action) {
                case UPDATE_SETTINGS:
                    if (packet.settings != null) flywheel.setSettings(packet.settings);
                    break;
                case FORCE_PARK:
                    flywheel.forcePark();
                    break;
                case RELEASE:
                    flywheel.release();
                    break;
            }
        });
    }
}
