package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.display.HologramSettings;
import com.createstats.blockentity.HolographicProjectorBE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class HologramConfigPacket implements CustomPacketPayload
{
    private final BlockPos pos;
    private final CompoundTag newSettings;
    private final boolean redstoneEnabled;
    private final String redstoneChannelId;
    private final float redstoneThreshold;
    private final boolean redstoneAbove;
    private final int redstoneMode;
    private final boolean scanEnabled;
    
    public static final CustomPacketPayload.Type<HologramConfigPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "hologram_config"));
    public static final StreamCodec<FriendlyByteBuf, HologramConfigPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), HologramConfigPacket::new);

    public HologramConfigPacket(BlockPos pos, CompoundTag newSettings) {
        this(pos, newSettings, false, "", 0.0f, true, 0, false);
    }

    public HologramConfigPacket(BlockPos pos, CompoundTag newSettings, boolean redstoneEnabled, String redstoneChannelId, float redstoneThreshold, boolean redstoneAbove, int redstoneMode, boolean scanEnabled) {
        this.pos = pos;
        this.newSettings = newSettings;
        this.redstoneEnabled = redstoneEnabled;
        this.redstoneChannelId = (redstoneChannelId != null) ? redstoneChannelId : "";
        this.redstoneThreshold = redstoneThreshold;
        this.redstoneAbove = redstoneAbove;
        this.redstoneMode = redstoneMode;
        this.scanEnabled = scanEnabled;
    }
    
    public HologramConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.newSettings = buf.readNbt();
        this.redstoneEnabled = buf.readBoolean();
        this.redstoneChannelId = buf.readUtf();
        this.redstoneThreshold = buf.readFloat();
        this.redstoneAbove = buf.readBoolean();
        this.redstoneMode = buf.readVarInt();
        this.scanEnabled = buf.readBoolean();
    }
    
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeNbt(this.newSettings);
        buf.writeBoolean(this.redstoneEnabled);
        buf.writeUtf(this.redstoneChannelId);
        buf.writeFloat(this.redstoneThreshold);
        buf.writeBoolean(this.redstoneAbove);
        buf.writeVarInt(this.redstoneMode);
        buf.writeBoolean(this.scanEnabled);
    }
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(HologramConfigPacket packet, IPayloadContext ctx) {
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
                if (be instanceof HolographicProjectorBE projector) {
                    final java.util.UUID owner = projector.getOwnerUUID();
                    if (owner != null && !owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
                        return;
                    }
                    if (packet.newSettings != null) {
                        projector.setHologramSettings(HologramSettings.load(packet.newSettings));
                    }
                    projector.setRedstoneEnabled(packet.redstoneEnabled);
                    projector.setRedstoneChannelId(packet.redstoneChannelId);
                    projector.setRedstoneThreshold(packet.redstoneThreshold);
                    projector.setRedstoneAbove(packet.redstoneAbove);
                    projector.setRedstoneMode(packet.redstoneMode);
                    projector.setScanEnabled(packet.scanEnabled);
                    projector.setChanged();
                    player.level().sendBlockUpdated(packet.pos, projector.getBlockState(), projector.getBlockState(), 3);
                    return;
                }
                else {
                    return;
                }
            }
        });
    }
}
