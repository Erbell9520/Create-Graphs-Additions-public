package com.createstats.network;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.createstats.display.HologramSettings;
import com.createstats.holomk3.HoloMk3Settings;
import com.createstats.holomk3.HoloProjectorMk3BE;

public record HoloMk3ConfigPacket(BlockPos pos, CompoundTag mk3, CompoundTag hologram,
                                  boolean redstoneEnabled, String redstoneChannelId,
                                  float redstoneThreshold, boolean redstoneAbove, int redstoneMode,
                                  boolean scanEnabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HoloMk3ConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "holo_mk3_config"));

    public static final StreamCodec<FriendlyByteBuf, HoloMk3ConfigPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), HoloMk3ConfigPacket::decode);

    private void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeNbt(this.mk3);
        buf.writeNbt(this.hologram);
        buf.writeBoolean(this.redstoneEnabled);
        buf.writeUtf(this.redstoneChannelId);
        buf.writeFloat(this.redstoneThreshold);
        buf.writeBoolean(this.redstoneAbove);
        buf.writeVarInt(this.redstoneMode);
        buf.writeBoolean(this.scanEnabled);
    }

    private static HoloMk3ConfigPacket decode(FriendlyByteBuf buf) {
        return new HoloMk3ConfigPacket(buf.readBlockPos(), buf.readNbt(), buf.readNbt(),
                buf.readBoolean(), buf.readUtf(), buf.readFloat(), buf.readBoolean(),
                buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HoloMk3ConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5,
                    packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof HoloProjectorMk3BE projector)) {
                return;
            }
            final UUID owner = projector.getOwnerUUID();
            if (owner != null && !owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
                return;
            }
            if (packet.mk3 != null) {
                projector.setMk3Settings(HoloMk3Settings.load(packet.mk3));
            }
            if (packet.hologram != null) {
                projector.setHologramSettings(HologramSettings.load(packet.hologram));
            }
            projector.setRedstoneEnabled(packet.redstoneEnabled);
            projector.setRedstoneChannelId(packet.redstoneChannelId);
            projector.setRedstoneThreshold(packet.redstoneThreshold);
            projector.setRedstoneAbove(packet.redstoneAbove);
            projector.setRedstoneMode(packet.redstoneMode);
            projector.setScanEnabled(packet.scanEnabled);
            projector.setChanged();
            player.level().sendBlockUpdated(packet.pos, projector.getBlockState(),
                    projector.getBlockState(), 3);
        });
    }
}
