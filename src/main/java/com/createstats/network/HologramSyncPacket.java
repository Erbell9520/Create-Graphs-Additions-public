package com.createstats.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class HologramSyncPacket implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<HologramSyncPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "hologram_sync"));
    public static final StreamCodec<FriendlyByteBuf, HologramSyncPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), HologramSyncPacket::new);

    private final BlockPos pos;
    private final CompoundTag hologramSettingsTag;
    private final CompoundTag channelDataTag;

    public HologramSyncPacket(BlockPos pos, CompoundTag hologramSettingsTag, CompoundTag channelDataTag) {
        this.pos = pos;
        this.hologramSettingsTag = hologramSettingsTag;
        this.channelDataTag = channelDataTag;
    }

    public HologramSyncPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.hologramSettingsTag = buf.readNbt();
        this.channelDataTag = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeNbt(this.hologramSettingsTag);
        buf.writeNbt(this.channelDataTag);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HologramSyncPacket packet, IPayloadContext ctx) {
    }
}
